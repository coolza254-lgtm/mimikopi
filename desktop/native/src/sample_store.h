#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace mimikopi {

/**
 * One decoded sample: 16-bit interleaved stereo. The first `headFrames` live in RAM so a note can start without
 * touching the disk; the rest is memory-mapped from the decoded .pcm file and paged in ahead of playback by the
 * prefetch thread. Same layout as the Android engine's store.
 */
struct Sample {
    std::vector<int16_t> head;
    const int16_t* mapped = nullptr;
    size_t mappedBytes = 0;
    int64_t frames = 0;
    int64_t headFrames = 0;
    void* mapping = nullptr;  // Windows file-mapping handle

    inline const int16_t* frameAt(int64_t i) const {
        return i < headFrames ? &head[static_cast<size_t>(i) * 2] : &mapped[static_cast<size_t>(i) * 2];
    }
};

class SampleStore {
public:
    ~SampleStore();
    bool load(const std::vector<std::string>& paths, int64_t headFrames, std::string& error);
    void clear();
    const Sample& operator[](size_t i) const { return samples_[i]; }
    size_t size() const { return samples_.size(); }
    void prefetch(size_t index, int64_t fromFrame, int64_t frames) const;
    size_t residentHeadBytes() const;

private:
    std::vector<Sample> samples_;
};

}  // namespace mimikopi
