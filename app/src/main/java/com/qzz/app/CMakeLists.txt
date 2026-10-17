cmake_minimum_required(VERSION 3.22.1)
project(qzzaim)

# ============================================================
# SHADOWHOOK (Injetor Nativo)
# ============================================================
# ATENÇÃO: O arquivo "libshadowhook.so" DEVE estar na pasta:
# app/src/main/cpp/libs/arm64-v8a/libshadowhook.so
add_library(shadowhook SHARED IMPORTED)
set_target_properties(shadowhook PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libshadowhook.so
    IMPORTED_NO_SONAME ON
)

# ============================================================
# PROJETO PRINCIPAL (QZZ)
# ============================================================
add_library(qzzaim SHARED
    aimbot.cpp
    memory.cpp
    injector.cpp
)

target_compile_options(qzzaim PRIVATE -O2 -fvisibility=hidden -std=c++17)
target_link_libraries(qzzaim shadowhook log android)