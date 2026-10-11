package com.id.mimikopi.ui.knowledge

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.knowledge.Article
import com.id.mimikopi.core.knowledge.Knowledge
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.ScreenScaffold
import com.id.mimikopi.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bundled articles (content/knowledge), read once per process. */
private object Articles {
    @Volatile private var cache: List<Article>? = null
    fun all(app: Application): List<Article> = cache ?: app.assets.list("knowledge").orEmpty().filter { it.endsWith(".json") }
        .mapNotNull { name -> runCatching { app.assets.open("knowledge/$name").use { Article.parse(it.readBytes().toString(Charsets.UTF_8)) } }.getOrNull() }
        .sortedBy { it.title }.also { cache = it }
}

class KnowledgeViewModel(app: Application) : AndroidViewModel(app) {
    private val articles = Articles.all(app)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val results: StateFlow<List<Article>> = _query.map { Knowledge.search(articles, it) }.stateIn(viewModelScope, SharingStarted.Eagerly, articles)
    fun setQuery(q: String) { _query.value = q.take(60) }
}

/** One article; its examples play on the app's piano (one after another, or together for a chord). */
class ArticleViewModel(app: Application, args: SavedStateHandle) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    private val piano = services.piano
    val article: Article? = Articles.all(app).firstOrNull { it.id == args.get<String>("articleId") }
    private var holding = false
    private var job: Job? = null

    fun onStart() {
        if (holding) return
        viewModelScope.launch { holding = withContext(Dispatchers.IO) { piano.acquire(needPack = false) } }
    }

    fun onStop() {
        job?.cancel()
        if (!holding) return
        holding = false
        services.inputHub.releaseAll()
        viewModelScope.launch(Dispatchers.IO) { piano.release() }
    }

    fun play(example: String) {
        val steps = Knowledge.sequence(example) ?: return
        job?.cancel()
        job = viewModelScope.launch {
            for (step in steps) {
                val len = if (step.size > 1) 900L else 450L
                step.forEach { piano.noteOn(it, if (step.size > 1) 70 else 85) }
                delay(len)
                step.forEach { piano.noteOff(it) }
                delay(60)
            }
        }
    }

    override fun onCleared() = onStop()
}

@Composable
fun KnowledgeScreen(onBack: () -> Unit, onOpen: (String) -> Unit, vm: KnowledgeViewModel = viewModel()) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(R.string.knowledge_title), onBack = onBack) {
        OutlinedTextField(value = query, onValueChange = vm::setQuery, label = { Text(stringResource(R.string.knowledge_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (results.isEmpty()) Text(stringResource(R.string.knowledge_none), style = MaterialTheme.typography.bodyMedium)
        results.forEach { a ->
            PageCard(title = a.title, onClick = { onOpen(a.id) }) {
                Text(a.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ArticleScreen(onBack: () -> Unit, onOpenLesson: (String) -> Unit, vm: ArticleViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) { onDispose { vm.onStop() } }
    val a = vm.article
    ScreenScaffold(title = a?.title ?: "", onBack = onBack) {
        if (a == null) return@ScreenScaffold
        Text(a.summary, style = MaterialTheme.typography.bodyLarge)
        a.sections.forEach { sec ->
            PageCard(title = sec.heading.ifBlank { null }) {
                Text(sec.text, style = MaterialTheme.typography.bodyLarge)
                sec.examples.forEach { ex ->
                    OutlinedButton(onClick = { vm.play(ex.play) }, modifier = Modifier.padding(top = Tokens.Space.xs).heightIn(min = Tokens.minTouch)) {
                        Text(stringResource(R.string.knowledge_play, ex.label))
                    }
                }
            }
        }
        if (a.lessons.isNotEmpty()) PageCard(title = stringResource(R.string.knowledge_lessons)) {
            a.lessons.forEach { id -> TextButton(onClick = { onOpenLesson(id) }) { Text(stringResource(R.string.knowledge_lesson, id)) } }
        }
    }
}
