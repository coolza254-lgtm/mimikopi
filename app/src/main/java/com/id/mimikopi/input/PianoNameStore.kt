package com.id.mimikopi.input

import com.id.mimikopi.core.input.PianoNames
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingsDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's own names for their pianos ([PianoNames]), kept in one setting. */
class PianoNameStore(private val dao: SettingsDao) {
    private val _own = MutableStateFlow<Map<String, String>>(emptyMap())
    val own: StateFlow<Map<String, String>> = _own.asStateFlow()

    suspend fun load() {
        _own.value = PianoNames.decode(dao.get(PianoNames.SETTING_KEY))
    }

    fun display(reported: String): String = PianoNames.display(reported, _own.value)

    suspend fun rename(reported: String, name: String) {
        _own.value = PianoNames.rename(_own.value, reported, name)
        dao.put(SettingEntity(PianoNames.SETTING_KEY, PianoNames.encode(_own.value)))
    }
}
