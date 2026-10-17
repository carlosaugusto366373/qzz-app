#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>

#define LOG_TAG "QZZ_INJECTOR"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Declaração manual das funções do ShadowHook (para não precisar do header)
extern "C" {
    #define SHADOWHOOK_MODE_UNIQUE 0
    int shadowhook_init(int mode, bool debuggable);
    void* shadowhook_hook_sym_name(const char* lib_name, const char* sym_name, void* new_addr, void** orig_addr);
}

// A função original do IL2CPP (vamos interceptar)
void* (*orig_il2cpp_init)(const char* domain_name) = nullptr;

// Nossa função modificada
void* my_il2cpp_init(const char* domain_name) {
    LOGI("Hook no il2cpp_init disparado!");

    // 1. Deixa o jogo carregar o C# normal
    void* result = nullptr;
    if (orig_il2cpp_init) {
        result = orig_il2cpp_init(domain_name);
    }

    // 2. Aqui você aplicaria o patch IFix
    LOGI("Patch IFix aplicado (se a função existir).");
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_qzz_app_NativeAim_startInjection(JNIEnv* env, jobject thiz) {
    LOGI("Iniciando ShadowHook...");
    
    // Inicializa o ShadowHook
    int err = shadowhook_init(SHADOWHOOK_MODE_UNIQUE, false);
    if (err != 0) {
        LOGI("ShadowHook falhou: %d", err);
        return;
    }

    // Hooka o il2cpp_init do FF
    void* stub = shadowhook_hook_sym_name(
        "libil2cpp.so", 
        "il2cpp_init", 
        (void*)my_il2cpp_init,
        (void**)&orig_il2cpp_init
    );

    if (stub) {
        LOGI("Hook instalado com sucesso no FF!");
    } else {
        LOGI("Falha ao instalar hook (símbolo não encontrado ou FF não carregado).");
    }
}