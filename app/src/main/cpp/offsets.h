#pragma once
#include <cstdint>

namespace Offsets {
    constexpr uintptr_t GWorld                 = 0x0;
    constexpr uintptr_t GameInstance           = 0x1A0;
    constexpr uintptr_t LocalPlayer            = 0x88;
    constexpr uintptr_t PlayerController       = 0x30;
    constexpr uintptr_t PlayerCameraManager    = 0x4A0;
    constexpr uintptr_t Camera                 = 0x3F0;

    constexpr uintptr_t EntityList             = 0x1C8;
    constexpr uintptr_t EntityCount            = 0x1D0;
    constexpr uintptr_t EntitySize             = 0x10;

    constexpr uintptr_t ActorPosition          = 0x1E0;
    constexpr uintptr_t ActorHealth            = 0x560;
    constexpr uintptr_t ActorMaxHealth         = 0x564;
    constexpr uintptr_t ActorTeam              = 0x5B8;
    constexpr uintptr_t ActorName              = 0x4C0;

    constexpr uintptr_t LocalTeam              = 0x5B8;

    constexpr uintptr_t CameraViewMatrix       = 0x2E0;
    constexpr uintptr_t CameraLocation         = 0x2A0;
}

namespace Config {
    constexpr float  AIM_SMOOTH    = 0.35f;
    constexpr float  AIM_FOV_PX    = 400.0f;
    constexpr int    AIM_OFFSET_Y  = -60;
    constexpr bool   AIM_ENABLED   = true;
    constexpr bool   ESP_ENABLED   = true;
    constexpr bool   ESP_TEAM_CHECK= true;
    constexpr int    ESP_MAX_DIST  = 400;
}