package com.id.mimikopi.input

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Coarse output route used to key latency offsets (section 4: wired / speaker / Bluetooth). */
object OutputRoute {
    const val SPEAKER = "SPEAKER"
    const val WIRED = "WIRED"
    const val BLUETOOTH = "BLUETOOTH"
    const val USB = "USB"
    const val UNKNOWN = "UNKNOWN"

    fun current(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return UNKNOWN
        val am = context.getSystemService(AudioManager::class.java)
        val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
        val type = am.getAudioDevicesForAttributes(media).firstOrNull()?.type ?: return UNKNOWN
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> SPEAKER
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> WIRED
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_HEARING_AID -> BLUETOOTH
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> USB
            else -> UNKNOWN
        }
    }
}
