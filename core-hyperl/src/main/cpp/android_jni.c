/* SPDX-License-Identifier: LicenseRef-HyperL-Community-1.0 */
#include "hyperl.h"
#include <jni.h>
#include <stdlib.h>

struct cancellation {
    JNIEnv *env;
    jobject callback;
    jmethodID method;
};
static int stopped(void *value) {
    struct cancellation *c = value;
    if ((*c->env)->ExceptionCheck(c->env)) return 1;
    return (*c->env)->CallBooleanMethod(c->env, c->callback, c->method) ||
           (*c->env)->ExceptionCheck(c->env);
}
static void fail(JNIEnv *env, enum hl_status status) {
    if ((*env)->ExceptionCheck(env)) return;
    const char *type = status == HL_CANCELLED ? "kotlinx/coroutines/JobCancellationException" :
                       status == HL_MEMORY ? "java/lang/OutOfMemoryError" : "java/lang/IllegalArgumentException";
    /* CancellationException has a public String constructor on every supported runtime. */
    if (status == HL_CANCELLED) type = "java/util/concurrent/CancellationException";
    jclass cls = (*env)->FindClass(env, type);
    if (cls) { (*env)->ThrowNew(env, cls, status == HL_CANCELLED ? "HyperL stopped" :
        status == HL_MEMORY ? "HyperL allocation failed" : status == HL_NONFINITE ? "HyperL nonfinite value" : "Invalid HyperL native graph");
        (*env)->DeleteLocalRef(env, cls); }
}
static int callback(JNIEnv *env, jobject object, struct cancellation *c) {
    if (!object) return 0;
    jclass cls = (*env)->GetObjectClass(env, object);
    if (!cls) return 0;
    jmethodID method = (*env)->GetMethodID(env, cls, "cancelled", "()Z");
    (*env)->DeleteLocalRef(env, cls);
    if (!method) return 0;
    c->env = env; c->callback = object; c->method = method;
    return 1;
}
JNIEXPORT jstring JNICALL Java_com_meshlit_core_hyperl_NativeBridge_version(JNIEnv *env, jclass cls) {
    (void)cls;
    return (*env)->NewStringUTF(env, hl_version());
}
JNIEXPORT jfloatArray JNICALL Java_com_meshlit_core_hyperl_NativeBridge_execute(
    JNIEnv *env, jclass cls, jobjectArray arrays, jintArray operations, jintArray a, jintArray b,
    jint output_index, jobject cancel) {
    (void)cls;
    struct hl_vector inputs[HL_MAX_INPUTS] = {{0}};
    struct hl_step steps[HL_MAX_STEPS] = {{0}};
    size_t lengths[HL_MAX_VALUES] = {0};
    float *output = NULL;
    jfloatArray result = NULL;
    enum hl_status status = HL_INVALID;
    struct cancellation context;
    if (!arrays || !operations || !a || !b || !callback(env, cancel, &context)) goto done;
    jsize n = (*env)->GetArrayLength(env, arrays), s = (*env)->GetArrayLength(env, operations);
    if (n < 1 || n > (jsize)HL_MAX_INPUTS || s < 1 || s > (jsize)HL_MAX_STEPS ||
        (*env)->GetArrayLength(env, a) != s || (*env)->GetArrayLength(env, b) != s ||
        output_index < 0 || output_index >= n + s) goto done;
    jint op[HL_MAX_STEPS], aa[HL_MAX_STEPS], bb[HL_MAX_STEPS];
    (*env)->GetIntArrayRegion(env, operations, 0, s, op);
    (*env)->GetIntArrayRegion(env, a, 0, s, aa);
    (*env)->GetIntArrayRegion(env, b, 0, s, bb);
    if ((*env)->ExceptionCheck(env)) goto done;
    size_t retained = 0;
    for (jsize i = 0; i < n; i++) {
        jfloatArray array = (*env)->GetObjectArrayElement(env, arrays, i);
        if (!array) goto done;
        jsize count = (*env)->GetArrayLength(env, array);
        (*env)->DeleteLocalRef(env, array);
        if (count < 1 || count > (jsize)HL_MAX_VECTOR_ELEMENTS) goto done;
        lengths[i] = (size_t)count; retained += lengths[i];
    }
    for (jsize i = 0; i < s; i++) {
        if (op[i] < HL_ADD || op[i] > HL_SUM || aa[i] < 0 || aa[i] >= n + i) goto done;
        if ((op[i] == HL_ADD || op[i] == HL_MULTIPLY) &&
            (bb[i] < 0 || bb[i] >= n + i || lengths[aa[i]] != lengths[bb[i]])) goto done;
        lengths[n + i] = op[i] == HL_SUM ? 1 : lengths[aa[i]];
        retained += lengths[n + i];
        steps[i].operation = (enum hl_operation)op[i]; steps[i].a = (size_t)aa[i]; steps[i].b = (size_t)bb[i];
    }
    if (retained > HL_MAX_RETAINED_ELEMENTS) goto done;
    status = HL_MEMORY;
    for (jsize i = 0; i < n; i++) {
        if (stopped(&context)) { status = HL_CANCELLED; goto done; }
        inputs[i].length = lengths[i];
        float *data = malloc(lengths[i] * sizeof(float));
        inputs[i].data = data;
        if (!data) goto done;
        jfloatArray array = (*env)->GetObjectArrayElement(env, arrays, i);
        if (!array) goto done;
        (*env)->GetFloatArrayRegion(env, array, 0, (jsize)lengths[i], data);
        (*env)->DeleteLocalRef(env, array);
        if ((*env)->ExceptionCheck(env)) goto done;
    }
    output = malloc(lengths[output_index] * sizeof(float));
    if (!output) goto done;
    size_t count = 0;
    status = hl_execute(inputs, (size_t)n, steps, (size_t)s, (size_t)output_index, output,
                        lengths[output_index], &count, stopped, &context);
    if (status != HL_OK) goto done;
    if (stopped(&context)) { status = HL_CANCELLED; goto done; }
    result = (*env)->NewFloatArray(env, (jsize)count);
    if (result) (*env)->SetFloatArrayRegion(env, result, 0, (jsize)count, output);
done:
    for (size_t i = 0; i < HL_MAX_INPUTS; i++) free((void *)inputs[i].data);
    free(output);
    if (status != HL_OK) fail(env, status);
    return result;
}
JNIEXPORT jfloat JNICALL Java_com_meshlit_core_hyperl_NativeBridge_precise(
    JNIEnv *env, jclass cls, jfloatArray array, jobject cancel) {
    (void)cls;
    struct cancellation context;
    if (!array || !callback(env, cancel, &context)) { fail(env, HL_INVALID); return 0; }
    jsize count = (*env)->GetArrayLength(env, array);
    if (count < 1 || count > (jsize)HL_MAX_VECTOR_ELEMENTS) { fail(env, HL_INVALID); return 0; }
    float *data = malloc((size_t)count * sizeof(float));
    if (!data) { fail(env, HL_MEMORY); return 0; }
    (*env)->GetFloatArrayRegion(env, array, 0, count, data);
    float result = 0;
    enum hl_status status = HL_CANCELLED;
    if (!(*env)->ExceptionCheck(env)) {
        struct hl_vector input = {data, (size_t)count};
        status = hl_sum_precise(&input, &result, stopped, &context);
    }
    free(data);
    if (status != HL_OK) fail(env, status);
    return result;
}
