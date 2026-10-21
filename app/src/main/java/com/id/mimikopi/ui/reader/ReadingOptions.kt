package com.id.mimikopi.ui.reader

/** Free reading may change pace; fixed lessons, benchmarks and exams keep their assessment settings. */
internal fun ReaderOptions.constrainReadingChange(requested: ReaderOptions, fixed: Boolean, freeReading: Boolean): ReaderOptions {
    if (!fixed) return requested
    return copy(
        mode = if (freeReading) requested.mode else mode,
        showKeyboard = requested.showKeyboard,
        playOtherHand = requested.playOtherHand,
        pageView = requested.pageView,
        paperSize = requested.paperSize,
    )
}

/** Training starts without a timing demand; benchmarks and exams construct their own fixed options. */
internal fun ReaderOptions.newReadingOptions(level: Int) = ReaderOptions(
    mode = com.id.mimikopi.core.playalong.PlayMode.WAIT,
    hands = if (level == 2) HandChoice.LEFT else if (level == 5) HandChoice.BOTH else HandChoice.RIGHT,
    playOtherHand = false, metronome = true, useRamp = false, showKeyboard = showKeyboard,
    pageView = pageView, paperSize = false,
)
