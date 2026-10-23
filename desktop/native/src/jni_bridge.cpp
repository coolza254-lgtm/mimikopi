// JNI entry points for com.id.mimikopi.desktop.audio.NativeAudio.
#include <jni.h>

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif

#include <cstdio>
#include <string>
#include <vector>

#include "clock.h"
#include "desktop_engine.h"
#include "miniaudio.h"

using mimikopi::DesktopEngine;

namespace {

DesktopEngine* engine(jlong h) { return reinterpret_cast<DesktopEngine*>(h); }

jstring str(JNIEnv* env, const std::string& s) { return s.empty() ? nullptr : env->NewStringUTF(s.c_str()); }

std::string utf8(JNIEnv* env, jstring s) {
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

std::vector<int> ints(JNIEnv* env, jintArray a) {
    std::vector<int> out(static_cast<size_t>(env->GetArrayLength(a)));
    env->GetIntArrayRegion(a, 0, static_cast<jsize>(out.size()), reinterpret_cast<jint*>(out.data()));
    return out;
}

#ifdef _WIN32
FILE* openForWrite(const std::string& utf8Path) {
    int n = MultiByteToWideChar(CP_UTF8, 0, utf8Path.c_str(), -1, nullptr, 0);
    std::wstring w(static_cast<size_t>(n), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, utf8Path.c_str(), -1, w.data(), n);
    return _wfopen(w.c_str(), L"wb");
}
#else
FILE* openForWrite(const std::string& path) { return std::fopen(path.c_str(), "wb"); }
#endif

}  // namespace

extern "C" {

#define FN(name) JNIEXPORT JNICALL Java_com_id_mimikopi_desktop_audio_NativeAudio_##name

jlong FN(nativeCreate)(JNIEnv*, jclass) { return reinterpret_cast<jlong>(new DesktopEngine()); }

void FN(nativeDestroy)(JNIEnv*, jclass, jlong h) { delete engine(h); }

jlong FN(nativeNowNanos)(JNIEnv*, jclass) { return mimikopi::nowNanos(); }

jstring FN(nativeLoad)(JNIEnv* env, jclass, jlong h, jobjectArray paths, jint packRate, jintArray noteToRegion,
                       jintArray layerStart, jintArray layerCount, jintArray keyCenter, jfloatArray releaseSec,
                       jintArray loVel, jintArray hiVel) {
    std::vector<std::string> files;
    const jsize n = env->GetArrayLength(paths);
    for (jsize i = 0; i < n; ++i) {
        auto s = static_cast<jstring>(env->GetObjectArrayElement(paths, i));
        files.push_back(utf8(env, s));
        env->DeleteLocalRef(s);
    }
    mimikopi::PianoTables t;
    t.packSampleRate = packRate;
    auto map = ints(env, noteToRegion);
    for (size_t i = 0; i < 128 && i < map.size(); ++i) t.noteToRegion[i] = map[i];
    t.regionLayerStart = ints(env, layerStart);
    t.regionLayerCount = ints(env, layerCount);
    t.regionKeyCenter = ints(env, keyCenter);
    t.regionReleaseSec.resize(static_cast<size_t>(env->GetArrayLength(releaseSec)));
    env->GetFloatArrayRegion(releaseSec, 0, static_cast<jsize>(t.regionReleaseSec.size()), t.regionReleaseSec.data());
    t.layerLoVel = ints(env, loVel);
    t.layerHiVel = ints(env, hiVel);
    return str(env, engine(h)->load(files, t));
}

void FN(nativeUnload)(JNIEnv*, jclass, jlong h) { engine(h)->unload(); }

jstring FN(nativeStart)(JNIEnv* env, jclass, jlong h, jint periodFrames, jboolean exclusive, jboolean nullBackend) {
    return str(env, engine(h)->start(periodFrames, exclusive == JNI_TRUE, nullBackend == JNI_TRUE));
}

void FN(nativeStop)(JNIEnv*, jclass, jlong h) { engine(h)->stop(); }

jboolean FN(nativeRunning)(JNIEnv*, jclass, jlong h) { return engine(h)->running() ? JNI_TRUE : JNI_FALSE; }

void FN(nativeNoteOn)(JNIEnv*, jclass, jlong h, jint note, jint velocity) { engine(h)->noteOn(note, velocity); }

void FN(nativeNoteOff)(JNIEnv*, jclass, jlong h, jint note) { engine(h)->noteOff(note); }

void FN(nativeSustain)(JNIEnv*, jclass, jlong h, jboolean down) { engine(h)->sustain(down == JNI_TRUE); }

void FN(nativeAllNotesOff)(JNIEnv*, jclass, jlong h) { engine(h)->allNotesOff(); }

void FN(nativeSetMasterGain)(JNIEnv*, jclass, jlong h, jfloat gain) { engine(h)->setMasterGain(gain); }

void FN(nativeSetTone)(JNIEnv*, jclass, jlong h, jfloat reverb, jfloat warmth) { engine(h)->setTone(reverb, warmth); }

void FN(nativeSetMetronome)(JNIEnv* env, jclass, jlong h, jboolean on, jdouble bpm, jint ticksPerBeat, jintArray levels, jfloat volume) {
    engine(h)->setMetronome(on == JNI_TRUE, bpm, ticksPerBeat, ints(env, levels), volume);
}

jlong FN(nativeMetronomeTickCount)(JNIEnv*, jclass, jlong h) { return engine(h)->metronomeTickCount(); }

jlongArray FN(nativeDrainTicks)(JNIEnv* env, jclass, jlong h) {
    auto ticks = engine(h)->drainTicks();
    jlongArray out = env->NewLongArray(static_cast<jsize>(ticks.size()));
    if (!ticks.empty()) env->SetLongArrayRegion(out, 0, static_cast<jsize>(ticks.size()), reinterpret_cast<const jlong*>(ticks.data()));
    return out;
}

jlongArray FN(nativeTimestamp)(JNIEnv* env, jclass, jlong h) {
    int64_t frame = 0, nanos = 0;
    if (!engine(h)->timestamp(frame, nanos)) return nullptr;
    jlongArray out = env->NewLongArray(2);
    jlong v[2] = {frame, nanos};
    env->SetLongArrayRegion(out, 0, 2, v);
    return out;
}

jdoubleArray FN(nativeMetrics)(JNIEnv* env, jclass, jlong h) {
    auto m = engine(h)->metrics();
    const jdouble v[] = {
        static_cast<double>(m.sampleRate), static_cast<double>(m.periodFrames), static_cast<double>(m.periods),
        m.estimatedLatencyMs, static_cast<double>(m.exclusive), static_cast<double>(m.activeVoices),
        static_cast<double>(m.maxActiveVoices), m.maxCallbackMicros, static_cast<double>(m.callbacks),
        static_cast<double>(m.restarts), static_cast<double>(m.residentBytes), static_cast<double>(m.samplesLoaded),
        static_cast<double>(m.stoppedByDevice),
    };
    const jsize n = static_cast<jsize>(sizeof(v) / sizeof(v[0]));
    jdoubleArray out = env->NewDoubleArray(n);
    env->SetDoubleArrayRegion(out, 0, n, v);
    return out;
}

jfloatArray FN(nativeRender)(JNIEnv* env, jclass, jlong h, jint frames) {
    std::vector<float> buf(static_cast<size_t>(frames) * 2);
    engine(h)->render(buf.data(), static_cast<uint32_t>(frames), 2);
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(buf.size()));
    env->SetFloatArrayRegion(out, 0, static_cast<jsize>(buf.size()), buf.data());
    return out;
}

/**
 * Decodes one FLAC file of the sample pack to raw 16-bit interleaved stereo (the engine's .pcm format).
 * Returns {frames, sampleRate} or null on failure.
 */
jlongArray FN(nativeDecodeToPcm16)(JNIEnv* env, jclass, jstring src, jstring dst) {
    const std::string in = utf8(env, src);
    const std::string outPath = utf8(env, dst);
    ma_decoder_config cfg = ma_decoder_config_init(ma_format_s16, 2, 0);
    ma_decoder decoder;
#ifdef _WIN32
    int n = MultiByteToWideChar(CP_UTF8, 0, in.c_str(), -1, nullptr, 0);
    std::wstring w(static_cast<size_t>(n), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, in.c_str(), -1, w.data(), n);
    if (ma_decoder_init_file_w(w.c_str(), &cfg, &decoder) != MA_SUCCESS) return nullptr;
#else
    if (ma_decoder_init_file(in.c_str(), &cfg, &decoder) != MA_SUCCESS) return nullptr;
#endif
    FILE* f = openForWrite(outPath);
    if (f == nullptr) {
        ma_decoder_uninit(&decoder);
        return nullptr;
    }
    std::vector<int16_t> buf(4096 * 2);
    jlong total = 0;
    bool ok = true;
    for (;;) {
        ma_uint64 got = 0;
        ma_result r = ma_decoder_read_pcm_frames(&decoder, buf.data(), 4096, &got);
        if (got > 0) {
            if (std::fwrite(buf.data(), sizeof(int16_t) * 2, static_cast<size_t>(got), f) != got) {
                ok = false;
                break;
            }
            total += static_cast<jlong>(got);
        }
        if (r != MA_SUCCESS || got == 0) break;
    }
    const jlong rate = decoder.outputSampleRate;
    ma_decoder_uninit(&decoder);
    if (std::fclose(f) != 0) ok = false;
    if (!ok || total == 0) return nullptr;
    jlongArray out = env->NewLongArray(2);
    jlong v[2] = {total, rate};
    env->SetLongArrayRegion(out, 0, 2, v);
    return out;
}

}  // extern "C"
