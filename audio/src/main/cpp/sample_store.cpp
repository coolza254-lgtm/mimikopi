#include "sample_store.h"

#include <algorithm>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

namespace mimikopi {

SampleStore::~SampleStore() { clear(); }

void SampleStore::clear() {
    for (auto& s : samples_) {
        if (s.mapped != nullptr) munmap(const_cast<int16_t*>(s.mapped), s.mappedBytes);
    }
    samples_.clear();
}

bool SampleStore::load(const std::vector<std::string>& paths, int64_t headFrames, std::string& error) {
    clear();
    samples_.reserve(paths.size());
    for (const auto& path : paths) {
        int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
        if (fd < 0) {
            error = "open " + path + ": " + std::strerror(errno);
            clear();
            return false;
        }
        struct stat st {};
        if (fstat(fd, &st) != 0 || st.st_size < 4 || st.st_size % 4 != 0) {
            close(fd);
            error = "bad size: " + path;
            clear();
            return false;
        }
        void* p = mmap(nullptr, static_cast<size_t>(st.st_size), PROT_READ, MAP_PRIVATE, fd, 0);
        close(fd);
        if (p == MAP_FAILED) {
            error = "mmap " + path + ": " + std::strerror(errno);
            clear();
            return false;
        }
        Sample s;
        s.mapped = static_cast<const int16_t*>(p);
        s.mappedBytes = static_cast<size_t>(st.st_size);
        s.frames = st.st_size / 4;
        s.headFrames = std::min(headFrames, s.frames);
        s.head.assign(s.mapped, s.mapped + s.headFrames * 2);
        madvise(p, s.mappedBytes, MADV_SEQUENTIAL);
        samples_.push_back(std::move(s));
    }
    return true;
}

void SampleStore::prefetch(size_t index, int64_t fromFrame, int64_t frames) const {
    if (index >= samples_.size()) return;
    const Sample& s = samples_[index];
    int64_t begin = std::max(fromFrame, s.headFrames);
    int64_t end = std::min(s.frames, fromFrame + frames);
    if (begin >= end) return;
    const auto* bytes = reinterpret_cast<const volatile uint8_t*>(s.mapped);
    constexpr int64_t kPage = 4096;
    uint8_t sink = 0;
    for (int64_t b = (begin * 4) & ~(kPage - 1); b < end * 4; b += kPage) sink ^= bytes[b];
    (void)sink;
}

size_t SampleStore::residentHeadBytes() const {
    size_t total = 0;
    for (const auto& s : samples_) total += s.head.size() * sizeof(int16_t);
    return total;
}

}  // namespace mimikopi
