package com.id.mimikopi.learning

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The practice session started on the Today screen; attempts made meanwhile are linked to it. */
class SessionTracker(private val repo: LearningRepository) {
    data class Active(val id: Long, val startedAtMillis: Long, val plannedMinutes: Int)

    private val _active = MutableStateFlow<Active?>(null)
    val active: StateFlow<Active?> = _active.asStateFlow()

    fun currentId(): Long? = _active.value?.id

    suspend fun start(plannedMinutes: Int) {
        if (_active.value != null) return
        val id = repo.startSession(plannedMinutes)
        _active.value = Active(id, System.currentTimeMillis(), plannedMinutes)
    }

    /** Ends the session; returns minutes practised. Time is capped (a session left open overnight is not 8 hours of practice). */
    suspend fun finish(): Long {
        val a = _active.value ?: return 0
        val seconds = ((System.currentTimeMillis() - a.startedAtMillis) / 1000).coerceAtMost(a.plannedMinutes * 60L * 2 + 600)
        repo.finishSession(a.id, seconds)
        _active.value = null
        return seconds / 60
    }
}
