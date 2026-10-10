#include <jni.h>

#include <string>
#include <vector>

#include "piano_engine.h"

using mimikopi::EngineMetrics;
using mimikopi::PianoEngine;
using mimikopi::PianoTables;

namespace {
inline PianoEngine* engine(jlong handle) { return reinterpret_cast<PianoEngine*>(handle); }

std::vector<int> ints(JNIEnv* env, jintArray a) {
    std::vector<int> v(static_cast<size_t>(env->GetArrayLength(a)));
    env->GetIntArrayRegion(a, 0, static_cast<jsize>(v.size()), v.data());
    return v;
}

jstring orNull(JNIEnv* env, const std::string& s) { return s.empty() ? nullptr : env->NewStringUTF(s.c_str()); }
}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_id_mimikopi_audio_NativePiano_nativeCreate(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new PianoEngine());
}

JNIEXPORT void JNICALL Java_com_id_mimikopi_audio_NativePiano_nativeDestroy(JNIEnv*, jobject, jlong h) {
    delete engine(h);
}

JNIEXPORT jstring JNICALL Java_com_id_mimikopi_audio_NativePiano_nativeLoad(
    JNIEnv* env, jobject, jlong h, jobjectArray paths, jint packRate, jintArray noteToRegion,
    jintArray layerStart, jintArray layerCount, jintArray keyCenter, jfloatArray releaseSec,
    jintArray loVel, jintArray hiVel) {
    std::vector<std::string> files;
    jsize n = env->GetArrayLength(paths);
    files.reserve(static_cast<size_t>(n));
    for (jsize i = 0; i < n; ++i) {
        auto js = static_cast<jstring>(env->GetObjectArrayElement(paths, i));
        const char* c = env->GetStringUTFChars(js, nullptr);
        files.emplace_back(c);
        env->ReleaseStringUTFChars(js, c);
        env->DeleteLocalRef(js);
    }
    PianoTables t;
    t.packSampleRate = packRate;
    auto ntr = ints(env, noteToRegion);
    if (ntr.size() != 128) return env->NewStringUTF("noteToRegion must have 128 entries");
    std::copy(ntr.begin(), ntr.end(), t.noteToRegion.begin());
    t.regionLayerStart = ints(env, layerStart);
    t.regionLayerCount = ints(env, layerCount);
    t.regionKeyCenter = ints(env, keyCenter);
    t.regionReleaseSec.resize(static_cast<size_t>(env->GetArrayLength(releaseSec)));
    env->GetFloatArrayRegion(releaseSec, 0, static_cast<jsize>(t.regionReleaseSec.size()), t.regionReleaseSec.data());
    t.layerLoVel = ints(env, loVel);
    t.layerHiVel = ints(env, hiVel);
    return orNull(env, engine(h)->load(files, t));
}

JNIEXPORT jstring JNICALL Java_com_id_mimikopi_audio_NativePiano_nativeStart(JNIEnv* env, jobject, jlong h) {
    return orNull(env, engine(h)->start());
}

JNIEXPORT void JNICALL Java_com_id_mimikopi_audio_NativePiano_nativeStop(JNIEnv*, jobject, jlong h) { engine(h)->stop(); }

JNIEXPORT void JNICALL Java_com_id_mimikopi_audio_NativePiano_nativeNoteOn(JNIEnv*, jobject, jlong h, jint note, jint vel) {
    engine(h)->noteOn(note, vel);
}

JNIEXPORT void JNICALL Java_com_id_mimikopi_audio_NativePiano_nativeNoteOff(JNIEnv*, jobject, jlong h, jint note) {
    engine(h)->noteOff(note);
}

JNIEXPORT void JNICALL Java_com_id_mimikopi_audio_NativePiano_nativeSustain(JNIEnv*, jobject, jlong h, jboolean down) {
    engine(h)->sustain(down == JNI_TRUE);
}

JNIEXPORT void JNICALL Java_com_id_mimikopi_audio_NativePiano_nativeAllNotesOff(JNIEnv*, jobject, jlong h) {
    engine(h)->allNotesOff();
}

JNIEXPORT void JNICALL Java_com_id_mimikopi_audio_NativePiano_nativeSetGain(JNIEnv*, jobject, jlong h, jfloat g) {
    engine(h)->setMasterGain(g);
}

JNIEXPORT jdoubleArray JNICALL Java_com_id_mimikopi_audio_NativePiano_nativeMetrics(JNIEnv* env, jobject, jlong h) {
    EngineMetrics m = engine(h)->metrics();
    // Order must match NativePiano.metrics() in Kotlin.
    jdouble values[] = {
        static_cast<double>(m.sampleRate), static_cast<double>(m.framesPerBurst), static_cast<double>(m.bufferSizeFrames),
        m.estimatedLatencyMs, static_cast<double>(m.sharingExclusive), static_cast<double>(m.lowLatency),
        static_cast<double>(m.xRuns), static_cast<double>(m.activeVoices), static_cast<double>(m.maxActiveVoices),
        m.maxCallbackMicros, static_cast<double>(m.callbacks), static_cast<double>(m.restarts),
        static_cast<double>(m.deviceId), static_cast<double>(m.residentBytes),
    };
    constexpr jsize n = sizeof(values) / sizeof(values[0]);
    jdoubleArray arr = env->NewDoubleArray(n);
    env->SetDoubleArrayRegion(arr, 0, n, values);
    return arr;
}

}  // extern "C"
