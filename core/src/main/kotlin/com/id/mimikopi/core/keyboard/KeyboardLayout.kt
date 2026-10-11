package com.id.mimikopi.core.keyboard

import com.id.mimikopi.core.music.isBlackKey

/** A key's rectangle in layout units (same units as the width/height given to [KeyboardLayout]). */
data class KeyRect(val note: Int, val black: Boolean, val left: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
}

/**
 * Piano keyboard geometry and touch hit-testing, independent of any UI toolkit.
 * White keys are equal width; black keys are narrower and offset the way real keys are
 * (C#/D# lean outward from the two-black group, F#/A# around the middle of the three-black group).
 */
class KeyboardLayout(
    lowNote: Int,
    highNote: Int,
    val whiteKeyWidth: Float,
    val height: Float,
    val blackHeightRatio: Float = 0.62f,
    val blackWidthRatio: Float = 0.6f,
) {
    val lowNote: Int = if (isBlackKey(lowNote)) lowNote - 1 else lowNote
    val highNote: Int = if (isBlackKey(highNote)) highNote + 1 else highNote

    val whiteKeys: List<KeyRect>
    val blackKeys: List<KeyRect>

    init {
        require(this.lowNote in 0..127 && this.highNote in this.lowNote..127)
        val whites = mutableListOf<KeyRect>()
        val blacks = mutableListOf<KeyRect>()
        var x = 0f
        for (note in this.lowNote..this.highNote) {
            if (!isBlackKey(note)) {
                whites += KeyRect(note, false, x, x + whiteKeyWidth, height)
                x += whiteKeyWidth
            } else {
                // Centre sits on the boundary to the left (x), nudged by the key's position in its group.
                val bw = whiteKeyWidth * blackWidthRatio
                val center = x + whiteKeyWidth * BLACK_OFFSET.getValue(note % 12)
                blacks += KeyRect(note, true, center - bw / 2, center + bw / 2, height * blackHeightRatio)
            }
        }
        whiteKeys = whites
        blackKeys = blacks
    }

    val totalWidth: Float get() = whiteKeys.size * whiteKeyWidth

    /** The note under (x, y), black keys first because they sit on top; null outside the keyboard. */
    fun hitTest(x: Float, y: Float): Int? {
        if (y < 0 || y > height || x < 0 || x >= totalWidth) return null
        if (y <= height * blackHeightRatio) {
            blackKeys.firstOrNull { x >= it.left && x < it.right }?.let { return it.note }
        }
        val index = (x / whiteKeyWidth).toInt().coerceIn(0, whiteKeys.lastIndex)
        return whiteKeys[index].note
    }

    /** Left edge of a note's key, for scrolling a note into view. */
    fun leftOf(note: Int): Float? = (whiteKeys + blackKeys).firstOrNull { it.note == note }?.left

    private companion object {
        // Offset of the black key centre relative to the boundary on its left, in white-key widths.
        val BLACK_OFFSET = mapOf(1 to -0.08f, 3 to 0.08f, 6 to -0.1f, 8 to 0.0f, 10 to 0.1f)
    }
}
