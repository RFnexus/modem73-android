#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "core_smoke.hh"
#include <pty.h>
#include <termios.h>
#include <poll.h>
#include <thread>
#include <atomic>
#include <unistd.h>
#include "engine.hh"
#ifdef WITH_HAMLIB
#include <hamlib/rig.h>
#endif

#ifndef MODEM73_VERSION
#define MODEM73_VERSION "dev"
#endif

#define LOG_TAG "modem73-native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

JavaVM* g_vm = nullptr;
jclass g_native_core_class = nullptr;
jmethodID g_on_ptt = nullptr;

std::string to_std(JNIEnv* env, jstring s) {
    if (!s) return "";
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

jstring to_java(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

bool call_ptt(bool on) {
    if (!g_vm || !g_native_core_class || !g_on_ptt) return false;
    JNIEnv* env = nullptr;
    bool attached = false;
    int rc = g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (rc == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return false;
        attached = true;
    } else if (rc != JNI_OK) {
        return false;
    }
    jboolean ok = env->CallStaticBooleanMethod(g_native_core_class, g_on_ptt, on ? JNI_TRUE : JNI_FALSE);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        ok = JNI_FALSE;
    }
    if (attached) g_vm->DetachCurrentThread();
    return ok == JNI_TRUE;
}

}  // namespace

static int g_pty_master = -1;
static int g_pty_slave = -1;
static std::atomic<bool> g_pty_run{false};
static std::thread g_pty_thread;
static jmethodID g_on_rig_data = nullptr;

static void pty_close() {
    g_pty_run = false;
    if (g_pty_thread.joinable()) g_pty_thread.join();
    if (g_pty_master >= 0) close(g_pty_master);
    if (g_pty_slave >= 0) close(g_pty_slave);
    g_pty_master = -1;
    g_pty_slave = -1;
}

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {

    g_vm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass local = env->FindClass("app/modem73/NativeCore");
    if (local) {
        g_native_core_class = reinterpret_cast<jclass>(env->NewGlobalRef(local));
        g_on_ptt = env->GetStaticMethodID(g_native_core_class, "onExternalPtt", "(Z)Z");
        g_on_rig_data = env->GetStaticMethodID(g_native_core_class, "onRigData", "([B)V");
        if (!g_on_ptt) env->ExceptionClear();
    }
    Modem73Engine::instance().ptt_hook = [](bool on) { return call_ptt(on); };
    return JNI_VERSION_1_6;

}

JNIEXPORT jstring JNICALL
Java_app_modem73_NativeCore_ptyCreate(JNIEnv* env, jobject) {

    pty_close();
    int master = -1, slave = -1;
    char name[128];
    if (openpty(&master, &slave, name, nullptr, nullptr) != 0) return env->NewStringUTF("");
    struct termios t;
    if (tcgetattr(slave, &t) == 0) {
        cfmakeraw(&t);
        tcsetattr(slave, TCSANOW, &t);
    }
    
    g_pty_master = master;
    g_pty_slave = slave;
    g_pty_run = true;
    g_pty_thread = std::thread([] {
        std::vector<uint8_t> buf(512);
        while (g_pty_run) {
            struct pollfd pfd{g_pty_master, POLLIN, 0};
            int pr = poll(&pfd, 1, 100);
            if (pr <= 0) continue;
            ssize_t n = read(g_pty_master, buf.data(), buf.size());
            if (n <= 0) continue;
            JNIEnv* e = nullptr;
            bool attached = false;
            int rc = g_vm->GetEnv(reinterpret_cast<void**>(&e), JNI_VERSION_1_6);
            if (rc == JNI_EDETACHED) {
                if (g_vm->AttachCurrentThread(&e, nullptr) != JNI_OK) continue;
                attached = true;
            } else if (rc != JNI_OK) {
                continue;
            }
            jbyteArray arr = e->NewByteArray((jsize)n);
            e->SetByteArrayRegion(arr, 0, (jsize)n, reinterpret_cast<const jbyte*>(buf.data()));
            e->CallStaticVoidMethod(g_native_core_class, g_on_rig_data, arr);
            if (e->ExceptionCheck()) e->ExceptionClear();
            e->DeleteLocalRef(arr);
            if (attached) g_vm->DetachCurrentThread();
        }
    });
    return env->NewStringUTF(name);
}

JNIEXPORT void JNICALL
Java_app_modem73_NativeCore_ptyPush(JNIEnv* env, jobject, jbyteArray data) {
    if (g_pty_master < 0) return;
    jsize n = env->GetArrayLength(data);
    std::vector<uint8_t> buf(n);
    env->GetByteArrayRegion(data, 0, n, reinterpret_cast<jbyte*>(buf.data()));
    size_t off = 0;
    while (off < buf.size()) {
        ssize_t w = write(g_pty_master, buf.data() + off, buf.size() - off);
        if (w <= 0) break;
        off += (size_t)w;
    }
}

JNIEXPORT void JNICALL
Java_app_modem73_NativeCore_ptyClose(JNIEnv*, jobject) {
    pty_close();
}

JNIEXPORT jstring JNICALL
Java_app_modem73_NativeCore_hamlibRigList(JNIEnv* env, jobject) {
#ifdef WITH_HAMLIB
    std::string out;
    rig_load_all_backends();
    rig_list_foreach([](const struct rig_caps* caps, void* data) -> int {
        auto* s = static_cast<std::string*>(data);
        *s += std::to_string(caps->rig_model) + "|" + (caps->mfg_name ? caps->mfg_name : "") + "|" +
              (caps->model_name ? caps->model_name : "") + "|" + (caps->version ? caps->version : "") + "\n";
        return 1;
    }, &out);
    return env->NewStringUTF(out.c_str());
#else
    return env->NewStringUTF("");
#endif
}

JNIEXPORT jstring JNICALL
Java_app_modem73_NativeCore_version(JNIEnv* env, jobject) {
    return env->NewStringUTF(MODEM73_VERSION);
}

JNIEXPORT jstring JNICALL
Java_app_modem73_NativeCore_buildInfo(JNIEnv* env, jobject) {
    return to_java(env, modem73_build_info());
}

JNIEXPORT jstring JNICALL
Java_app_modem73_NativeCore_loopbackSelfTest(JNIEnv* env, jobject) {
    std::string r = modem73_loopback_selftest();
    LOGI("%s", r.c_str());
    return to_java(env, r);
}

JNIEXPORT jboolean JNICALL
Java_app_modem73_NativeCore_engineStart(JNIEnv* env, jobject, jstring configJson, jstring homeDir) {
    std::string err;
    bool ok = Modem73Engine::instance().start(to_std(env, configJson), to_std(env, homeDir), err);
    if (!ok) LOGE("engineStart failed: %s", err.c_str());
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_app_modem73_NativeCore_engineStop(JNIEnv*, jobject) {
    Modem73Engine::instance().stop();
}

JNIEXPORT jboolean JNICALL
Java_app_modem73_NativeCore_engineRunning(JNIEnv*, jobject) {
    return Modem73Engine::instance().running() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_app_modem73_NativeCore_lastError(JNIEnv* env, jobject) {
    return to_java(env, Modem73Engine::instance().last_error());
}

JNIEXPORT jstring JNICALL
Java_app_modem73_NativeCore_statusJson(JNIEnv* env, jobject) {
    return to_java(env, Modem73Engine::instance().status_json());
}

JNIEXPORT jstring JNICALL
Java_app_modem73_NativeCore_snapshotJson(JNIEnv* env, jobject) {
    return to_java(env, Modem73Engine::instance().snapshot_json());
}

JNIEXPORT jstring JNICALL
Java_app_modem73_NativeCore_configJson(JNIEnv* env, jobject) {
    return to_java(env, Modem73Engine::instance().config_json());
}

JNIEXPORT jstring JNICALL
Java_app_modem73_NativeCore_setConfigJson(JNIEnv* env, jobject, jstring json) {
    std::string err;
    bool ok = Modem73Engine::instance().set_config_json(to_std(env, json), err);
    return to_java(env, ok ? "" : (err.empty() ? "rejected" : err));
}

JNIEXPORT void JNICALL
Java_app_modem73_NativeCore_queueData(JNIEnv* env, jobject, jbyteArray data, jint operMode) {
    if (!data) return;
    jsize n = env->GetArrayLength(data);
    std::vector<uint8_t> bytes(n);
    env->GetByteArrayRegion(data, 0, n, reinterpret_cast<jbyte*>(bytes.data()));
    Modem73Engine::instance().queue_data(bytes, operMode);
}

JNIEXPORT void JNICALL
Java_app_modem73_NativeCore_sendChat(JNIEnv* env, jobject, jstring text) {
    Modem73Engine::instance().send_chat(to_std(env, text));
}

JNIEXPORT jstring JNICALL
Java_app_modem73_NativeCore_rigctlCommand(JNIEnv* env, jobject, jstring cmd) {
    return to_java(env, Modem73Engine::instance().rigctl(to_std(env, cmd)));
}

JNIEXPORT void JNICALL
Java_app_modem73_NativeCore_setRigPoll(JNIEnv*, jobject, jboolean on) {
    Modem73Engine::instance().set_rig_poll(on == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_app_modem73_NativeCore_rigRefresh(JNIEnv*, jobject) {
    Modem73Engine::instance().rig_refresh();
}

JNIEXPORT jfloat JNICALL
Java_app_modem73_NativeCore_alcTune(JNIEnv*, jobject) {
    return Modem73Engine::instance().alc_tune();
}

JNIEXPORT jfloatArray JNICALL
Java_app_modem73_NativeCore_takeWaterfall(JNIEnv* env, jobject) {
    int bins = 0;
    std::vector<float> rows = Modem73Engine::instance().take_waterfall(bins);
    jfloatArray arr = env->NewFloatArray((jsize)rows.size());
    if (!rows.empty()) env->SetFloatArrayRegion(arr, 0, (jsize)rows.size(), rows.data());
    return arr;
}

JNIEXPORT jint JNICALL
Java_app_modem73_NativeCore_waterfallBins(JNIEnv*, jobject) {
    return 128;
}

JNIEXPORT void JNICALL
Java_app_modem73_NativeCore_resetStats(JNIEnv*, jobject) {
    Modem73Engine::instance().reset_stats();
}

JNIEXPORT jstring JNICALL
Java_app_modem73_NativeCore_debugRecord(JNIEnv* env, jobject, jint seconds) {
    return to_java(env, Modem73Engine::instance().debug_record(seconds));
}

JNIEXPORT jstring JNICALL
Java_app_modem73_NativeCore_audioDevicesJson(JNIEnv* env, jobject) {
    return to_java(env, Modem73Engine::instance().audio_devices_json());
}

}
