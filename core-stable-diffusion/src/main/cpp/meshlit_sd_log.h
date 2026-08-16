// Phase 4.x — android logcat helpers for the libmeshlit_sd stub.
// Thin wrappers so the .cpp file doesn't have to drag <android/log.h>
// into every translation unit. Phase 2 keeps these and adds
// higher-level logging (per-step progress, scheduler events) once
// stable-diffusion.cpp is wired in.

#pragma once

#include <android/log.h>

#define LOG_TAG "meshlit_sd"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
