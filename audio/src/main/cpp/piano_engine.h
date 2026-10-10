#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <oboe/Oboe.h>

#include "sample_store.h"

namespace mimikopi {

/** Lookup tables built in Kotlin by core's SampleMap and handed over at load time. */
struct PianoTables {
    int packSampleRate = 48000;
    std::array<int, 128> noteToRegion{};
    std::vector<int> regionLayerStart, regionLayerCount, regionKeyCenter;
    std::vector<float> regionReleaseSec;
    std::vector<int> layerLoVel, layerHiVel;  // indexed like the sample files
};

struct EngineMetrics {
    int sampleRate = 0;
    int framesPerBurst = 0;
    int bufferSizeFrames = 0;
    double estimatedLatencyMs = -1;
    int sharingExclusive = 0;
    int lowLatency = 0;
    int xRuns = -1;
    int activeVoices = 0;
    int maxActiveVoices = 0;
    double maxCallbackMicros = 0;
    int64_t callbacks = 0;
    int restarts = 0;
    int deviceId = 0;
    int64_t residentBytes = 0;
};

/**
 * Sampled piano driven entirely by the Oboe audio callback: note commands are queued by any thread
 * and applied at the start of the next callback; nothing in the callback allocates, locks or does I/O.
 */
class PianoEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    static constexpr int kMaxVoices = 48;
    static constexpr int64_t kHeadSeconds10 = 15;  // 1.5 s of every sample stays in RAM

    PianoEngine();
    ~PianoEngine() override;

    /** Must be called while stopped. Returns an empty string on success. */
    std::string load(const std::vector<std::string>& paths, const PianoTables& tables);
    std::string start();
    void stop();

    void noteOn(int note, int velocity);
    void noteOff(int note);
    void sustain(bool down);
    void allNotesOff();
    void setMasterGain(float gain) { masterGain_.store(gain); }

    EngineMetrics metrics();

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

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
        int note = -1;
        int sample = -1;
        double pos = 0;
        double inc = 1;
        float gain = 1;
        float env = 1;
        float releaseCoef = 1;
        uint64_t order = 0;
    };

    std::string openStreamLocked();
    void push(const Cmd& cmd);
    void applyCommands();
    void startNote(int note, int velocity);
    void releaseVoice(Voice& v, float seconds);
    float coefFor(float seconds) const;
    void prefetchLoop();

    // Command queue: many producers (guarded by producerMutex_), one consumer (audio thread, lock-free).
    static constexpr size_t kQueueSize = 1024;
    std::array<Cmd, kQueueSize> queue_{};
    std::atomic<size_t> head_{0}, tail_{0};
    std::mutex producerMutex_;

    std::mutex streamMutex_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::atomic<bool> wantRunning_{false};
    std::atomic<int> restarts_{0};

    SampleStore store_;
    PianoTables tables_;
    std::atomic<bool> loaded_{false};

    // Audio-thread state.
    std::array<Voice, kMaxVoices> voices_{};
    uint64_t nextOrder_ = 1;
    bool pedalDown_ = false;
    int outRate_ = 48000;
    std::atomic<float> masterGain_{0.35f};

    // Published by the audio thread for the prefetcher and metrics.
    std::array<std::atomic<int>, kMaxVoices> pubSample_{};
    std::array<std::atomic<int64_t>, kMaxVoices> pubPos_{};
    std::atomic<int> activeVoices_{0}, maxActive_{0};
    std::atomic<int64_t> callbacks_{0};
    std::atomic<double> maxCallbackMicros_{0};

    std::thread prefetcher_;
    std::atomic<bool> prefetchRunning_{false};
};

}  // namespace mimikopi
