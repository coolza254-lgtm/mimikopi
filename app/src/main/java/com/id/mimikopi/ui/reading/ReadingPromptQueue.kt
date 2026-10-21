package com.id.mimikopi.ui.reading

import com.id.mimikopi.ui.practice.Prompt

/** Retry status belongs to a queue entry, not its pitch: later original questions must still count. */
internal class ReadingPromptQueue {
    data class Entry(val prompt: Prompt, val retry: Boolean = false)
    private val entries = ArrayDeque<Entry>()
    private val repeated = mutableSetOf<Prompt>()
    val size: Int get() = entries.size
    fun clear() { entries.clear(); repeated.clear() }
    fun addAll(prompts: List<Prompt>) { entries.addAll(prompts.map { Entry(it) }) }
    fun next(): Entry? = entries.removeFirstOrNull()
    fun repeatOnce(prompt: Prompt) {
        if (repeated.add(prompt)) entries.addLast(Entry(prompt, retry = true))
    }
}
