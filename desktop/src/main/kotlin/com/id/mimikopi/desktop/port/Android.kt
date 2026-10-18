package com.id.mimikopi.desktop.port

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import org.w3c.dom.Element
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

/*
 * The few Android APIs the ported screens use (desktop/.../port), with the same names and shapes, so the Android
 * code is copied with only its imports changed. Everything here is plain Compose Desktop and coroutines.
 */

// ---------------- resources ----------------

/** The Android string with that id ([R.string], generated from the app's strings.xml). */
fun stringResource(id: Int): String = STRING_VALUES[id]

/** The Android string with that id, formatted like Resources.getString(id, args). */
fun stringResource(id: Int, vararg formatArgs: Any): String = STRING_VALUES[id].format(*formatArgs)

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/** Icons read so far by [R.drawable] id: each file is parsed once per run, not once per screen that shows it. */
private val vectorCache = ConcurrentHashMap<Int, ImageVector>()

/** One XML factory for every icon (a factory is not thread-safe, so builders are made under its lock). */
private val xmlFactory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }

/** An Android vector drawable ([R.drawable]) drawn by Compose; icons are tinted by the caller as on Android. */
@Composable
fun painterResource(id: Int): Painter = rememberVectorPainter(remember(id) { vectorCache.getOrPut(id) { loadVectorDrawable(DRAWABLE_FILES[id]) } })

/** Reads the paths of an Android vector drawable XML from the classpath (the app's res/drawable is a resource folder). */
internal fun loadVectorDrawable(file: String): ImageVector {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(file) ?: error("missing drawable $file")
    val xml = synchronized(xmlFactory) { xmlFactory.newDocumentBuilder() }
    val doc = stream.use { xml.parse(it) }
    val root = doc.documentElement
    fun Element.attr(name: String): String = getAttributeNS(ANDROID_NS, name)
    fun dp(s: String) = s.removeSuffix("dp").toFloatOrNull() ?: 24f
    val builder = ImageVector.Builder(
        name = file.removeSuffix(".xml"),
        defaultWidth = dp(root.attr("width")).dp, defaultHeight = dp(root.attr("height")).dp,
        viewportWidth = root.attr("viewportWidth").toFloatOrNull() ?: 24f, viewportHeight = root.attr("viewportHeight").toFloatOrNull() ?: 24f,
    )
    val paths = root.getElementsByTagName("path")
    for (i in 0 until paths.length) {
        val p = paths.item(i) as Element
        builder.addPath(
            pathData = addPathNodes(p.attr("pathData")),
            pathFillType = if (p.attr("fillType") == "evenOdd") PathFillType.EvenOdd else PathFillType.NonZero,
            fill = androidColor(p.attr("fillColor"))?.let { SolidColor(it) },
            stroke = androidColor(p.attr("strokeColor"))?.let { SolidColor(it) },
            strokeLineWidth = p.attr("strokeWidth").toFloatOrNull() ?: 0f,
            strokeLineCap = when (p.attr("strokeLineCap")) { "round" -> StrokeCap.Round; "square" -> StrokeCap.Square; else -> StrokeCap.Butt },
            strokeLineJoin = when (p.attr("strokeLineJoin")) { "round" -> StrokeJoin.Round; "bevel" -> StrokeJoin.Bevel; else -> StrokeJoin.Miter },
        )
    }
    return builder.build()
}

/** "#RRGGBB" or "#AARRGGBB"; null when absent or fully transparent. */
private fun androidColor(s: String): Color? {
    if (!s.startsWith("#")) return null
    val hex = s.removePrefix("#")
    val argb = when (hex.length) {
        6 -> 0xFF000000L or hex.toLong(16)
        8 -> hex.toLong(16)
        else -> return null
    }
    if (argb ushr 24 == 0L) return null
    return Color(argb.toInt())
}

/** android.os.SystemClock: only uptimeMillis is used (tap tempo). */
object SystemClock {
    fun uptimeMillis(): Long = System.nanoTime() / 1_000_000
}

// ---------------- view models ----------------

open class Application

/** androidx.lifecycle.ViewModel: a scope on the UI thread, cancelled when the screen goes away ([clear]). */
abstract class ViewModel {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    protected open fun onCleared() {}

    /** Called once when the screen that owns it leaves the composition. */
    fun clear() {
        onCleared()
        scope.cancel()
    }
}

val ViewModel.viewModelScope: CoroutineScope get() = scope

open class AndroidViewModel(private val application: Application) : ViewModel() {
    @Suppress("UNCHECKED_CAST")
    fun <T : Application> getApplication(): T = application as T
}

/** Navigation arguments, as the Android screens read them. */
class SavedStateHandle(private val values: Map<String, String?> = emptyMap()) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: String): T? = values[key] as T?
}

/** Creates a screen's view model once and clears it when the screen leaves the composition. */
@Composable
fun <T : ViewModel> rememberViewModel(vararg keys: Any?, create: () -> T): T {
    val vm = remember(*keys) { create() }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    return vm
}

/**
 * View models of the pages in the window's stacks. A page that is only hidden (another page on top, another tab) keeps
 * its view model, so it comes back with its data and scroll position at once; the window clears it when the page
 * leaves its stack.
 */
class PageModels {
    private val models = HashMap<String, ViewModel>()

    @Suppress("UNCHECKED_CAST")
    fun <T : ViewModel> getOrCreate(key: String, create: () -> T): T = models.getOrPut(key, create) as T

    fun clear(key: String) { models.remove(key)?.clear() }
}

/** The window's page models and the key of the page shown (null outside the window, e.g. in tests). */
val LocalPageModels = staticCompositionLocalOf<PageModels?> { null }
val LocalPageKey = staticCompositionLocalOf<String?> { null }

/**
 * A list page's view model: kept by the window while the page is in a stack, else as [rememberViewModel]. Only for
 * pages that neither take piano input nor hold the sound, since a hidden page's view model stays alive.
 */
@Composable
fun <T : ViewModel> rememberPageViewModel(create: () -> T): T {
    val models = LocalPageModels.current
    val key = LocalPageKey.current
    if (models == null || key == null) return rememberViewModel(create = create)
    return remember(key) { models.getOrCreate(key, create) }
}

// ---------------- lifecycle ----------------

abstract class Lifecycle {
    enum class Event { ON_CREATE, ON_START, ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY, ON_ANY }
}

/**
 * A desktop screen is "started" while it is shown: start/resume events run when it enters the composition,
 * pause/stop events when it leaves.
 */
@Composable
fun LifecycleEventEffect(event: Lifecycle.Event, onEvent: () -> Unit) {
    val current by rememberUpdatedState(onEvent)
    DisposableEffect(Unit) {
        if (event == Lifecycle.Event.ON_CREATE || event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) current()
        onDispose {
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) current()
        }
    }
}

@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(): State<T> = collectAsState()

/** Stand-in for Android's Context: the ported code only asks it for the application. */
object AndroidContext {
    val applicationContext: Any get() = MimikopiApp
}

val LocalContext = staticCompositionLocalOf { AndroidContext }
