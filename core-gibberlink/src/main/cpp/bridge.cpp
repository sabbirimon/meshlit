#include <jni.h>
#include <ggwave/ggwave.h>
#include <mutex>
#include <set>
#include <vector>
#include <cstdint>

// Upstream instance/protocol state is global. All calls, including initialization,
// are serialized; arbitrary or stale handles cannot enter the library.
static std::mutex lock;
static std::set<int> live;
static void fail(JNIEnv *e, const char *message) {
    e->ThrowNew(e->FindClass("java/lang/IllegalArgumentException"), message);
}
extern "C" JNIEXPORT jint JNICALL
Java_com_meshlit_core_gibberlink_GibberLinkCodec_open(JNIEnv *e, jobject) {
    std::lock_guard<std::mutex> guard(lock);
    if (live.size() >= 4) { fail(e, "Audio codec capacity reached"); return -1; }
    ggwave_setLogFile(nullptr);
    auto p = ggwave_getDefaultParameters();
    p.sampleRateInp = p.sampleRateOut = p.sampleRate = 48000;
    p.sampleFormatInp = p.sampleFormatOut = GGWAVE_SAMPLE_FORMAT_I16;
    p.samplesPerFrame = 1024;
    int instance = ggwave_init(p);
    if (instance < 0) { fail(e, "Cannot initialize audio codec"); return -1; }
    live.insert(instance); return instance;
}
extern "C" JNIEXPORT void JNICALL
Java_com_meshlit_core_gibberlink_GibberLinkCodec_close(JNIEnv *, jobject, jint handle) {
    std::lock_guard<std::mutex> guard(lock);
    if (live.erase(handle)) ggwave_free(handle);
}
extern "C" JNIEXPORT jshortArray JNICALL
Java_com_meshlit_core_gibberlink_GibberLinkCodec_encode(JNIEnv *e, jobject, jint handle, jbyteArray input) {
    std::lock_guard<std::mutex> guard(lock);
    const int n = input ? e->GetArrayLength(input) : 0;
    if (!live.count(handle) || n < 1 || n > 96) { fail(e, "Invalid audio packet"); return nullptr; }
    std::vector<jbyte> text(n); e->GetByteArrayRegion(input, 0, n, text.data());
    for (auto c : text) if (c < 32 || c > 126) { fail(e, "Use printable English characters"); return nullptr; }
    int bytes = ggwave_encode(handle, text.data(), n, GGWAVE_PROTOCOL_AUDIBLE_FAST, 20, nullptr, 1);
    if (bytes < 2 || bytes > 48000 * 2 * 25 || bytes % 2) { fail(e, "Audio waveform limit exceeded"); return nullptr; }
    std::vector<int16_t> pcm(bytes / 2);
    int written = ggwave_encode(handle, text.data(), n, GGWAVE_PROTOCOL_AUDIBLE_FAST, 20, pcm.data(), 0);
    if (written != bytes) { fail(e, "Audio encoding failed"); return nullptr; }
    auto out = e->NewShortArray(bytes / 2);
    if (out) e->SetShortArrayRegion(out, 0, bytes / 2, pcm.data()); return out;
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_meshlit_core_gibberlink_GibberLinkCodec_decode(JNIEnv *e, jobject, jint handle, jshortArray input, jint count) {
    std::lock_guard<std::mutex> guard(lock);
    if (!live.count(handle) || !input || count < 1 || count > 4096 || count > e->GetArrayLength(input)) {
        fail(e, "Invalid audio frame"); return nullptr;
    }
    std::vector<int16_t> pcm(count); e->GetShortArrayRegion(input, 0, count, pcm.data());
    uint8_t payload[256];
    const int n = ggwave_ndecode(handle, pcm.data(), count * 2, payload, sizeof(payload));
    // Corrupt/noisy/incomplete frames are not transcripts. Accept English packets only.
    if (n < 1 || n > 96) return nullptr;
    for (int i = 0; i < n; ++i) if (payload[i] < 32 || payload[i] > 126) return nullptr;
    auto out = e->NewByteArray(n);
    if (out) e->SetByteArrayRegion(out, 0, n, reinterpret_cast<jbyte *>(payload)); return out;
}
