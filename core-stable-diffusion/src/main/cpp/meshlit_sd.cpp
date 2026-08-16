// Phase 4.x — libmeshlit_sd.so JNI stub body.
//
// MVP1: every entry point is a typed stub that compiles, links, and
// returns a structured signal the Kotlin side translates into a
// MeshlitResult.Failure with a "sd.native_stub" tag. The dispatch
// path is exercised end-to-end, the UI status chip flips to
// "loaded", and Phase 2 can replace the body of each native function
// without changing the Kotlin surface.
//
// The magic handle 0xC0FFEEL is intentionally non-zero so the Kotlin
// side can verify "loadLibrary succeeded AND loadModel was called"
// without having to allocate real sd_ctx_t memory in the stub build.
//
// Phase 2 (out of scope here): swap the stub bodies for real sd.cpp
// + ggml calls — new_sd_ctx / sd_image_t -> sd_image_gen etc.

#include <jni.h>
#include <cstdint>

#include "meshlit_sd_log.h"

namespace {

// Magic handle the stub returns from nativeLoadModel. Distinct from 0
// (which is the "unloaded" sentinel on the Kotlin side) so the
// engine can short-circuit on isReady without touching the file
// system. Value chosen to be visually obvious in debugger logs.
constexpr int64_t kStubHandleMagic = static_cast<int64_t>(0xC0FFEE0000000000LL);

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* /*vm*/, void* /*reserved*/) {
    LOGI("meshlit_sd stub loaded — install sd.cpp + ggml and rebuild to enable on-device inference");
    return JNI_VERSION_1_6;
}

JNIEXPORT jstring JNICALL
Java_com_meshlit_stable_1diffusion_engines_SdCppEngine_nativeVersion(
    JNIEnv* env, jobject /*this*/) {
    return env->NewStringUTF("meshlit_sd/0.1.0-stub");
}

// Returns 0 on success (stub: always), 1 on failure. Writes the
// magic handle to outHandle[0] so the Kotlin side can verify the
// load path actually executed.
JNIEXPORT jint JNICALL
Java_com_meshlit_stable_1diffusion_engines_SdCppEngine_nativeLoadModel(
    JNIEnv* env, jobject /*this*/,
    jstring unetJ, jstring textEncoderJ, jstring vaeJ,
    jint threads, jint gpuLayers, jlongArray outHandleJ) {
    const char* unet = unetJ ? env->GetStringUTFChars(unetJ, nullptr) : "";
    const char* clip = textEncoderJ ? env->GetStringUTFChars(textEncoderJ, nullptr) : "";
    const char* vae = vaeJ ? env->GetStringUTFChars(vaeJ, nullptr) : "";
    LOGW("nativeLoadModel stub: unet=%s clip=%s vae=%s threads=%d gpuLayers=%d",
         unet, clip, vae, static_cast<int>(threads), static_cast<int>(gpuLayers));
    if (unetJ) env->ReleaseStringUTFChars(unetJ, unet);
    if (textEncoderJ) env->ReleaseStringUTFChars(textEncoderJ, clip);
    if (vaeJ) env->ReleaseStringUTFChars(vaeJ, vae);
    if (outHandleJ == nullptr) return 1;
    jlong handle = static_cast<jlong>(kStubHandleMagic);
    env->SetLongArrayRegion(outHandleJ, 0, 1, &handle);
    return 0;
}

// Returns nullptr — Kotlin wrapper translates that into a typed
// "sd.native_stub" Failure. The arg list matches the Kotlin side 1:1
// (see SdCppEngine.nativeTxt2img) so the JNI mangled name resolves.
// The runner uses a separate progress() flow (SdProgressEvent) for
// step-level updates, so there is no progress callback parameter
// here. Phase 2 adds a real sd_image_gen implementation; the JIT
// caller stays unchanged.
JNIEXPORT jbyteArray JNICALL
Java_com_meshlit_stable_1diffusion_engines_SdCppEngine_nativeTxt2img(
    JNIEnv* env, jobject /*this*/, jlong handle,
    jstring promptJ, jstring negPromptJ,
    jint steps, jfloat cfg, jstring samplerJ,
    jlong seed, jint width, jint height) {
    const char* prompt = promptJ ? env->GetStringUTFChars(promptJ, nullptr) : "";
    const char* neg = negPromptJ ? env->GetStringUTFChars(negPromptJ, nullptr) : "";
    const char* sampler = samplerJ ? env->GetStringUTFChars(samplerJ, nullptr) : "";
    LOGW("nativeTxt2img stub: handle=0x%llx prompt=\"%s\" neg=\"%s\" steps=%d cfg=%.2f sampler=%s seed=%lld w=%d h=%d",
         static_cast<unsigned long long>(handle), prompt, neg,
         static_cast<int>(steps), cfg, sampler,
         static_cast<long long>(seed),
         static_cast<int>(width), static_cast<int>(height));
    if (promptJ) env->ReleaseStringUTFChars(promptJ, prompt);
    if (negPromptJ) env->ReleaseStringUTFChars(negPromptJ, neg);
    if (samplerJ) env->ReleaseStringUTFChars(samplerJ, sampler);
    return nullptr;
}

JNIEXPORT void JNICALL
Java_com_meshlit_stable_1diffusion_engines_SdCppEngine_nativeUnload(
    JNIEnv* /*env*/, jobject /*this*/, jlong handle) {
    LOGI("nativeUnload stub: handle=0x%llx", static_cast<unsigned long long>(handle));
}

JNIEXPORT void JNICALL
Java_com_meshlit_stable_1diffusion_engines_SdCppEngine_nativeInterrupt(
    JNIEnv* /*env*/, jobject /*this*/, jlong handle) {
    LOGI("nativeInterrupt stub: handle=0x%llx", static_cast<unsigned long long>(handle));
}

}  // extern "C"
