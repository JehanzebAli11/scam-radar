package io.github.jehanzebali11.scamradar.llm

import android.content.Context
import io.github.jehanzebali11.scamradar.core.classify.FakeClassifier
import io.github.jehanzebali11.scamradar.core.classify.MessageClassifier
import io.github.jehanzebali11.scamradar.core.verdict.ScamChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ModelState {
    data object Loading : ModelState

    /** No model file yet: the app runs the fake classifier in demo mode. */
    data class Missing(val lookedIn: List<String>) : ModelState

    data class Ready(val backend: String) : ModelState

    data class Failed(val message: String) : ModelState
}

/** Something that hands out the current checker (real model or demo). */
fun interface CheckerProvider {
    suspend fun checker(): ScamChecker
}

/**
 * Owns the single model instance for the whole process (UI and notification
 * listener share it). Falls back to [FakeClassifier] when no model file exists
 * or loading fails, so the app is always usable.
 */
class ClassifierHolder(private val context: Context) : CheckerProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var current: MessageClassifier? = null

    private val _state = MutableStateFlow<ModelState>(ModelState.Loading)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    fun warmUp() {
        scope.launch { checker() }
    }

    /** Drop the loaded model and look for the file again (after an adb push). */
    fun reload() {
        scope.launch {
            mutex.withLock {
                (current as? AutoCloseable)?.close()
                current = null
                _state.value = ModelState.Loading
            }
            checker()
        }
    }

    override suspend fun checker(): ScamChecker = ScamChecker(classifier())

    private suspend fun classifier(): MessageClassifier =
        mutex.withLock {
            current ?: load().also { current = it }
        }

    private fun load(): MessageClassifier {
        val file = ModelLocator.find(context)
        if (file == null) {
            _state.value = ModelState.Missing(ModelLocator.candidates(context).map { it.absolutePath })
            return FakeClassifier()
        }
        return try {
            LiteRtClassifier.load(file.absolutePath, context.cacheDir.absolutePath).also {
                _state.value = ModelState.Ready(it.backendName)
            }
        } catch (e: Throwable) {
            _state.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
            FakeClassifier()
        }
    }
}
