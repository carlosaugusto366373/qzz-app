cmake_minimum_required(VERSION 3.22.1)
project(qzzaim)

add_library(qzzaim SHARED
    aimbot.cpp
    memory.cpp
)

target_compile_options(qzzaim PRIVATE -O2 -fvisibility=hidden -std=c++17)
target_link_libraries(qzzaim log android)