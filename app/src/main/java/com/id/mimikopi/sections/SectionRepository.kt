package com.id.mimikopi.sections

import com.id.mimikopi.core.section.Section
import com.id.mimikopi.data.db.AppDatabase
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The user's sections for section practice, kept in the settings table (so JSON backups include them). */
class SectionRepository(private val db: AppDatabase) {
    private val mutex = Mutex()
    private var loaded = false
    private val _sections = MutableStateFlow<List<Section>>(emptyList())
    /** Newest first. */
    val sections: StateFlow<List<Section>> = _sections.asStateFlow()

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        loaded = true
        _sections.value = db.settingsDao().get(SettingKeys.SECTIONS)
            ?.let { runCatching { Section.parseList(it) }.getOrNull() }.orEmpty()
            .sortedByDescending { it.createdAtMillis }
    }

    fun section(id: String): Section? = _sections.value.firstOrNull { it.id == id }

    suspend fun save(section: Section) {
        load()
        write((_sections.value.filter { it.id != section.id } + section).sortedByDescending { it.createdAtMillis })
    }

    suspend fun delete(id: String) {
        load()
        write(_sections.value.filter { it.id != id })
    }

    private suspend fun write(list: List<Section>) = mutex.withLock {
        _sections.value = list
        db.settingsDao().put(SettingEntity(SettingKeys.SECTIONS, Section.toJson(list)))
    }

    fun newId(): String = "sec-" + System.currentTimeMillis().toString(36)
}
