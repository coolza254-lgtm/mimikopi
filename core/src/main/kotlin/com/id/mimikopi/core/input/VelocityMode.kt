package com.id.mimikopi.core.input

/**
 * Whether how hard a key is struck changes the app's piano sound. Off (the start, as the user asked): every note
 * sounds at one level, so a beginner hears even notes while learning to find them; on later, to practise touch.
 * Only the app's own sound follows this; nothing is judged by velocity, and a piano's own speaker follows the
 * piano's touch setting.
 */
data class VelocityMode(val on: Boolean = false, val fixed: Int = MEDIUM) {
    /** The velocity the app's piano plays for a key struck at [played] (1..127). */
    fun sound(played: Int): Int = if (on) played else fixed

    fun encode(): String = if (on) "on" else "off:$fixed"

    companion object {
        const val SOFT = 50
        const val MEDIUM = 80
        const val LOUD = 105
        val LEVELS = listOf(SOFT, MEDIUM, LOUD)

        /** "on" or "off:<level>"; anything else is the default (off, medium). */
        fun parse(text: String?): VelocityMode = when {
            text == "on" -> VelocityMode(on = true)
            text != null && text.startsWith("off:") -> VelocityMode(false, text.removePrefix("off:").toIntOrNull()?.coerceIn(1, 127) ?: MEDIUM)
            else -> VelocityMode()
        }
    }
}
