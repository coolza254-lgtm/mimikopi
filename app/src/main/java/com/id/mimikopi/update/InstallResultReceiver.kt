package com.id.mimikopi.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Install progress reported by the system installer. */
sealed interface InstallEvent {
    /** The system wants the user to confirm; the UI must start [confirm] from the foreground activity. */
    data class NeedsConfirmation(val confirm: Intent) : InstallEvent
    data class Failed(val status: Int, val message: String?) : InstallEvent
    data object Succeeded : InstallEvent
}

/** Bridges the receiver (no UI) and the screen that shows the result. */
object InstallEvents {
    private val _events = MutableStateFlow<InstallEvent?>(null)
    val events: StateFlow<InstallEvent?> = _events.asStateFlow()

    fun post(event: InstallEvent) {
        _events.value = event
    }

    fun consume() {
        _events.value = null
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val event = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) InstallEvent.NeedsConfirmation(confirm) else InstallEvent.Failed(status, "no confirmation intent")
            }
            PackageInstaller.STATUS_SUCCESS -> InstallEvent.Succeeded
            else -> InstallEvent.Failed(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
        InstallEvents.post(event)
    }
}
