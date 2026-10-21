#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "effects.h"  // shared with the Android engine (audio/src/main/cpp)
#include "sample_store.h"

struct ma_device;

namespace mimikopi {

/** Lookup tables built in Kotlin by core's SampleMap and handed over at load time (same as Android). */
struct PianoTables {
    int packSampleRate = 48000;
    std::array<int, 128> noteToRegion{};
    std::vector<int> regionLayerStart, regionLayerCount, regionKeyCenter;
    std::vector<float> regionReleaseSec;
    std::vector<int> layerLoVel, layerHiVel;
};

struct EngineMetrics {
    int sampleRate = 0;
    int periodFrames = 0;
    int periods = 0;
    double estimatedLatencyMs = -1;
    int exclusive = 0;
    int activeVoices = 0;
    int maxActiveVoices = 0;
    double maxCallbackMicros = 0;
    int64_t callbacks = 0;
    int restarts = 0;
    int64_t residentBytes = 0;
    int samplesLoaded = 0;
    int stoppedByDevice = 0;
};

/**
 * The Windows piano: the Android engine's sampler, effects and sample-accurate metronome, driven by a miniaudio
 * device (WASAPI, exclusive mode first, shared as the fallback) instead of Oboe. Notes are queued by any thread and
 * applied at the start of the next callback; nothing in the callback allocates, locks or does I/O.
 *
 * Without an installed sample pack the engine plays a simple built-in tone, so prompts, answers, demos and clicks
 * always sound; the app says which sound is in use.
 */
class DesktopEngine {
public:
    static constexpr int kMaxVoices = 48;
    static constexpr int64_t kHeadSeconds10 = 15;

    DesktopEngine();
    ~DesktopEngine();

    std::string load(const std::vector<std::string>& paths, const PianoTables& tables);
    void unload();
    /** periodFrames: 128, 256 or 512. nullBackend: no sound card (tests). Empty string on success. */
    std::string start(int periodFrames, bool preferExclusive, bool nullBackend);
    void stop();
    bool running() const { return running_.load(); }

    void noteOn(int note, int velocity);
    void noteOff(int note);
    void sustain(bool down);
    void allNotesOff();
    void setMasterGain(float gain) { masterGain_.store(gain); }
    void setTone(float reverb, float warmth) {
        reverb_.store(reverb);
        warmth_.store(warmth);
        toneVersion_.fetch_add(1);
    }

    void setMetronome(bool on, double bpm, int ticksPerBeat, const std::vector<int>& levels, float volume);
    int64_t metronomeTickCount() const { return metroTickCount_.load(); }
    std::vector<int64_t> drainTicks();
    /** Frame position and the nanoTime when that frame is heard (callback time + the device's buffering). */
    bool timestamp(int64_t& framePosition, int64_t& timeNanos);
    EngineMetrics metrics();

    /** Renders one block (the device callback; also called directly by tests). */
    void render(float* out, uint32_t frames, int channels);
    void onDeviceStopped();

private:
    enum class CmdType : uint8_t { NoteOn, NoteOff, SustainOn, SustainOff, AllOff };
    struct Cmd {
        CmdType type;
        uint8_t note;
        uint8_t velocity;
    };
    struct Voice {
        bool active = false;
        bool keyDown = false;
        bool sustained = false;
        bool releasing = false;
        bool synth = false;
        int note = -1;
        int sample = -1;
        double pos = 0;
        double inc = 1;
        float gain = 1;
        float env = 1;
        float decayCoef = 1;
        float releaseCoef = 1;
        float bright = 1;
        float brightCoef = 1;
        int age = 0;
        uint64_t order = 0;
    };

    void push(const Cmd& cmd);
    void applyCommands();
    void startNote(int note, int velocity);
    void startSynthNote(Voice& v, int note, int velocity);
    Voice& allocateVoice(int note);
    void releaseVoice(Voice& v, float seconds);
    float releaseFor(const Voice& v) const;
    float coefFor(float seconds) const;
    void prepareForRate(int rate);
    void prefetchLoop();

    static constexpr size_t kQueueSize = 1024;
    std::array<Cmd, kQueueSize> queue_{};
    std::atomic<size_t> head_{0}, tail_{0};
    std::mutex producerMutex_;

    std::mutex deviceMutex_;
    ma_device* device_ = nullptr;
    std::atomic<bool> running_{false};
    std::atomic<int> restarts_{0};
    std::atomic<int> stoppedByDevice_{0};
    int periodFrames_ = 0, periods_ = 0, exclusive_ = 0;

    SampleStore store_;
    PianoTables tables_;
    std::atomic<bool> loaded_{false};

    std::array<Voice, kMaxVoices> voices_{};
    uint64_t nextOrder_ = 1;
    bool pedalDown_ = false;
    int outRate_ = 48000;
    std::atomic<float> masterGain_{0.35f};
    std::atomic<float> reverb_{0.3f}, warmth_{0.5f};
    std::atomic<int> toneVersion_{1};
    int appliedToneVersion_ = 0;
    std::array<Biquad, 2> highPass_{}, body_{}, shelf_{};
    Freeverb reverbFx_;
    Compressor compressor_;
    // Built-in tone: one cycle of a bright and a dull piano-like spectrum, crossfaded as the note decays.
    static constexpr int kTable = 2048;
    std::array<float, kTable + 1> brightTable_{}, dullTable_{};

    std::array<std::atomic<int>, kMaxVoices> pubSample_{};
    std::array<std::atomic<int64_t>, kMaxVoices> pubPos_{};
    std::atomic<int> activeVoices_{0}, maxActive_{0};
    std::atomic<int64_t> callbacks_{0};
    std::atomic<double> maxCallbackMicros_{0};

    // Timestamp published by the callback: a seqlock over (frame, nanos).
    std::atomic<uint32_t> stampSeq_{0};
    std::atomic<int64_t> stampFrame_{0}, stampNanos_{0};
    std::atomic<int64_t> latencyNanos_{0};

    static constexpr int kMaxPattern = 32;
    std::atomic<bool> metroOn_{false};
    std::atomic<double> metroBpm_{80};
    std::atomic<int> metroTicksPerBeat_{1};
    std::array<std::atomic<uint8_t>, kMaxPattern> metroPattern_{};
    std::atomic<int> metroLen_{4};
    std::atomic<float> metroVolume_{0.7f};
    std::atomic<int64_t> metroTickCount_{0};
    bool metroWasOn_ = false;
    double metroCountdown_ = 0;
    int64_t metroTick_ = 0;
    int64_t framesWritten_ = 0;
    struct Click { int level = 0; size_t pos = 0; bool active = false; };
    std::array<Click, 2> clicks_{};
    std::array<std::vector<float>, 4> clickTables_;
    static constexpr size_t kTickRing = 1024;
    std::array<std::atomic<int64_t>, kTickRing> tickRing_{};
    std::atomic<uint64_t> tickWrite_{0};
    uint64_t tickRead_ = 0;
    std::mutex tickMutex_;
    void prepareClicks(int rate);

    std::thread prefetcher_;
    std::atomic<bool> prefetchRunning_{false};
};

}  // namespace mimikopi
