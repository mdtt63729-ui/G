#include <jni.h>
#include <android/log.h>
#include <fstream>
#include <string>
#include <algorithm>
#include <cctype>

namespace {
constexpr const char* TAG = "GitofySecurity";

bool mapsContain(const char* token) {
    std::ifstream in("/proc/self/maps");
    if (!in) return false;
    std::string line;
    std::string needle(token);
    std::transform(needle.begin(), needle.end(), needle.begin(), ::tolower);
    while (std::getline(in, line)) {
        std::transform(line.begin(), line.end(), line.begin(), ::tolower);
        if (line.find(needle) != std::string::npos) return true;
    }
    return false;
}

int tracerPid() {
    std::ifstream in("/proc/self/status");
    if (!in) return 0;
    std::string line;
    while (std::getline(in, line)) {
        if (line.rfind("TracerPid:", 0) == 0) {
            try { return std::stoi(line.substr(10)); } catch (...) { return 0; }
        }
    }
    return 0;
}
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gitofy_core_security_NativeSecurity_nativeEnvironmentFlags(JNIEnv*, jclass) {
    jint flags = 0;
    if (tracerPid() > 0) flags |= 1;
    if (mapsContain("frida-agent") || mapsContain("frida-gadget") || mapsContain("libfrida")) flags |= 2;
    if (mapsContain("xposed") || mapsContain("lsposed") || mapsContain("substrate")) flags |= 4;
    return flags;
}


extern "C" JNIEXPORT jboolean JNICALL
Java_com_gitofy_core_security_NativeSecurity_nativeValidateIdentity(
        JNIEnv* env, jclass /*clazz*/, jstring packageName, jstring appLabel) {
    if (packageName == nullptr || appLabel == nullptr) return JNI_FALSE;

    const char* packageChars = env->GetStringUTFChars(packageName, nullptr);
    const char* labelChars = env->GetStringUTFChars(appLabel, nullptr);
    if (packageChars == nullptr || labelChars == nullptr) {
        if (packageChars != nullptr) env->ReleaseStringUTFChars(packageName, packageChars);
        if (labelChars != nullptr) env->ReleaseStringUTFChars(appLabel, labelChars);
        return JNI_FALSE;
    }

    const bool valid = std::string(packageChars) == "com.gitofy" &&
                       std::string(labelChars) == "GITOFY";

    env->ReleaseStringUTFChars(packageName, packageChars);
    env->ReleaseStringUTFChars(appLabel, labelChars);
    return valid ? JNI_TRUE : JNI_FALSE;
}
