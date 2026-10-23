package com.id.mimikopi.core.input

/**
 * The pink toy piano drawn beside the piano's name on both apps (design "B", chosen 2026-10-22): SVG path data on a
 * 64 × 64 grid, painted in order. Colours are fixed (the app icon's pink and mustard), so it reads on light and dark.
 */
object ToyPianoArt {
    const val SIZE = 64f

    /** One shape: filled with [fill] or, when [stroke] > 0, a round-capped line of that width. ARGB colours. */
    data class Layer(val path: String, val color: Long, val stroke: Float = 0f)

    private const val PINK = 0xFFE88AA0
    private const val PINK_DEEP = 0xFFD46F88
    private const val PINK_SOFT = 0xFFF6C1CE
    private const val MUSTARD = 0xFFF2BF55
    private const val PLUM = 0xFF6B3A4A
    private const val WHITE = 0xFFFFFFFF

    private fun blackKey(x: Float) = "M$x,31 h3 v5.5 a1,1 0 0 1 -1,1 h-1 a1,1 0 0 1 -1,-1 z"

    val LAYERS: List<Layer> = listOf(
        // Feet, body and lid.
        Layer("M13,47.5 a3.5,3.5 0 0 1 7,0 v5 a3.5,3.5 0 0 1 -7,0 z M44,47.5 a3.5,3.5 0 0 1 7,0 v5 a3.5,3.5 0 0 1 -7,0 z", PINK_DEEP),
        Layer("M17,15 H47 A9,9 0 0 1 56,24 V38 A9,9 0 0 1 47,47 H17 A9,9 0 0 1 8,38 V24 A9,9 0 0 1 17,15 Z", PINK),
        Layer("M9.5,10 H54.5 A4.5,4.5 0 0 1 54.5,19 H9.5 A4.5,4.5 0 0 1 9.5,10 Z", PINK_DEEP),
        Layer("M13.8,24 a2.2,2.2 0 1 0 4.4,0 a2.2,2.2 0 1 0 -4.4,0 z M45.8,24 a2.2,2.2 0 1 0 4.4,0 a2.2,2.2 0 1 0 -4.4,0 z", PINK_SOFT),
        // The mustard eighth note.
        Layer("M33.5,19.5 V27", MUSTARD, 1.8f),
        Layer("M33.5,19.5 Q37,21 36,23.5", MUSTARD, 1.8f),
        Layer("M28.669,28.358 A2.8,2.1 -20 1 0 33.931,26.442 A2.8,2.1 -20 1 0 28.669,28.358 Z", MUSTARD),
        // Eight white keys, five black ones (C D E F G A B C).
        Layer("M15,31 H49 A3,3 0 0 1 52,34 V40 A3,3 0 0 1 49,43 H15 A3,3 0 0 1 12,40 V34 A3,3 0 0 1 15,31 Z", WHITE),
        Layer("M17,32 V42 M22,32 V42 M27,32 V42 M32,32 V42 M37,32 V42 M42,32 V42 M47,32 V42", PINK_SOFT, 1f),
        Layer(listOf(15.5f, 20.5f, 30.5f, 35.5f, 40.5f).joinToString(" ") { blackKey(it) }, PLUM),
    )
}
