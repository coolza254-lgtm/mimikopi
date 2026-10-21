package com.id.mimikopi.ui.reading

/** The reading pools live in core so the phone and the computer practise the same notes. */
typealias ReadingClef = com.id.mimikopi.core.practice.ReadingClef
typealias ReadingRange = com.id.mimikopi.core.practice.ReadingRange
typealias ReadingPractice = com.id.mimikopi.core.practice.ReadingPractice

/** Pages never follow the target note: the player chooses the octave, so navigation cannot reveal an answer. */
internal fun readingKeyPages(range: IntRange): List<IntRange> {
    require(range.first >= 0 && range.last <= 127 && !range.isEmpty())
    val firstC = range.first / 12 * 12
    return generateSequence(firstC) { it + 12 }.takeWhile { it < range.last || it == firstC }
        .map { it..minOf(it + 12, 127) }.toList()
}
