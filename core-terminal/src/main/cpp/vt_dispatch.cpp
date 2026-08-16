// vt_dispatch.cpp — JNI entry points.
//
// The Kotlin `Parser.feed` calls into `nativeParse` which:
//   1. Allocates a `DirectByteBuffer` for the action buffer
//      (caller-freed).
//   2. Calls into the C++ parser.
//   3. Returns the buffer + the action count via out-params.
//
// The Kotlin side then walks the buffer in a tight loop and feeds
// each action into the existing Dispatch handlers. This keeps the
// native side small (state machine only) and lets the JVM stay the
// source of truth for cell mutation (which is what Compose reads).

#include "vt_parser.h"

#include <jni.h>
#include <cstring>

extern "C" {

JNIEXPORT jint JNICALL
Java_com_meshlit_terminal_nativ_NativeParser_nativeParse(
        JNIEnv* env,
        jclass /* clazz */,
        jobject input_buffer,
        jint input_length,
        jobject out_action_buffer) {
    auto* in = static_cast<uint8_t*>(env->GetDirectBufferAddress(input_buffer));
    auto* out_ptr = static_cast<int32_t*>(env->GetDirectBufferAddress(out_action_buffer));
    if (in == nullptr || out_ptr == nullptr) return -1;

    // The caller pre-allocates the output buffer with capacity
    // 4*input_length (conservative upper bound for typical mixes of
    // printable + CSI/OSC). If the parser exceeds it, it transparently
    // copies into a heap region — the result is still readable but
    // the caller must re-fetch the pointer.
    const jint out_capacity = env->GetDirectBufferCapacity(out_action_buffer);
    vt::ActionBuffer buf(out_ptr, out_capacity);
    vt::parse(in, input_length, buf);

    return buf.length;
}

JNIEXPORT jstring JNICALL
Java_com_meshlit_terminal_nativ_NativeParser_nativeVersion(
        JNIEnv* env,
        jclass) {
    return env->NewStringUTF("vt_native/0.1.0");
}

}  // extern "C"