#pragma once

#include <cstdint>

namespace mimikopi {

/** Monotonic time in nanoseconds: QueryPerformanceCounter on Windows (what the JVM's System.nanoTime uses), CLOCK_MONOTONIC elsewhere. */
int64_t nowNanos();

}  // namespace mimikopi
