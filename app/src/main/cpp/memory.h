#pragma once
#include <cstdint>
#include <cstddef>
#include <string>

class Memory {
public:
    static bool      Init(const std::string& processName);
    static uintptr_t GetBase(const std::string& moduleName);
    static bool      Read(uintptr_t addr, void* buf, size_t size);

    template<typename T>
    static T Read(uintptr_t addr) {
        T v{};
        Read(addr, &v, sizeof(T));
        return v;
    }

    static int       GetPid();
    static bool      IsValid(uintptr_t addr);
    static uintptr_t SigScan(uintptr_t start, size_t size, const char* pattern);

private:
    static int       pid_;
    static bool      ParseMaps();
};