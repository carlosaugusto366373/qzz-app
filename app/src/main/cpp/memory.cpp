#include "memory.h"
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <dirent.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/uio.h>
#include <android/log.h>

#define LOG_TAG "QZZ-MEM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

int Memory::pid_ = -1;

bool Memory::Init(const std::string& processName) {
    pid_ = -1;
    DIR* d = opendir("/proc");
    if (!d) return false;

    struct dirent* e;
    while ((e = readdir(d))) {
        if (e->d_type != DT_DIR) continue;
        int pid = atoi(e->d_name);
        if (pid <= 0) continue;

        char path[64];
        snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);

        int fd = open(path, O_RDONLY);
        if (fd < 0) continue;

        char buf[256] = {0};
        read(fd, buf, sizeof(buf) - 1);
        close(fd);

        if (strstr(buf, processName.c_str())) {
            pid_ = pid;
            closedir(d);
            LOGI("encontrou PID %d para %s", pid, processName.c_str());
            return true;
        }
    }

    closedir(d);
    LOGE("processo %s não encontrado", processName.c_str());
    return false;
}

int Memory::GetPid() { return pid_; }

bool Memory::ParseMaps() { return pid_ > 0; }

uintptr_t Memory::GetBase(const std::string& moduleName) {
    if (pid_ <= 0) return 0;

    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/maps", pid_);

    FILE* f = fopen(path, "r");
    if (!f) return 0;

    char line[512];
    uintptr_t base = 0;

    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, moduleName.c_str())) {
            uintptr_t start = 0;
            sscanf(line, "%lx-", &start);
            base = start;
            break;
        }
    }

    fclose(f);
    return base;
}

bool Memory::Read(uintptr_t addr, void* buf, size_t size) {
    if (pid_ <= 0 || addr == 0) return false;

    struct iovec local  { buf, size };
    struct iovec remote { (void*)addr, size };

    ssize_t n = process_vm_readv(pid_, &local, 1, &remote, 1, 0);
    return n == (ssize_t)size;
}

bool Memory::IsValid(uintptr_t addr) {
    if (addr == 0 || addr < 0x10000) return false;
    if (addr > 0x00007FFFFFFFFFFF) return false;
    return true;
}

// SigScan: procura pattern de bytes com wildcards "??"
uintptr_t Memory::SigScan(uintptr_t start, size_t size, const char* pattern) {
    if (pid_ <= 0 || start == 0 || size == 0) return 0;

    // parse do pattern
    uint8_t bytes[64];
    bool    mask[64];
    int     len = 0;

    const char* p = pattern;
    while (*p && len < 64) {
        while (*p == ' ') p++;
        if (!*p) break;

        if (*p == '?') {
            bytes[len] = 0;
            mask[len]  = false;
            len++;
            while (*p == '?') p++;
        } else {
            char hex[3] = { p[0], p[1], 0 };
            bytes[len] = (uint8_t)strtoul(hex, nullptr, 16);
            mask[len]  = true;
            len++;
            p += 2;
        }
    }

    if (len == 0) return 0;

    // lê em chunks
    const size_t CHUNK = 0x100000;
    uint8_t* buf = (uint8_t*)malloc(CHUNK);
    if (!buf) return 0;

    uintptr_t found = 0;

    for (uintptr_t off = 0; off < size; off += CHUNK - len) {
        size_t toRead = (size - off) < CHUNK ? (size - off) : CHUNK;
        if (!Read(start + off, buf, toRead)) continue;

        for (size_t i = 0; i + len <= toRead; i++) {
            bool ok = true;
            for (int j = 0; j < len; j++) {
                if (mask[j] && buf[i + j] != bytes[j]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                found = start + off + i;
                break;
            }
        }
        if (found) break;
    }

    free(buf);
    return found;
}