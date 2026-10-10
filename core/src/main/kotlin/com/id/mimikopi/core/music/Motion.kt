package com.id.mimikopi.core.music

import kotlin.math.abs

/** Reading by distance (learning-system.md 1.6): repeat / step / skip, plus direction. */
enum class MotionKind { REPEAT, STEP, SKIP }

enum class Direction { UP, DOWN, SAME }

/**
 * Melodic motion between two written notes, measured on the staff (letter steps), not in semitones.
 * [staffSteps] is the absolute number of staff positions moved (a 3rd = 2, a 5th = 4).
 */
data class Motion(val kind: MotionKind, val direction: Direction, val staffSteps: Int) {
    /** Generic interval number as musicians name it (unison = 1, 2nd = 2, 3rd = 3 ...). */
    val intervalNumber: Int get() = staffSteps + 1

    companion object {
        fun between(from: SpelledPitch, to: SpelledPitch): Motion {
            val delta = to.diatonicIndex - from.diatonicIndex
            val kind = when (abs(delta)) {
                0 -> MotionKind.REPEAT
                1 -> MotionKind.STEP
                else -> MotionKind.SKIP
            }
            val direction = when {
                delta > 0 -> Direction.UP
                delta < 0 -> Direction.DOWN
                else -> Direction.SAME
            }
            return Motion(kind, direction, abs(delta))
        }
    }
}
