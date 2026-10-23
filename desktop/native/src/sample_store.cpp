#include "sample_store.h"

#include <algorithm>

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#else
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#endif

namespace mimikopi {

namespace {

#ifdef _WIN32
std::wstring widen(const std::string& utf8) {
    int n = MultiByteToWideChar(CP_UTF8, 0, utf8.c_str(), -1, nullptr, 0);
    std::wstring w(static_cast<size_t>(n > 0 ? n : 1), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, utf8.c_str(), -1, w.data(), n);
    return w;
}

bool mapFile(const std::string& path, Sample& s, std::string& error) {
    HANDLE file = CreateFileW(widen(path).c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING,
                              FILE_ATTRIBUTE_NORMAL | FILE_FLAG_SEQUENTIAL_SCAN, nullptr);
    if (file == INVALID_HANDLE_VALUE) {
        error = "open " + path + ": error " + std::to_string(GetLastError());
        return false;
    }
    LARGE_INTEGER size{};
    if (!GetFileSizeEx(file, &size) || size.QuadPart < 4 || size.QuadPart % 4 != 0) {
        CloseHandle(file);
        error = "bad size: " + path;
        return false;
    }
    HANDLE mapping = CreateFileMappingW(file, nullptr, PAGE_READONLY, 0, 0, nullptr);
    CloseHandle(file);
    if (mapping == nullptr) {
        error = "map " + path + ": error " + std::to_string(GetLastError());
        return false;
    }
    void* p = MapViewOfFile(mapping, FILE_MAP_READ, 0, 0, 0);
    if (p == nullptr) {
        CloseHandle(mapping);
        error = "view " + path + ": error " + std::to_string(GetLastError());
        return false;
    }
    s.mapping = mapping;
    s.mapped = static_cast<const int16_t*>(p);
    s.mappedBytes = static_cast<size_t>(size.QuadPart);
    return true;
}

void unmap(Sample& s) {
    if (s.mapped != nullptr) UnmapViewOfFile(s.mapped);
    if (s.mapping != nullptr) CloseHandle(static_cast<HANDLE>(s.mapping));
}
#else
bool mapFile(const std::string& path, Sample& s, std::string& error) {
    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        error = "open " + path + ": " + std::strerror(errno);
        return false;
    }
    struct stat st {};
    if (fstat(fd, &st) != 0 || st.st_size < 4 || st.st_size % 4 != 0) {
        close(fd);
        error = "bad size: " + path;
        return false;
    }
    void* p = mmap(nullptr, static_cast<size_t>(st.st_size), PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (p == MAP_FAILED) {
        error = "mmap " + path + ": " + std::strerror(errno);
        return false;
    }
    madvise(p, static_cast<size_t>(st.st_size), MADV_SEQUENTIAL);
    s.mapped = static_cast<const int16_t*>(p);
    s.mappedBytes = static_cast<size_t>(st.st_size);
    return true;
}

void unmap(Sample& s) {
    if (s.mapped != nullptr) munmap(const_cast<int16_t*>(s.mapped), s.mappedBytes);
}
#endif

}  // namespace

SampleStore::~SampleStore() { clear(); }

void SampleStore::clear() {
    for (auto& s : samples_) unmap(s);
    samples_.clear();
}

bool SampleStore::load(const std::vector<std::string>& paths, int64_t headFrames, std::string& error) {
    clear();
    samples_.reserve(paths.size());
    for (const auto& path : paths) {
        Sample s;
        if (!mapFile(path, s, error)) {
            clear();
            return false;
        }
        s.frames = static_cast<int64_t>(s.mappedBytes / 4);
        s.headFrames = std::min(headFrames, s.frames);
        s.head.assign(s.mapped, s.mapped + s.headFrames * 2);
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
