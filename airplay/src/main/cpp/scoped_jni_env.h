#pragma once
#include <jni.h>

// Native receiver workers must detach before returning/exiting. Never detach a
// thread that was already owned by the VM (including Java-originating calls).
class ScopedJniEnv {
public:
    explicit ScopedJniEnv(JavaVM* vm) : vm_(vm) {
        if (!vm_) return;
        const jint status = vm_->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6);
        if (status == JNI_EDETACHED) {
            attached_ = vm_->AttachCurrentThread(&env_, nullptr) == JNI_OK;
            if (!attached_) env_ = nullptr;
        } else if (status != JNI_OK) env_ = nullptr;
    }
    ~ScopedJniEnv() { if (attached_) vm_->DetachCurrentThread(); }
    ScopedJniEnv(const ScopedJniEnv&) = delete;
    ScopedJniEnv& operator=(const ScopedJniEnv&) = delete;
    JNIEnv* get() const { return env_; }
private:
    JavaVM* vm_;
    JNIEnv* env_ = nullptr;
    bool attached_ = false;
};
