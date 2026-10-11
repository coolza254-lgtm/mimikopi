#include "piano_engine.h"

#include <algorithm>
#include <android/log.h>
#include <chrono>
#include <cmath>

#define LOG_TAG "MimikopiAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace mimikopi {

namespace {
constexpr float kRetriggerRelease = 0.03f;  // same key struck again: fade the old voice quickly
constexpr float kStealRelease = 0.01f;
constexpr float kSilent = 1e-4f;  // -80 dB: voice is finished

inline float hermite(float xm1, float x0, float x1, float x2, float t) {
    float c1 = 0.5f * (x1 - xm1);
    float c2 = xm1 - 2.5f * x0 + 2.0f * x1 - 0.5f * x2;
    float c3 = 0.5f * (x2 - xm1) + 1.5f * (x0 - x1);
    return ((c3 * t + c2) * t + c1) * t + x0;
}
}  // namespace

PianoEngine::PianoEngine() {
    for (auto& s : pubSample_) s.store(-1);
    for (auto& p : pubPos_) p.store(0);
}

PianoEngine::~PianoEngine() { stop(); }

std::string PianoEngine::load(const std::vector<std::string>& paths, const PianoTables& tables) {
    if (wantRunning_.load()) return "engine must be stopped to load samples";
    loaded_.store(false);
    std::string error;
    int64_t head = tables.packSampleRate * kHeadSeconds10 / 10;
    if (!store_.load(paths, head, error)) return error;
    if (tables.layerLoVel.size() != paths.size() || tables.layerHiVel.size() != paths.size()) return "table size mismatch";
    tables_ = tables;
    for (auto& v : voices_) v = Voice{};
    loaded_.store(true);
    LOGI("loaded %zu samples, %zu MB resident heads", paths.size(), store_.residentHeadBytes() >> 20);
    return {};
}

std::string PianoEngine::openStreamLocked() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setUsage(oboe::Usage::Game)
        ->setContentType(oboe::ContentType::Music)
        ->setFormatConversionAllowed(true)
        ->setChannelConversionAllowed(true)
        ->setDataCallback(this)
        ->setErrorCallback(this);
    std::shared_ptr<oboe::AudioStream> stream;
    oboe::Result r = builder.openStream(stream);
    if (r != oboe::Result::OK) return std::string("openStream: ") + oboe::convertToText(r);
    outRate_ = stream->getSampleRate();
    // Two bursts of buffering: lowest latency that usually survives UI load.
    stream->setBufferSizeInFrames(stream->getFramesPerBurst() * 2);
    r = stream->requestStart();
    if (r != oboe::Result::OK) {
        stream->close();
        return std::string("requestStart: ") + oboe::convertToText(r);
    }
    stream_ = stream;
    LOGI("stream open: %d Hz, burst %d, exclusive %d", outRate_, stream->getFramesPerBurst(),
         stream->getSharingMode() == oboe::SharingMode::Exclusive);
    return {};
}

std::string PianoEngine::start() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    if (stream_) return {};
    wantRunning_.store(true);
    std::string err = openStreamLocked();
    if (!err.empty()) {
        wantRunning_.store(false);
        return err;
    }
    if (!prefetchRunning_.exchange(true)) prefetcher_ = std::thread(&PianoEngine::prefetchLoop, this);
    return {};
}

void PianoEngine::stop() {
    std::shared_ptr<oboe::AudioStream> stream;
    {
        std::lock_guard<std::mutex> lock(streamMutex_);
        wantRunning_.store(false);
        stream = std::move(stream_);
    }
    // Close outside the lock: Oboe's close() can wait for a pending error callback, which takes the lock.
    if (stream) {
        stream->stop();
        stream->close();
    }
    if (prefetchRunning_.exchange(false) && prefetcher_.joinable()) prefetcher_.join();
    for (auto& v : voices_) v = Voice{};
    pedalDown_ = false;
    head_.store(tail_.load());
    activeVoices_.store(0);
}

void PianoEngine::onErrorAfterClose(oboe::AudioStream*, oboe::Result error) {
    // Typically the output route changed (Bluetooth connected/disconnected) or another app took the
    // exclusive stream: reopen on the current device, retrying briefly if the device is still switching.
    LOGE("stream closed: %s", oboe::convertToText(error));
    for (int attempt = 0; attempt < 10; ++attempt) {
        {
            std::lock_guard<std::mutex> lock(streamMutex_);
            stream_.reset();
            if (!wantRunning_.load()) return;
            std::string err = openStreamLocked();
            if (err.empty()) {
                restarts_.fetch_add(1);
                return;
            }
            LOGE("reopen attempt %d failed: %s", attempt + 1, err.c_str());
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(300));
    }
}

void PianoEngine::push(const Cmd& cmd) {
    std::lock_guard<std::mutex> lock(producerMutex_);
    size_t h = head_.load(std::memory_order_relaxed);
    size_t next = (h + 1) % kQueueSize;
    if (next == tail_.load(std::memory_order_acquire)) return;  // full: drop (never block)
    queue_[h] = cmd;
    head_.store(next, std::memory_order_release);
}

void PianoEngine::noteOn(int note, int velocity) {
    if (note < 0 || note > 127) return;
    if (velocity <= 0) {  // MIDI convention: velocity 0 is note-off
        noteOff(note);
        return;
    }
    push({CmdType::NoteOn, static_cast<uint8_t>(note), static_cast<uint8_t>(std::min(velocity, 127))});
}

void PianoEngine::noteOff(int note) {
    if (note < 0 || note > 127) return;
    push({CmdType::NoteOff, static_cast<uint8_t>(note), 0});
}

void PianoEngine::sustain(bool down) { push({down ? CmdType::SustainOn : CmdType::SustainOff, 0, 0}); }

void PianoEngine::allNotesOff() { push({CmdType::AllOff, 0, 0}); }

float PianoEngine::coefFor(float seconds) const {
    // Exponential decay reaching -60 dB after `seconds`.
    return std::exp(std::log(0.001f) / (std::max(seconds, 0.005f) * static_cast<float>(outRate_)));
}

void PianoEngine::releaseVoice(Voice& v, float seconds) {
    v.releasing = true;
    v.keyDown = false;
    v.sustained = false;
    v.releaseCoef = coefFor(seconds);
}

void PianoEngine::startNote(int note, int velocity) {
    int region = tables_.noteToRegion[static_cast<size_t>(note)];
    if (region < 0) return;
    int start = tables_.regionLayerStart[region];
    int count = tables_.regionLayerCount[region];
    int sample = start;
    for (int i = start; i < start + count; ++i) {
        if (velocity >= tables_.layerLoVel[i] && velocity <= tables_.layerHiVel[i]) {
            sample = i;
            break;
        }
    }
    for (auto& v : voices_) {
        if (v.active && v.note == note && !v.releasing) releaseVoice(v, kRetriggerRelease);
    }
    Voice* slot = nullptr;
    for (auto& v : voices_) {
        if (!v.active) {
            slot = &v;
            break;
        }
    }
    if (slot == nullptr) {
        // Steal the oldest releasing voice, else the oldest voice.
        for (auto& v : voices_)
            if (v.releasing && (slot == nullptr || v.order < slot->order)) slot = &v;
        if (slot == nullptr)
            for (auto& v : voices_)
                if (slot == nullptr || v.order < slot->order) slot = &v;
    }
    int lo = tables_.layerLoVel[sample], hi = tables_.layerHiVel[sample];
    float within = hi > lo ? static_cast<float>(velocity - lo) / static_cast<float>(hi - lo) : 1.0f;
    Voice& v = *slot;
    v = Voice{};
    v.active = true;
    v.keyDown = true;
    v.note = note;
    v.sample = sample;
    v.pos = 0;
    v.inc = std::pow(2.0, (note - tables_.regionKeyCenter[region]) / 12.0) * tables_.packSampleRate / outRate_;
    v.gain = 0.7f + 0.3f * within;  // the layer carries the timbre; this smooths steps between layers
    v.env = 1;
    v.order = nextOrder_++;
}

void PianoEngine::applyCommands() {
    size_t t = tail_.load(std::memory_order_relaxed);
    size_t h = head_.load(std::memory_order_acquire);
    while (t != h) {
        const Cmd c = queue_[t];
        t = (t + 1) % kQueueSize;
        switch (c.type) {
            case CmdType::NoteOn:
                startNote(c.note, c.velocity);
                break;
            case CmdType::NoteOff:
                for (auto& v : voices_) {
                    if (v.active && v.note == c.note && v.keyDown) {
                        if (pedalDown_) {
                            v.keyDown = false;
                            v.sustained = true;
                        } else {
                            releaseVoice(v, tables_.regionReleaseSec[tables_.noteToRegion[c.note]]);
                        }
                    }
                }
                break;
            case CmdType::SustainOn:
                pedalDown_ = true;
                break;
            case CmdType::SustainOff:
                pedalDown_ = false;
                for (auto& v : voices_)
                    if (v.active && v.sustained) releaseVoice(v, tables_.regionReleaseSec[tables_.noteToRegion[v.note]]);
                break;
            case CmdType::AllOff:
                pedalDown_ = false;
                for (auto& v : voices_)
                    if (v.active) releaseVoice(v, 0.05f);
                break;
        }
    }
    tail_.store(t, std::memory_order_release);
}

oboe::DataCallbackResult PianoEngine::onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) {
    auto t0 = std::chrono::steady_clock::now();
    auto* out = static_cast<float*>(audioData);
    const int channels = stream->getChannelCount();
    std::fill(out, out + numFrames * channels, 0.0f);
    if (!loaded_.load(std::memory_order_acquire)) return oboe::DataCallbackResult::Continue;

    applyCommands();
    constexpr float kScale = 1.0f / 32768.0f;
    int active = 0;
    for (size_t vi = 0; vi < voices_.size(); ++vi) {
        Voice& v = voices_[vi];
        if (!v.active) {
            pubSample_[vi].store(-1, std::memory_order_relaxed);
            continue;
        }
        const Sample& s = store_[static_cast<size_t>(v.sample)];
        const int64_t last = s.frames - 1;
        for (int32_t f = 0; f < numFrames; ++f) {
            int64_t i = static_cast<int64_t>(v.pos);
            if (i + 2 > last) {
                v.active = false;
                break;
            }
            float t = static_cast<float>(v.pos - static_cast<double>(i));
            const int16_t* a = s.frameAt(i > 0 ? i - 1 : 0);
            const int16_t* b = s.frameAt(i);
            const int16_t* c = s.frameAt(i + 1);
            const int16_t* d = s.frameAt(i + 2);
            float g = v.gain * v.env * kScale;
            float l = hermite(a[0], b[0], c[0], d[0], t) * g;
            float r = hermite(a[1], b[1], c[1], d[1], t) * g;
            if (channels >= 2) {
                out[f * channels] += l;
                out[f * channels + 1] += r;
            } else {
                out[f] += 0.5f * (l + r);
            }
            v.pos += v.inc;
            if (v.releasing) {
                v.env *= v.releaseCoef;
                if (v.env < kSilent) {
                    v.active = false;
                    break;
                }
            }
        }
        if (v.active) {
            ++active;
            pubSample_[vi].store(v.sample, std::memory_order_relaxed);
            pubPos_[vi].store(static_cast<int64_t>(v.pos), std::memory_order_relaxed);
        } else {
            pubSample_[vi].store(-1, std::memory_order_relaxed);
        }
    }

    const float master = masterGain_.load(std::memory_order_relaxed);
    for (int32_t k = 0; k < numFrames * channels; ++k) {
        float x = out[k] * master;
        // Soft limiter above -1 dBFS so dense chords never hard-clip.
        float ax = std::fabs(x);
        if (ax > 0.89f) x = std::copysign(0.89f + 0.11f * std::tanh((ax - 0.89f) / 0.11f), x);
        out[k] = x;
    }

    activeVoices_.store(active, std::memory_order_relaxed);
    if (active > maxActive_.load(std::memory_order_relaxed)) maxActive_.store(active, std::memory_order_relaxed);
    callbacks_.fetch_add(1, std::memory_order_relaxed);
    double us = std::chrono::duration<double, std::micro>(std::chrono::steady_clock::now() - t0).count();
    if (us > maxCallbackMicros_.load(std::memory_order_relaxed)) maxCallbackMicros_.store(us, std::memory_order_relaxed);
    return oboe::DataCallbackResult::Continue;
}

void PianoEngine::prefetchLoop() {
    while (prefetchRunning_.load()) {
        if (loaded_.load()) {
            const int64_t ahead = static_cast<int64_t>(tables_.packSampleRate) * 2;
            for (size_t i = 0; i < voices_.size(); ++i) {
                int s = pubSample_[i].load(std::memory_order_relaxed);
                if (s >= 0) store_.prefetch(static_cast<size_t>(s), pubPos_[i].load(std::memory_order_relaxed), ahead);
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(30));
    }
}

EngineMetrics PianoEngine::metrics() {
    EngineMetrics m;
    std::lock_guard<std::mutex> lock(streamMutex_);
    if (stream_) {
        m.sampleRate = stream_->getSampleRate();
        m.framesPerBurst = stream_->getFramesPerBurst();
        m.bufferSizeFrames = stream_->getBufferSizeInFrames();
        auto latency = stream_->calculateLatencyMillis();
        if (latency) m.estimatedLatencyMs = latency.value();
        m.sharingExclusive = stream_->getSharingMode() == oboe::SharingMode::Exclusive;
        m.lowLatency = stream_->getPerformanceMode() == oboe::PerformanceMode::LowLatency;
        auto xr = stream_->getXRunCount();
        if (xr) m.xRuns = xr.value();
        m.deviceId = stream_->getDeviceId();
    }
    m.activeVoices = activeVoices_.load();
    m.maxActiveVoices = maxActive_.load();
    m.maxCallbackMicros = maxCallbackMicros_.load();
    m.callbacks = callbacks_.load();
    m.restarts = restarts_.load();
    m.residentBytes = static_cast<int64_t>(store_.residentHeadBytes());
    return m;
}

}  // namespace mimikopi
