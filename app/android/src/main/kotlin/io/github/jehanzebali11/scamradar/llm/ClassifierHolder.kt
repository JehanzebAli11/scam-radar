package io.github.jehanzebali11.scamradar.llm

import android.content.Context
import io.github.jehanzebali11.scamradar.core.classify.FakeClassifier
import io.github.jehanzebali11.scamradar.core.classify.MessageClassifier
import io.github.jehanzebali11.scamradar.core.verdict.ScamChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

    /** The model was unloaded after a quiet spell; it loads again for the next message. */
    data object Asleep : ModelState

    data class Failed(val message: String) : ModelState
}

/** Something that hands out the current checker (real model or demo). */
fun interface CheckerProvider {
    suspend fun checker(): ScamChecker
}

/** A loaded classifier plus the state to show for it. */
data class Loaded(val classifier: MessageClassifier, val state: ModelState)

/**
 * Owns the single model instance for the whole process (UI and notification
 * listener share it), and keeps it loaded only while it is being used:
 *
 * - Nothing is loaded until a message needs checking (or the app's screen
 *   opens, see [warmUp]).
 * - After [idleMillis] with no checks the model is closed to free memory, and
 *   loaded again on the next message.
 *
 * Falls back to [FakeClassifier] when no model file exists or loading fails,
 * so the app is always usable.
 */
class ClassifierHolder(
    private val loader: () -> Loaded,
    private val idleMillis: Long = DEFAULT_IDLE_MILLIS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : CheckerProvider {
    constructor(context: Context) : this(loader = { loadFrom(context.applicationContext) })

    private val mutex = Mutex()

    @Volatile private var current: MessageClassifier? = null
    private var idleJob: Job? = null

    private val _state = MutableStateFlow<ModelState>(ModelState.Asleep)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    /** Load ahead of time, e.g. when the user opens the app to paste a message. */
    fun warmUp() {
        scope.launch {
            mutex.withLock { ensureLoaded() }
            scheduleRelease()
        }
    }

    /** Drop the loaded model and look for the file again (after an adb push). */
    fun reload() {
        scope.launch {
            mutex.withLock {
                unload()
                ensureLoaded()
            }
            scheduleRelease()
        }
    }

    override suspend fun checker(): ScamChecker = ScamChecker(onDemand)

    /** Loads the model for each check if needed, and restarts the idle timer afterwards. */
    private val onDemand =
        object : MessageClassifier {
            override val description: String get() = current?.description ?: "loads on demand"
            override val isRealModel: Boolean get() = current?.isRealModel ?: false

            override suspend fun classify(text: String): String =
                try {
                    mutex.withLock {
                        idleJob?.cancel()
                        ensureLoaded().classify(text)
                    }
                } finally {
                    scheduleRelease()
                }
        }

    private fun ensureLoaded(): MessageClassifier =
        current ?: run {
            _state.value = ModelState.Loading
            val loaded = loader()
            _state.value = loaded.state
            loaded.classifier.also { current = it }
        }

    private fun unload() {
        (current as? AutoCloseable)?.close()
        current = null
    }

    private fun scheduleRelease() {
        idleJob?.cancel()
        idleJob =
            scope.launch {
                delay(idleMillis)
                mutex.withLock {
                    val wasRealModel = current is AutoCloseable
                    unload()
                    // Demo mode keeps showing "Missing" so the banner stays honest.
                    if (wasRealModel) _state.value = ModelState.Asleep
                }
            }
    }

    companion object {
        /** Unload the model after this long without a message to check. */
        const val DEFAULT_IDLE_MILLIS = 5 * 60_000L

        private fun loadFrom(context: Context): Loaded {
            val file =
                ModelLocator.find(context)
                    ?: return Loaded(FakeClassifier(), ModelState.Missing(ModelLocator.candidates(context).map { it.absolutePath }))
            return try {
                val model = LiteRtClassifier.load(file.absolutePath, context.cacheDir.absolutePath)
                Loaded(model, ModelState.Ready(model.backendName))
            } catch (e: Throwable) {
                Loaded(FakeClassifier(), ModelState.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }
}
