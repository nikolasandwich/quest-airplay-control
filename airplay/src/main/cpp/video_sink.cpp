#include "scoped_jni_env.h"
#include "video_sink.h"
#include <android/log.h>
#include <mutex>
#include <vector>
#include <cstring>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "video_sink", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "video_sink", __VA_ARGS__)

namespace {
JavaVM*   g_vm       = nullptr;
jobject   g_sinkRef  = nullptr;
jmethodID g_onNal    = nullptr;
jmethodID g_onEnd    = nullptr;
std::mutex g_mu;

// Cache the last SPS+PPS packet so late-attaching sinks can configure.
std::vector<uint8_t> g_cachedConfig;
int64_t g_cachedConfigPts = 0;


bool isSpsOrPps(const uint8_t* data, int len) {
    if (!data || len < 5) return false;
    if (data[0] != 0 || data[1] != 0 || data[2] != 0 || data[3] != 1) return false;
    int type = data[4] & 0x1F;
    return type == 7 || type == 8;
}

void sendToSink(JNIEnv* env, const uint8_t* data, int len, int64_t pts) {
    if (!g_sinkRef || !g_onNal) return;
    if (!data || len <= 0) return;
    jbyteArray arr = env->NewByteArray(len);
    if (!arr) { if (env->ExceptionCheck()) env->ExceptionClear(); return; }
    env->SetByteArrayRegion(arr, 0, len, reinterpret_cast<const jbyte*>(data));
    env->CallVoidMethod(g_sinkRef, g_onNal, arr, static_cast<jlong>(pts));
    if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
    env->DeleteLocalRef(arr);
}
}

namespace localair {

void initJvm(JavaVM* vm) { g_vm = vm; }
JavaVM* jvm() { return g_vm; }

void setSink(JNIEnv* env, jobject sink) {
    std::lock_guard<std::mutex> lk(g_mu);
    if (g_sinkRef) { env->DeleteGlobalRef(g_sinkRef); g_sinkRef = nullptr; g_onNal = nullptr; g_onEnd = nullptr; }
    if (!sink) return;
    g_sinkRef = env->NewGlobalRef(sink);
    jclass cls = env->GetObjectClass(g_sinkRef);
    g_onNal = env->GetMethodID(cls, "onNalUnit", "([BJ)V");
    g_onEnd = env->GetMethodID(cls, "onSessionEnd", "()V");
    if (!g_onNal) LOGE("onNalUnit not found on sink");
    env->DeleteLocalRef(cls);

    // Replay cached SPS+PPS to the new sink so it can configure immediately.
    if (!g_cachedConfig.empty() && g_onNal) {
        LOGI("replaying cached config (%zu bytes) to new sink", g_cachedConfig.size());
        sendToSink(env, g_cachedConfig.data(), g_cachedConfig.size(), g_cachedConfigPts);
    }
}

void dispatchSessionEnd() {
    std::lock_guard<std::mutex> lk(g_mu);
    g_cachedConfig.clear();
    if (!g_sinkRef || !g_onEnd) return;
    ScopedJniEnv scope(g_vm);
    JNIEnv* env = scope.get();
    if (!env) return;
    env->CallVoidMethod(g_sinkRef, g_onEnd);
    if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
}

void dispatchNal(const uint8_t* data, int len, int64_t ptsUs) {
    std::lock_guard<std::mutex> lk(g_mu);

    // Cache any buffer containing SPS/PPS for late-attaching sinks.
    if (isSpsOrPps(data, len)) {
        g_cachedConfig.assign(data, data + len);
        g_cachedConfigPts = ptsUs;
    }

    if (!g_sinkRef || !g_onNal) return;
    ScopedJniEnv scope(g_vm);
    JNIEnv* env = scope.get();
    if (!env) return;
    sendToSink(env, data, len, ptsUs);
}

} // namespace localair
