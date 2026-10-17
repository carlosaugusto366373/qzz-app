cmake_minimum_required(VERSION 3.22.1)
project(qzzaim)

# ============================================================
# SHADOWHOOK
# ============================================================
add_library(shadowhook SHARED IMPORTED)
set_target_properties(shadowhook PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libshadowhook.so
    IMPORTED_NO_SONAME ON
)

# ============================================================
# PROJETO PRINCIPAL (O seu app)
# ============================================================
add_library(qzzaim SHARED
    aimbot.cpp
    memory.cpp
    injector.cpp
)

target_compile_options(qzzaim PRIVATE -O2 -fvisibility=hidden -std=c++17)
target_link_libraries(qzzaim shadowhook log android)