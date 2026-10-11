#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <vector>

namespace mimikopi {

/** RBJ-cookbook biquad (transposed direct form II), one per channel. */
struct Biquad {
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    float z1 = 0, z2 = 0;

    inline float process(float x) {
        float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }
    void reset() { z1 = z2 = 0; }

    void setPeaking(float rate, float freq, float q, float gainDb) {
        float A = std::pow(10.0f, gainDb / 40.0f), w = 2.0f * float(M_PI) * freq / rate;
        float alpha = std::sin(w) / (2.0f * q), c = std::cos(w);
        float a0 = 1 + alpha / A;
        set((1 + alpha * A) / a0, (-2 * c) / a0, (1 - alpha * A) / a0, (-2 * c) / a0, (1 - alpha / A) / a0);
    }
    void setHighShelf(float rate, float freq, float gainDb) {
        float A = std::pow(10.0f, gainDb / 40.0f), w = 2.0f * float(M_PI) * freq / rate;
        float c = std::cos(w), alpha = std::sin(w) / 2.0f * std::sqrt(2.0f), sa = 2 * std::sqrt(A) * alpha;
        float a0 = (A + 1) - (A - 1) * c + sa;
        set(A * ((A + 1) + (A - 1) * c + sa) / a0, -2 * A * ((A - 1) + (A + 1) * c) / a0,
            A * ((A + 1) + (A - 1) * c - sa) / a0, 2 * ((A - 1) - (A + 1) * c) / a0, ((A + 1) - (A - 1) * c - sa) / a0);
    }
    void setHighPass(float rate, float freq, float q) {
        float w = 2.0f * float(M_PI) * freq / rate, alpha = std::sin(w) / (2.0f * q), c = std::cos(w);
        float a0 = 1 + alpha;
        set((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0);
    }

private:
    void set(float nb0, float nb1, float nb2, float na1, float na2) {
        b0 = nb0; b1 = nb1; b2 = nb2; a1 = na1; a2 = na2;
    }
};

/**
 * Freeverb (Jezar at Dreampoint, public domain): 8 parallel damped combs + 4 series allpasses per
 * channel, tunings scaled from 44.1 kHz. Buffers are allocated in prepare(), never in process().
 */
class Freeverb {
public:
    void prepare(int rate) {
        static constexpr int kComb[8] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        static constexpr int kAllpass[4] = {556, 441, 341, 225};
        static constexpr int kSpread = 23;
        float scale = static_cast<float>(rate) / 44100.0f;
        for (int ch = 0; ch < 2; ++ch) {
            for (int i = 0; i < 8; ++i) combs_[ch][i].init(static_cast<int>((kComb[i] + ch * kSpread) * scale));
            for (int i = 0; i < 4; ++i) allpasses_[ch][i].init(static_cast<int>((kAllpass[i] + ch * kSpread) * scale));
        }
        setRoom(room_, damp_);
    }

    void setRoom(float room, float damp) {
        room_ = room;
        damp_ = damp;
        for (auto& chan : combs_)
            for (auto& c : chan) {
                c.feedback = room * 0.28f + 0.7f;
                c.damp1 = damp * 0.4f;
                c.damp2 = 1.0f - c.damp1;
            }
    }

    /** Adds the wet signal (level `wet`) to l/r in place. */
    inline void process(float& l, float& r, float wet) {
        float in = (l + r) * 0.015f;
        float outL = 0, outR = 0;
        for (auto& c : combs_[0]) outL += c.process(in);
        for (auto& c : combs_[1]) outR += c.process(in);
        for (auto& a : allpasses_[0]) outL = a.process(outL);
        for (auto& a : allpasses_[1]) outR = a.process(outR);
        l += outL * wet * 3.0f;
        r += outR * wet * 3.0f;
    }

private:
    struct Comb {
        std::vector<float> buf;
        size_t idx = 0;
        float store = 0, feedback = 0.84f, damp1 = 0.2f, damp2 = 0.8f;
        void init(int n) { buf.assign(static_cast<size_t>(std::max(n, 1)), 0.0f); idx = 0; store = 0; }
        inline float process(float x) {
            float y = buf[idx];
            store = y * damp2 + store * damp1;
            if (std::fabs(store) < 1e-20f) store = 0;  // avoid denormals in the tail
            buf[idx] = x + store * feedback;
            if (++idx >= buf.size()) idx = 0;
            return y;
        }
    };
    struct Allpass {
        std::vector<float> buf;
        size_t idx = 0;
        void init(int n) { buf.assign(static_cast<size_t>(std::max(n, 1)), 0.0f); idx = 0; }
        inline float process(float x) {
            float b = buf[idx];
            float y = b - x;
            float v = x + b * 0.5f;
            if (std::fabs(v) < 1e-20f) v = 0;
            buf[idx] = v;
            if (++idx >= buf.size()) idx = 0;
            return y;
        }
    };
    std::array<std::array<Comb, 8>, 2> combs_;
    std::array<std::array<Allpass, 4>, 2> allpasses_;
    float room_ = 0.6f, damp_ = 0.5f;
};

/** Stereo-linked feed-forward compressor with soft knee; lets a phone speaker play louder without clipping. */
class Compressor {
public:
    void prepare(int rate) {
        attack_ = std::exp(-1.0f / (0.003f * rate));
        release_ = std::exp(-1.0f / (0.150f * rate));
        env_ = 0;
    }
    inline float gainFor(float l, float r) {
        float peak = std::max(std::fabs(l), std::fabs(r));
        float coef = peak > env_ ? attack_ : release_;
        env_ = coef * env_ + (1 - coef) * peak;
        float db = 20.0f * std::log10(env_ + 1e-9f);
        float over = db - kThresholdDb;
        float reduction;
        if (over <= -kKneeDb / 2) reduction = 0;
        else if (over >= kKneeDb / 2) reduction = over * (1 - 1 / kRatio);
        else {
            float x = over + kKneeDb / 2;
            reduction = (1 - 1 / kRatio) * x * x / (2 * kKneeDb);
        }
        return std::pow(10.0f, -reduction / 20.0f);
    }

private:
    static constexpr float kThresholdDb = -14.0f, kRatio = 3.0f, kKneeDb = 6.0f;
    float attack_ = 0, release_ = 0, env_ = 0;
};

}  // namespace mimikopi
