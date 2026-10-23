#include "clock.h"

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#else
#include <ctime>
#endif

namespace mimikopi {

int64_t nowNanos() {
#ifdef _WIN32
    static LARGE_INTEGER freq = [] { LARGE_INTEGER f; QueryPerformanceFrequency(&f); return f; }();
    LARGE_INTEGER c;
    QueryPerformanceCounter(&c);
    // Split to avoid overflow: whole seconds, then the remainder.
    const int64_t s = c.QuadPart / freq.QuadPart;
    const int64_t r = c.QuadPart % freq.QuadPart;
    return s * 1000000000LL + r * 1000000000LL / freq.QuadPart;
#else
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000000000LL + ts.tv_nsec;
#endif
}

}  // namespace mimikopi
