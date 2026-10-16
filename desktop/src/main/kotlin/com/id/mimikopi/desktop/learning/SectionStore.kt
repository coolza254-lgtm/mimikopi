package com.id.mimikopi.desktop.learning

import com.id.mimikopi.core.section.Section
import com.id.mimikopi.desktop.Keys
import com.id.mimikopi.desktop.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's sections (same "sections" row as the Android app), newest first. */
class SectionStore(private val store: Store) {
    private val _sections = MutableStateFlow(load())
    val sections: StateFlow<List<Section>> = _sections.asStateFlow()

    private fun load(): List<Section> = store.get(Keys.SECTIONS)?.let { runCatching { Section.parseList(it) }.getOrNull() }.orEmpty()
        .sortedByDescending { it.createdAtMillis }

    fun reload() { _sections.value = load() }

    fun section(id: String): Section? = _sections.value.firstOrNull { it.id == id }

    @Synchronized
    fun save(s: Section) = write((_sections.value.filter { it.id != s.id } + s).sortedByDescending { it.createdAtMillis })

    @Synchronized
    fun delete(id: String) = write(_sections.value.filter { it.id != id })

    private fun write(list: List<Section>) {
        _sections.value = list
        store.put(Keys.SECTIONS, Section.toJson(list))
    }

    fun newId(): String = "sec-" + System.currentTimeMillis().toString(36)
}
