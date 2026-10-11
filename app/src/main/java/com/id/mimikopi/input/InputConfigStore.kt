package com.id.mimikopi.input

import com.id.mimikopi.core.input.InputConfig
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.data.db.SettingKeys
import com.id.mimikopi.data.db.SettingsDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

/** Persists calibration results (octave offset, velocity probe, latency per input/output kind). */
class InputConfigStore(private val dao: SettingsDao) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _config = MutableStateFlow(InputConfig())
    val config: StateFlow<InputConfig> = _config.asStateFlow()
    private var loaded = false

    suspend fun load(): InputConfig {
        if (!loaded) {
            loaded = true
            dao.get(SettingKeys.INPUT_CONFIG)
                ?.let { runCatching { json.decodeFromString(InputConfig.serializer(), it) }.getOrNull() }
                ?.let { _config.value = it }
        }
        return _config.value
    }

    suspend fun update(transform: (InputConfig) -> InputConfig) {
        _config.update(transform)
        dao.put(SettingEntity(SettingKeys.INPUT_CONFIG, json.encodeToString(InputConfig.serializer(), _config.value)))
    }
}
