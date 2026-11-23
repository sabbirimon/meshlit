// SPDX-License-Identifier: Apache-2.0
#include <jni.h>
#include "placement.h"

JNIEXPORT jlongArray JNICALL
Java_com_meshlit_core_inference_pipeline_LayerPlacement_nativePlan(
        JNIEnv *env, jobject owner, jlongArray budgets, jlong shared, jint blocks,
        jlong overhead, jintArray manual) {
    (void)owner;
    const int n = (*env)->GetArrayLength(env, budgets);
    if (n < 2 || n > 64 || (manual && (*env)->GetArrayLength(env, manual) != n)) return NULL;
    jlong input[64], result[128];
    int64_t caps[64], bytes[64];
    jint provided[64];
    int32_t specified[64], counts[64];
    (*env)->GetLongArrayRegion(env, budgets, 0, n, input);
    if (manual) (*env)->GetIntArrayRegion(env, manual, 0, n, provided);
    if ((*env)->ExceptionCheck(env)) return NULL;
    for (int i=0; i<n; ++i) {
        caps[i] = input[i];
        if (manual) specified[i] = provided[i];
    }
    if (meshlit_place_layers(caps,n,shared,blocks,overhead,manual ? specified : NULL,counts,bytes)) return NULL;
    for (int i=0; i<n; ++i) { result[i] = counts[i]; result[n+i] = bytes[i]; }
    jlongArray packed = (*env)->NewLongArray(env,2*n);
    if (packed) (*env)->SetLongArrayRegion(env,packed,0,2*n,result);
    return packed;
}
