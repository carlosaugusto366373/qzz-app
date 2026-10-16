#include <jni.h>
#include <android/log.h>
#include <cstdio>
#include <cstring>
#include <cmath>
#include <atomic>
#include <thread>
#include <chrono>
#include <mutex>

#include "memory.h"
#include "offsets.h"

#define LOG_TAG "QZZ-AIM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct Vec3 { float x, y, z; };

// estado global
static std::atomic<bool>  g_Running{false};
static std::atomic<bool>  g_AimEnabled{false};
static std::atomic<bool>  g_EspEnabled{false};
static std::thread        g_Worker;
static std::mutex         g_Lock;

// base do módulo (libil2cpp.so)
static uintptr_t g_ModuleBase = 0;
static uintptr_t g_GWorldAddr = 0;

// callback pra mandar toques pro AccessibilityService
// assinatura: void(float x, float y)
typedef void (*TouchCallback)(float, float);
static TouchCallback g_TouchCb = nullptr;

void SetTouchCallback(TouchCallback cb) { g_TouchCb = cb; }

// ============================================================
// ACHAR O GWORLD POR SIGSCAN
// Padrão de exemplo — MUDE A CADA PATCH DO FF
// ============================================================
static const char* GWORLD_PATTERN = "48 8B 05 ?? ?? ?? ?? 48 8B 88 ?? ?? ?? ??";

bool FindGWorld() {
    if (g_ModuleBase == 0) {
        LOGE("base do módulo não encontrada");
        return false;
    }

    // libil2cpp.so tem ~100MB, procura nos primeiros 40MB
    uintptr_t found = Memory::SigScan(g_ModuleBase, 0x4000000, GWORLD_PATTERN);
    if (!found) {
        LOGE("GWorld não encontrado (pattern desatualizado?)");
        return false;
    }

    // a instrução é: mov rax, [rip+offset] → endereço = found + 3 + offset
    int32_t rel = Memory::Read<int32_t>(found + 3);
    g_GWorldAddr = found + 7 + rel;

    LOGI("GWorld addr: 0x%lx", g_GWorldAddr);
    return true;
}

// ============================================================
// LER CADEIA DE PONTEIROS
// ============================================================
uintptr_t Follow(uintptr_t base, uintptr_t offset) {
    if (!Memory::IsValid(base)) return 0;
    return Memory::Read<uintptr_t>(base + offset);
}

// ============================================================
// WORLD TO SCREEN
// ============================================================
bool WorldToScreen(const float matrix[16], const Vec3& world, int w, int h,
                   float& outX, float& outY) {
    float clipW = matrix[3]*world.x + matrix[7]*world.y + matrix[11]*world.z + matrix[15];
    if (clipW < 0.001f) return false;

    float clipX = matrix[0]*world.x + matrix[4]*world.y + matrix[8] *world.z + matrix[12];
    float clipY = matrix[1]*world.x + matrix[5]*world.y + matrix[9] *world.z + matrix[13];

    outX = (w * 0.5f) + (clipX / clipW) * (w * 0.5f);
    outY = (h * 0.5f) - (clipY / clipW) * (h * 0.5f);
    return true;
}

// ============================================================
// LOOP PRINCIPAL DO AIMBOT
// ============================================================
static int g_ScreenW = 1080;
static int g_ScreenH = 2400;

void SetScreenSize(int w, int h) { g_ScreenW = w; g_ScreenH = h; }

void AimWorker() {
    LOGI("aimbot thread iniciada");

    if (!Memory::Init("com.dts.freefireth") &&
        !Memory::Init("com.dts.freefiremax")) {
        LOGE("Free Fire não está rodando");
        return;
    }

    g_ModuleBase = Memory::GetBase("libil2cpp.so");
    if (!g_ModuleBase) {
        LOGE("libil2cpp.so não achada");
        return;
    }
    LOGI("libil2cpp.so base: 0x%lx", g_ModuleBase);

    if (!FindGWorld()) {
        LOGE("GWorld não resolvido — offsets desatualizados");
        return;
    }

    int centerX = g_ScreenW / 2;
    int centerY = g_ScreenH / 2;

    while (g_Running) {
        if (!g_AimEnabled) {
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
            continue;
        }

        // lê GWorld → GameInstance
        uintptr_t gworld = Memory::Read<uintptr_t>(g_GWorldAddr);
        if (!Memory::IsValid(gworld)) {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
            continue;
        }

        uintptr_t gameInst = Follow(gworld, Offsets::GameInstance);
        uintptr_t localPlayer = Follow(gameInst, Offsets::LocalPlayer);
        if (!Memory::IsValid(localPlayer)) {
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
            continue;
        }

        uintptr_t controller = Follow(localPlayer, Offsets::PlayerController);
        uintptr_t camMgr = Follow(controller, Offsets::PlayerCameraManager);
        uintptr_t camera = Follow(camMgr, Offsets::Camera);
        if (!Memory::IsValid(camera)) {
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
            continue;
        }

        // matriz da câmera
        float matrix[16];
        if (!Memory::Read(camera + Offsets::CameraViewMatrix, matrix, sizeof(matrix))) {
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
            continue;
        }

        // time do player local
        int localTeam = Memory::Read<int>(localPlayer + Offsets::LocalTeam);

        // lista de entidades
        uintptr_t entityList = Follow(gameInst, Offsets::EntityList);
        int entityCount = Memory::Read<int>(gameInst + Offsets::EntityCount);
        if (!Memory::IsValid(entityList) || entityCount <= 0 || entityCount > 200) {
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
            continue;
        }

        // procura o melhor alvo
        float bestDist = 1e9f;
        float bestX = -1, bestY = -1;

        for (int i = 0; i < entityCount; i++) {
            uintptr_t ent = Memory::Read<uintptr_t>(entityList + i * Offsets::EntitySize);
            if (!Memory::IsValid(ent) || ent == localPlayer) continue;

            float hp = Memory::Read<float>(ent + Offsets::ActorHealth);
            if (hp <= 0.0f) continue;

            int team = Memory::Read<int>(ent + Offsets::ActorTeam);
            if (Config::ESP_TEAM_CHECK && team == localTeam) continue;

            Vec3 pos = Memory::Read<Vec3>(ent + Offsets::ActorPosition);
            pos.y += 60.0f; // cabeça

            float sx, sy;
            if (!WorldToScreen(matrix, pos, g_ScreenW, g_ScreenH, sx, sy)) continue;

            float dx = sx - centerX;
            float dy = sy - centerY;
            float d = sqrtf(dx*dx + dy*dy);

            if (d > Config::AIM_FOV_PX) continue;
            if (d < bestDist) {
                bestDist = d;
                bestX = sx;
                bestY = sy;
            }
        }

        // aplica
        if (bestX > 0 && g_TouchCb) {
            float targetX = bestX;
            float targetY = bestY + Config::AIM_OFFSET_Y;

            // smoothing
            float smooth = Config::AIM_SMOOTH;
            targetX = centerX + (targetX - centerX) * (1.0f - smooth);
            targetY = centerY + (targetY - centerY) * (1.0f - smooth);

            g_TouchCb(targetX, targetY);
        }

        std::this_thread::sleep_for(std::chrono::milliseconds(8)); // ~120Hz
    }

    LOGI("aimbot thread finalizada");
}

// ============================================================
// JNI — chamadas do Kotlin
// ============================================================
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_qzz_app_NativeAim_init(JNIEnv*, jobject) {
    if (g_Running) return JNI_TRUE;
    g_Running = true;
    g_Worker = std::thread(AimWorker);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_qzz_app_NativeAim_stop(JNIEnv*, jobject) {
    g_Running = false;
    if (g_Worker.joinable()) g_Worker.join();
}

JNIEXPORT void JNICALL
Java_com_qzz_app_NativeAim_setAim(JNIEnv*, jobject, jboolean on) {
    g_AimEnabled = on;
}

JNIEXPORT void JNICALL
Java_com_qzz_app_NativeAim_setEsp(JNIEnv*, jobject, jboolean on) {
    g_EspEnabled = on;
}

JNIEXPORT void JNICALL
Java_com_qzz_app_NativeAim_setScreen(JNIEnv*, jobject, jint w, jint h) {
    SetScreenSize(w, h);
}

JNIEXPORT void JNICALL
Java_com_qzz_app_NativeAim_setTouchCallback(JNIEnv* env, jobject, jobject cb) {
    // ignora — usamos callback estático definido no MainActivity
    (void)env; (void)cb;
}

} // extern "C"