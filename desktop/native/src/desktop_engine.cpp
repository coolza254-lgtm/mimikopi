#include "desktop_engine.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>

#include "clock.h"
#include "miniaudio.h"

namespace mimikopi {

namespace {
constexpr float kRetriggerRelease = 0.03f;
constexpr float kSilent = 1e-4f;  // -80 dB: voice is finished
constexpr float kSynthRelease = 0.35f;
constexpr int kSynthAttackFrames = 96;

inline float hermite(float xm1, float x0, float x1, float x2, float t) {
    float c1 = 0.5f * (x1 - xm1);
    float c2 = xm1 - 2.5f * x0 + 2.0f * x1 - 0.5f * x2;
    float c3 = 0.5f * (x2 - xm1) + 1.5f * (x0 - x1);
    return ((c3 * t + c2) * t + c1) * t + x0;
}

struct DeviceHolder {
    ma_context context;
    ma_device device;
};

void dataCallback(ma_device* device, void* output, const void*, ma_uint32 frames) {
    auto* engine = static_cast<DesktopEngine*>(device->pUserData);
    engine->render(static_cast<float*>(output), frames, static_cast<int>(device->playback.channels));
}

void notificationCallback(const ma_device_notification* n) {
    if (n->type == ma_device_notification_type_stopped) {
        static_cast<DesktopEngine*>(n->pDevice->pUserData)->onDeviceStopped();
    }
}
}  // namespace

DesktopEngine::DesktopEngine() {
    for (auto& s : pubSample_) s.store(-1);
    for (auto& p : pubPos_) p.store(0);
    // Piano-like spectra: the bright one for the attack, the dull one for the tail.
    static constexpr float kBright[] = {1.0f, 0.55f, 0.36f, 0.24f, 0.16f, 0.11f, 0.07f, 0.045f};
    static constexpr float kDull[] = {1.0f, 0.22f, 0.07f, 0.025f};
    auto fill = [](std::array<float, kTable + 1>& t, const float* amps, int n) {
        float peak = 0;
        for (int i = 0; i < kTable; ++i) {
            double x = 2.0 * M_PI * i / kTable;
            double v = 0;
            for (int h = 0; h < n; ++h) v += amps[h] * std::sin((h + 1) * x);
            t[i] = static_cast<float>(v);
            peak = std::max(peak, std::fabs(t[i]));
        }
        for (int i = 0; i < kTable; ++i) t[i] /= peak;
        t[kTable] = t[0];
    };
    fill(brightTable_, kBright, 8);
    fill(dullTable_, kDull, 4);
    prepareForRate(outRate_);
}

DesktopEngine::~DesktopEngine() {
    stop();
    unload();
}

std::string DesktopEngine::load(const std::vector<std::string>& paths, const PianoTables& tables) {
    if (running_.load()) return "engine must be stopped to load samples";
    loaded_.store(false);
    if (tables.layerLoVel.size() != paths.size() || tables.layerHiVel.size() != paths.size()) return "table size mismatch";
    std::string error;
    int64_t head = tables.packSampleRate * kHeadSeconds10 / 10;
    if (!store_.load(paths, head, error)) return error;
    tables_ = tables;
    for (auto& v : voices_) v = Voice{};
    loaded_.store(true);
    return {};
}

void DesktopEngine::unload() {
    if (running_.load()) return;
    loaded_.store(false);
    store_.clear();
}

void DesktopEngine::prepareForRate(int rate) {
    outRate_ = rate;
    reverbFx_.prepare(rate);
    compressor_.prepare(rate);
    prepareClicks(rate);
    appliedToneVersion_ = 0;
}

std::string DesktopEngine::start(int periodFrames, bool preferExclusive, bool nullBackend) {
    std::lock_guard<std::mutex> lock(deviceMutex_);
    if (device_ != nullptr) return {};
    auto* holder = new DeviceHolder();
    ma_backend backends[1];
    ma_uint32 backendCount = 0;
    if (nullBackend) {
        backends[backendCount++] = ma_backend_null;
    } else {
#ifdef _WIN32
        backends[backendCount++] = ma_backend_wasapi;
#endif
    }
    if (ma_context_init(backendCount > 0 ? backends : nullptr, backendCount, nullptr, &holder->context) != MA_SUCCESS) {
        delete holder;
        return "ไม่พบระบบเสียงของเครื่อง";
    }
    std::string lastError;
    // Exclusive first (lowest latency, nothing else mixed in), shared as the fallback.
    for (int attempt = preferExclusive ? 0 : 1; attempt < 2; ++attempt) {
        ma_device_config cfg = ma_device_config_init(ma_device_type_playback);
        cfg.playback.format = ma_format_f32;
        cfg.playback.channels = 2;
        cfg.playback.shareMode = attempt == 0 ? ma_share_mode_exclusive : ma_share_mode_shared;
        cfg.sampleRate = 0;  // the device's own rate
        cfg.periodSizeInFrames = static_cast<ma_uint32>(std::clamp(periodFrames, 64, 2048));
        cfg.periods = 2;
        cfg.performanceProfile = ma_performance_profile_low_latency;
        cfg.noClip = MA_TRUE;
        cfg.dataCallback = dataCallback;
        cfg.notificationCallback = notificationCallback;
        cfg.pUserData = this;
        cfg.wasapi.noAutoStreamRouting = attempt == 0 ? MA_TRUE : MA_FALSE;
        if (ma_device_init(&holder->context, &cfg, &holder->device) != MA_SUCCESS) {
            lastError = attempt == 0 ? "exclusive mode refused" : "เปิดอุปกรณ์เสียงไม่ได้";
            continue;
        }
        const int rate = static_cast<int>(holder->device.sampleRate);
        prepareForRate(rate);
        framesWritten_ = 0;
        metroWasOn_ = false;
        periodFrames_ = static_cast<int>(holder->device.playback.internalPeriodSizeInFrames);
        periods_ = static_cast<int>(holder->device.playback.internalPeriods);
        exclusive_ = attempt == 0 ? 1 : 0;
        const double latencyMs = rate > 0 ? 1000.0 * periodFrames_ * std::max(periods_, 1) / rate : 0.0;
        latencyNanos_.store(static_cast<int64_t>(latencyMs * 1e6));
        device_ = &holder->device;
        running_.store(true);
        if (ma_device_start(&holder->device) != MA_SUCCESS) {
            running_.store(false);
            device_ = nullptr;
            ma_device_uninit(&holder->device);
            lastError = "เริ่มเสียงไม่ได้";
            continue;
        }
        if (!prefetchRunning_.exchange(true)) prefetcher_ = std::thread(&DesktopEngine::prefetchLoop, this);
        return {};
    }
    ma_context_uninit(&holder->context);
    delete holder;
    return lastError;
}

void DesktopEngine::stop() {
    ma_device* device = nullptr;
    {
        std::lock_guard<std::mutex> lock(deviceMutex_);
        device = device_;
        device_ = nullptr;
        running_.store(false);
    }
    if (device != nullptr) {
        auto* holder = reinterpret_cast<DeviceHolder*>(reinterpret_cast<char*>(device) - offsetof(DeviceHolder, device));
        ma_device_uninit(device);
        ma_context_uninit(&holder->context);
        delete holder;
    }
    if (prefetchRunning_.exchange(false) && prefetcher_.joinable()) prefetcher_.join();
    for (auto& v : voices_) v = Voice{};
    pedalDown_ = false;
    head_.store(tail_.load());
    activeVoices_.store(0);
}

void DesktopEngine::onDeviceStopped() {
    // Called on miniaudio's thread. A stop we asked for clears running_ first; anything else (the device was
    // unplugged, another program took it) is reported so the app can reopen the output.
    if (running_.load()) {
        stoppedByDevice_.fetch_add(1);
        running_.store(false);
    }
}

void DesktopEngine::push(const Cmd& cmd) {
    std::lock_guard<std::mutex> lock(producerMutex_);
    size_t h = head_.load(std::memory_order_relaxed);
    size_t next = (h + 1) % kQueueSize;
    if (next == tail_.load(std::memory_order_acquire)) return;
    queue_[h] = cmd;
    head_.store(next, std::memory_order_release);
}

void DesktopEngine::noteOn(int note, int velocity) {
    if (note < 0 || note > 127) return;
    if (velocity <= 0) {
        noteOff(note);
        return;
    }
    push({CmdType::NoteOn, static_cast<uint8_t>(note), static_cast<uint8_t>(std::min(velocity, 127))});
}

void DesktopEngine::noteOff(int note) {
    if (note < 0 || note > 127) return;
    push({CmdType::NoteOff, static_cast<uint8_t>(note), 0});
}

void DesktopEngine::sustain(bool down) { push({down ? CmdType::SustainOn : CmdType::SustainOff, 0, 0}); }

void DesktopEngine::allNotesOff() { push({CmdType::AllOff, 0, 0}); }

float DesktopEngine::coefFor(float seconds) const {
    return std::exp(std::log(0.001f) / (std::max(seconds, 0.005f) * static_cast<float>(outRate_)));
}

void DesktopEngine::releaseVoice(Voice& v, float seconds) {
    v.releasing = true;
    v.keyDown = false;
    v.sustained = false;
    v.releaseCoef = coefFor(seconds);
}

float DesktopEngine::releaseFor(const Voice& v) const {
    if (v.synth) return kSynthRelease;
    return tables_.regionReleaseSec[static_cast<size_t>(tables_.noteToRegion[static_cast<size_t>(v.note)])];
}

DesktopEngine::Voice& DesktopEngine::allocateVoice(int note) {
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
        for (auto& v : voices_)
            if (v.releasing && (slot == nullptr || v.order < slot->order)) slot = &v;
        if (slot == nullptr)
            for (auto& v : voices_)
                if (slot == nullptr || v.order < slot->order) slot = &v;
    }
    return *slot;
}

void DesktopEngine::startSynthNote(Voice& v, int note, int velocity) {
    const double freq = 440.0 * std::pow(2.0, (note - 69) / 12.0);
    v = Voice{};
    v.active = true;
    v.keyDown = true;
    v.synth = true;
    v.note = note;
    v.inc = freq * kTable / outRate_;
    v.gain = 0.42f * std::pow(static_cast<float>(velocity) / 127.0f, 1.4f);
    // Low notes ring longer, like strings; the brightness fades faster than the loudness.
    const float decay = std::clamp(9.0f - 0.085f * static_cast<float>(note - 21), 1.4f, 9.0f);
    v.decayCoef = coefFor(decay);
    v.brightCoef = coefFor(decay * 0.18f);
    v.bright = note > 88 ? 0.2f : 1.0f;
    v.env = 1;
    v.order = nextOrder_++;
}

void DesktopEngine::startNote(int note, int velocity) {
    const bool loaded = loaded_.load(std::memory_order_relaxed);
    if (!loaded) {
        startSynthNote(allocateVoice(note), note, velocity);
        return;
    }
    int region = tables_.noteToRegion[static_cast<size_t>(note)];
    if (region < 0) return;
    int start = tables_.regionLayerStart[static_cast<size_t>(region)];
    int count = tables_.regionLayerCount[static_cast<size_t>(region)];
    int sample = start;
    for (int i = start; i < start + count; ++i) {
        if (velocity >= tables_.layerLoVel[static_cast<size_t>(i)] && velocity <= tables_.layerHiVel[static_cast<size_t>(i)]) {
            sample = i;
            break;
        }
    }
    Voice& v = allocateVoice(note);
    int lo = tables_.layerLoVel[static_cast<size_t>(sample)], hi = tables_.layerHiVel[static_cast<size_t>(sample)];
    v = Voice{};
    v.active = true;
    v.keyDown = true;
    v.note = note;
    v.sample = sample;
    v.pos = 0;
    v.inc = std::pow(2.0, (note - tables_.regionKeyCenter[static_cast<size_t>(region)]) / 12.0) * tables_.packSampleRate / outRate_;
    float center = 0.5f * static_cast<float>(lo + hi);
    v.gain = std::pow(static_cast<float>(velocity) / center, 0.8f);
    v.env = 1;
    v.order = nextOrder_++;
}

void DesktopEngine::applyCommands() {
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
                            releaseVoice(v, releaseFor(v));
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
                    if (v.active && v.sustained) releaseVoice(v, releaseFor(v));
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

void DesktopEngine::render(float* out, uint32_t numFrames, int channels) {
    const int64_t callbackNanos = nowNanos();
    auto t0 = std::chrono::steady_clock::now();
    // Publish when this block's first frame will be heard.
    stampSeq_.fetch_add(1, std::memory_order_acq_rel);
    stampFrame_.store(framesWritten_, std::memory_order_relaxed);
    stampNanos_.store(callbackNanos + latencyNanos_.load(std::memory_order_relaxed), std::memory_order_relaxed);
    stampSeq_.fetch_add(1, std::memory_order_acq_rel);

    std::fill(out, out + static_cast<size_t>(numFrames) * static_cast<size_t>(channels), 0.0f);
    const bool loaded = loaded_.load(std::memory_order_acquire);
    applyCommands();
    constexpr float kScale = 1.0f / 32768.0f;
    int active = 0;
    for (size_t vi = 0; vi < voices_.size(); ++vi) {
        Voice& v = voices_[vi];
        if (!v.active) {
            pubSample_[vi].store(-1, std::memory_order_relaxed);
            continue;
        }
        if (v.synth) {
            for (uint32_t f = 0; f < numFrames; ++f) {
                const int i = static_cast<int>(v.pos);
                const float t = static_cast<float>(v.pos - i);
                const float b = brightTable_[i] + (brightTable_[i + 1] - brightTable_[i]) * t;
                const float d = dullTable_[i] + (dullTable_[i + 1] - dullTable_[i]) * t;
                // A few frames of fade-in so the tone starts without a click.
                const float attack = v.age < kSynthAttackFrames ? static_cast<float>(++v.age) / kSynthAttackFrames : 1.0f;
                const float s = (d + (b - d) * v.bright) * v.gain * v.env * attack;
                if (channels >= 2) {
                    out[f * channels] += s;
                    out[f * channels + 1] += s;
                } else {
                    out[f] += s;
                }
                v.pos += v.inc;
                if (v.pos >= kTable) v.pos -= kTable;
                v.bright *= v.brightCoef;
                v.env *= v.releasing ? v.releaseCoef : v.decayCoef;
                if (v.env < kSilent) {
                    v.active = false;
                    break;
                }
            }
            if (v.active) ++active;
            pubSample_[vi].store(-1, std::memory_order_relaxed);
            continue;
        }
        if (!loaded) {
            v.active = false;
            pubSample_[vi].store(-1, std::memory_order_relaxed);
            continue;
        }
        const Sample& s = store_[static_cast<size_t>(v.sample)];
        const int64_t last = s.frames - 1;
        for (uint32_t f = 0; f < numFrames; ++f) {
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

    if (appliedToneVersion_ != toneVersion_.load(std::memory_order_relaxed)) {
        appliedToneVersion_ = toneVersion_.load(std::memory_order_relaxed);
        const float w = warmth_.load(std::memory_order_relaxed);
        const float rate = static_cast<float>(outRate_);
        reverbFx_.setRoom(0.62f, 0.45f + 0.35f * w);
        for (int ch = 0; ch < 2; ++ch) {
            highPass_[ch].setHighPass(rate, 40.0f, 0.707f);
            body_[ch].setPeaking(rate, 320.0f, 0.8f, 2.0f * w);
            shelf_[ch].setHighShelf(rate, 2800.0f, -6.0f * w);
        }
    }
    const float master = masterGain_.load(std::memory_order_relaxed);
    const float wet = reverb_.load(std::memory_order_relaxed) * 0.35f;
    const bool metroOn = metroOn_.load(std::memory_order_relaxed);
    if (metroOn && !metroWasOn_) {
        metroCountdown_ = 0;
        metroTick_ = 0;
        metroTickCount_.store(0, std::memory_order_relaxed);
    }
    metroWasOn_ = metroOn;
    const double framesPerTick = static_cast<double>(outRate_) * 60.0 /
        std::max(1.0, metroBpm_.load(std::memory_order_relaxed)) / std::max(1, metroTicksPerBeat_.load(std::memory_order_relaxed));
    const int patternLen = std::max(1, std::min(kMaxPattern, metroLen_.load(std::memory_order_relaxed)));
    const float metroVol = metroVolume_.load(std::memory_order_relaxed);
    for (uint32_t f = 0; f < numFrames; ++f) {
        float l = out[f * channels];
        float r = channels >= 2 ? out[f * channels + 1] : l;
        l = shelf_[0].process(body_[0].process(highPass_[0].process(l)));
        r = shelf_[1].process(body_[1].process(highPass_[1].process(r)));
        reverbFx_.process(l, r, wet);
        l *= master;
        r *= master;
        const float g = compressor_.gainFor(l, r);
        l *= g;
        r *= g;
        if (metroOn) {
            if (metroCountdown_ <= 0.0) {
                int level = metroPattern_[static_cast<size_t>(metroTick_ % patternLen)].load(std::memory_order_relaxed);
                Click& slot = clicks_[clicks_[0].active ? 1 : 0];
                slot = Click{std::min(level, 3), 0, true};
                uint64_t w = tickWrite_.load(std::memory_order_relaxed);
                tickRing_[w % kTickRing].store(framesWritten_ + f, std::memory_order_relaxed);
                tickWrite_.store(w + 1, std::memory_order_release);
                ++metroTick_;
                metroTickCount_.store(metroTick_, std::memory_order_relaxed);
                metroCountdown_ += framesPerTick;
            }
            metroCountdown_ -= 1.0;
        }
        for (auto& ck : clicks_) {
            if (!ck.active) continue;
            const auto& table = clickTables_[static_cast<size_t>(ck.level)];
            float sample = table[ck.pos] * metroVol;
            l += sample;
            r += sample;
            if (++ck.pos >= table.size()) ck.active = false;
        }
        auto limit = [](float x) {
            float ax = std::fabs(x);
            return ax > 0.89f ? std::copysign(0.89f + 0.11f * std::tanh((ax - 0.89f) / 0.11f), x) : x;
        };
        if (channels >= 2) {
            out[f * channels] = limit(l);
            out[f * channels + 1] = limit(r);
            for (int ch = 2; ch < channels; ++ch) out[f * channels + ch] = 0.0f;
        } else {
            out[f] = limit(0.5f * (l + r));
        }
    }

    framesWritten_ += numFrames;
    activeVoices_.store(active, std::memory_order_relaxed);
    if (active > maxActive_.load(std::memory_order_relaxed)) maxActive_.store(active, std::memory_order_relaxed);
    callbacks_.fetch_add(1, std::memory_order_relaxed);
    double us = std::chrono::duration<double, std::micro>(std::chrono::steady_clock::now() - t0).count();
    if (us > maxCallbackMicros_.load(std::memory_order_relaxed)) maxCallbackMicros_.store(us, std::memory_order_relaxed);
}

void DesktopEngine::prepareClicks(int rate) {
    static constexpr float kFreq[4] = {1568.0f, 1175.0f, 880.0f, 660.0f};
    static constexpr float kAmp[4] = {0.9f, 0.7f, 0.55f, 0.3f};
    const size_t n = static_cast<size_t>(rate * 0.035);
    for (int lvl = 0; lvl < 4; ++lvl) {
        auto& t = clickTables_[static_cast<size_t>(lvl)];
        t.assign(n, 0.0f);
        for (size_t i = 0; i < n; ++i) {
            float time = static_cast<float>(i) / static_cast<float>(rate);
            float env = std::exp(-time / 0.008f) * std::min(1.0f, time / 0.0005f);
            float w = 2.0f * float(M_PI) * kFreq[lvl] * time;
            t[i] = kAmp[lvl] * env * (0.77f * std::sin(w) + 0.23f * std::sin(2.01f * w));
        }
    }
    for (auto& ck : clicks_) ck.active = false;
}

void DesktopEngine::setMetronome(bool on, double bpm, int ticksPerBeat, const std::vector<int>& levels, float volume) {
    int len = std::min(static_cast<int>(levels.size()), kMaxPattern);
    for (int i = 0; i < len; ++i) metroPattern_[static_cast<size_t>(i)].store(static_cast<uint8_t>(std::clamp(levels[static_cast<size_t>(i)], 0, 3)));
    metroLen_.store(std::max(1, len));
    metroBpm_.store(std::clamp(bpm, 20.0, 300.0));
    metroTicksPerBeat_.store(std::clamp(ticksPerBeat, 1, 8));
    metroVolume_.store(std::clamp(volume, 0.0f, 1.0f));
    metroOn_.store(on);
}

std::vector<int64_t> DesktopEngine::drainTicks() {
    std::lock_guard<std::mutex> lock(tickMutex_);
    std::vector<int64_t> out;
    uint64_t w = tickWrite_.load(std::memory_order_acquire);
    if (w - tickRead_ > kTickRing) tickRead_ = w - kTickRing;
    for (; tickRead_ < w; ++tickRead_) out.push_back(tickRing_[tickRead_ % kTickRing].load(std::memory_order_relaxed));
    return out;
}

bool DesktopEngine::timestamp(int64_t& framePosition, int64_t& timeNanos) {
    if (!running_.load() || callbacks_.load() == 0) return false;
    for (int tries = 0; tries < 8; ++tries) {
        uint32_t before = stampSeq_.load(std::memory_order_acquire);
        if (before & 1u) continue;
        int64_t f = stampFrame_.load(std::memory_order_relaxed);
        int64_t n = stampNanos_.load(std::memory_order_relaxed);
        if (stampSeq_.load(std::memory_order_acquire) == before) {
            framePosition = f;
            timeNanos = n;
            return true;
        }
    }
    return false;
}

void DesktopEngine::prefetchLoop() {
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

EngineMetrics DesktopEngine::metrics() {
    EngineMetrics m;
    {
        std::lock_guard<std::mutex> lock(deviceMutex_);
        if (device_ != nullptr) {
            m.sampleRate = outRate_;
            m.periodFrames = periodFrames_;
            m.periods = periods_;
            m.estimatedLatencyMs = static_cast<double>(latencyNanos_.load()) / 1e6;
            m.exclusive = exclusive_;
        }
    }
    m.activeVoices = activeVoices_.load();
    m.maxActiveVoices = maxActive_.load();
    m.maxCallbackMicros = maxCallbackMicros_.load();
    m.callbacks = callbacks_.load();
    m.restarts = restarts_.load();
    m.residentBytes = static_cast<int64_t>(store_.residentHeadBytes());
    m.samplesLoaded = loaded_.load() ? static_cast<int>(store_.size()) : 0;
    m.stoppedByDevice = stoppedByDevice_.load();
    return m;
}

}  // namespace mimikopi
