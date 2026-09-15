#include "scoped_jni_env.h"
#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <cstring>
#include <mutex>

#include "video_sink.h"
#include "audio_sink.h"

#if HAVE_RPIPLAY
extern "C" {
#include "raop.h"
#include "stream.h"
#include "dnssd.h"
}
#endif

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "airplay_native", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "airplay_native", __VA_ARGS__)

namespace {

jclass    g_nativeClass = nullptr;
jmethodID g_onConnInit  = nullptr;

#if HAVE_RPIPLAY
raop_t*  g_raop  = nullptr;
dnssd_t* g_dnssd = nullptr;
std::mutex g_videoSessionMutex;
raop_ntp_t* g_videoOwner = nullptr;

void audio_process(void*, raop_ntp_t*, aac_decode_struct* data) {
    localair::dispatchAac(data->data, data->data_len, static_cast<int64_t>(data->pts));
}
void video_process(void*, raop_ntp_t* owner, h264_decode_struct* data) {
    std::lock_guard<std::mutex> lock(g_videoSessionMutex);
    if (g_videoOwner != owner) {
        localair::dispatchSessionEnd();
        g_videoOwner = owner;
    }
    localair::dispatchNal(data->data, data->data_len, static_cast<int64_t>(data->pts));
}
void video_session_end(void*, raop_ntp_t* owner) {
    std::lock_guard<std::mutex> lock(g_videoSessionMutex);
    if (g_videoOwner != owner) return;
    g_videoOwner = nullptr;
    localair::dispatchSessionEnd();
}
void conn_init(void*) {
    LOGI("client connected");
    if (!g_nativeClass || !g_onConnInit) return;
    JavaVM* vm = localair::jvm();
    if (!vm) return;
    ScopedJniEnv scope(vm);
    JNIEnv* e = scope.get();
    if (!e) return;
    e->CallStaticVoidMethod(g_nativeClass, g_onConnInit);
    if (e->ExceptionCheck()) { e->ExceptionDescribe(); e->ExceptionClear(); }
}
void conn_destroy(void*) {
    LOGI("client disconnected");
}
void audio_flush(void*)  {}
void video_flush(void*)  {}
void audio_set_volume(void*, float)                       {}
void audio_set_metadata(void*, const void*, int)          {}
void audio_set_coverart(void*, const void*, int)          {}
void log_callback(void*, int level, const char* msg) {
    __android_log_print(ANDROID_LOG_DEBUG, "rpiplay", "[%d] %s", level, msg);
}
#endif

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_localair_airplay_nativebridge_AirPlayNative_nativeStart(JNIEnv* env, jclass, jstring name, jbyteArray address) {
#if HAVE_RPIPLAY
    raop_callbacks_t cbs{};
    cbs.audio_process      = audio_process;
    cbs.video_process      = video_process;
    cbs.conn_init          = conn_init;
    cbs.conn_destroy       = conn_destroy;
    cbs.video_session_end  = video_session_end;
    cbs.audio_flush        = audio_flush;
    cbs.video_flush        = video_flush;
    cbs.audio_set_volume   = audio_set_volume;
    cbs.audio_set_metadata = audio_set_metadata;
    cbs.audio_set_coverart = audio_set_coverart;

    g_raop = raop_init(10, &cbs);
    if (!g_raop) { LOGE("raop_init failed"); return 0; }
    raop_set_log_callback(g_raop, log_callback, nullptr);
    raop_set_log_level(g_raop, RAOP_LOG_INFO);

    // dnssd_stub.c implements this API as a no-op store for name + hw_addr.
    // Kotlin (NsdManager) handles actual Bonjour advertising.
    if (!name || !address || env->GetArrayLength(address) != 6) {
        raop_destroy(g_raop); g_raop = nullptr; return 0;
    }
    char hw_addr[6];
    env->GetByteArrayRegion(address, 0, 6, reinterpret_cast<jbyte*>(hw_addr));
    const char* receiver_name = env->GetStringUTFChars(name, nullptr);
    if (!receiver_name) { raop_destroy(g_raop); g_raop = nullptr; return 0; }
    int err = 0;
    g_dnssd = dnssd_init(receiver_name, strlen(receiver_name), hw_addr, 6, &err);
    env->ReleaseStringUTFChars(name, receiver_name);
    if (!g_dnssd) { LOGE("dnssd_init failed: %d", err); raop_destroy(g_raop); g_raop = nullptr; return 0; }
    raop_set_dnssd(g_raop, g_dnssd);

    unsigned short port = 0;
    if (raop_start(g_raop, &port) < 0) {
        LOGE("raop_start failed");
        dnssd_destroy(g_dnssd); g_dnssd = nullptr;
        raop_destroy(g_raop);   g_raop  = nullptr;
        return 0;
    }
    LOGI("raop listening on %d", port);
    return static_cast<jint>(port);
#else
    LOGI("stub start — RPiPlay not vendored");
    return 0;
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_localair_airplay_nativebridge_AirPlayNative_nativeIsRunning(JNIEnv*, jclass) {
#if HAVE_RPIPLAY
    return g_raop && raop_is_running(g_raop) ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_localair_airplay_nativebridge_AirPlayNative_nativeStop(JNIEnv*, jclass) {
#if HAVE_RPIPLAY
    if (g_raop)  { raop_stop(g_raop); raop_destroy(g_raop); g_raop = nullptr; }
    if (g_dnssd) { dnssd_destroy(g_dnssd); g_dnssd = nullptr; }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_localair_airplay_nativebridge_AirPlayNative_nativeSetSink(JNIEnv* env, jclass, jobject sink) {
    localair::setSink(env, sink);
}

extern "C" JNIEXPORT void JNICALL
Java_com_localair_airplay_nativebridge_AirPlayNative_nativeSetAudioSink(JNIEnv* env, jclass, jobject sink) {
    localair::setAudioSink(env, sink);
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    localair::initJvm(vm);
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return -1;
    jclass cls = env->FindClass("com/localair/airplay/nativebridge/AirPlayNative");
    if (cls) {
        g_nativeClass = reinterpret_cast<jclass>(env->NewGlobalRef(cls));
        g_onConnInit = env->GetStaticMethodID(cls, "onConnectionInit", "()V");
        env->DeleteLocalRef(cls);
    }
    return JNI_VERSION_1_6;
}
