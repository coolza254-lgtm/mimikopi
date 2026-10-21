package com.id.mimikopi.ui.reader

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Owns immersive mode only while the landscape falling player is on screen. */
@Composable
internal fun ReaderFullscreen(enabled: Boolean) {
    val window = LocalActivity.current?.window
    DisposableEffect(window, enabled) {
        val session = if (enabled && window != null) ReaderFullscreenSession(window) else null
        onDispose { session?.close() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (enabled && window != null) WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())
    }
}

internal class ReaderFullscreenSession(private val window: Window) : AutoCloseable {
    private val controller = WindowCompat.getInsetsController(window, window.decorView)
    private val oldBehavior = controller.systemBarsBehavior
    private val previousInsets = ViewCompat.getRootWindowInsets(window.decorView)
    private val statusWasVisible = previousInsets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
    private val navigationWasVisible = previousInsets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
    private val cutoutMode = if (Build.VERSION.SDK_INT >= 28) window.attributes.layoutInDisplayCutoutMode else 0

    init {
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun close() {
        controller.systemBarsBehavior = oldBehavior
        if (statusWasVisible) controller.show(WindowInsetsCompat.Type.statusBars())
        else controller.hide(WindowInsetsCompat.Type.statusBars())
        if (navigationWasVisible) controller.show(WindowInsetsCompat.Type.navigationBars())
        else controller.hide(WindowInsetsCompat.Type.navigationBars())
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            // Keep the framework's IntDef constants visible to lint while restoring the saved mode.
            layoutInDisplayCutoutMode = when (cutoutMode) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                else -> if (Build.VERSION.SDK_INT >= 30 && cutoutMode == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS)
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }
    }
}
