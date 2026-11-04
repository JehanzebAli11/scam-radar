package io.github.jehanzebali11.scamradar.llm

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import io.github.jehanzebali11.scamradar.BuildConfig
import io.github.jehanzebali11.scamradar.core.Prompt
import io.github.jehanzebali11.scamradar.core.classify.MessageClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The fine-tuned Gemma 3 270M running on-device through LiteRT-LM.
 *
 * Follows the LiteRT-LM Kotlin sample approach: build [EngineConfig] with named
 * arguments, call `initialize()` off the main thread, fall back to CPU if the
 * preferred backend fails, and talk to the model through a [Conversation].
 * Each message gets a fresh conversation so no history leaks between checks,
 * and the prompt is exactly the single training turn `Classify this message:\n<text>`.
 */
class LiteRtClassifier private constructor(
    private val engine: Engine,
    val backendName: String,
) : MessageClassifier, AutoCloseable {
    override val description: String = "on-device model ($backendName)"
    override val isRealModel: Boolean = true

    private val mutex = Mutex()

    override suspend fun classify(text: String): String =
        mutex.withLock {
            withContext(Dispatchers.Default) {
                engine.createConversation(CONVERSATION_CONFIG).use { conversation ->
                    conversation.sendMessage(Contents.of(Prompt.userContent(text)), maxOutputToken = MAX_OUTPUT_TOKENS).toString()
                }
            }
        }

    override fun close() = engine.close()

    companion object {
        private const val TAG = "LiteRtClassifier"

        /**
         * KV-cache size (input + output tokens). A message plus its answer is under
         * ~300 tokens (training used max length 256), so 512 is plenty; without it
         * LiteRT-LM asks for 4096. If the .litertlm was exported with a smaller
         * fixed cache, the engine uses that instead. 0 = engine/model default.
         * Set per build for the memory experiment (see export/README.md).
         */
        val MAX_NUM_TOKENS: Int? = BuildConfig.MODEL_MAX_TOKENS.takeIf { it > 0 }

        /** The longest compact line is well under this many tokens. */
        const val MAX_OUTPUT_TOKENS = 48

        /**
         * Greedy decoding (topK = 1): the answer is one short deterministic line.
         * temperature is irrelevant with topK = 1 and is left at 1.0 to avoid a
         * divide-by-zero in samplers that do not special-case 0.
         */
        private val CONVERSATION_CONFIG =
            ConversationConfig(
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 1.0),
            )

        /** Blocking; call from a background dispatcher. */
        fun load(modelPath: String, cacheDir: String, preferGpu: Boolean = false): LiteRtClassifier {
            val attempts =
                buildList {
                    if (preferGpu) add("GPU" to { Backend.GPU() as Backend })
                    add("CPU" to { Backend.CPU() as Backend })
                }
            var last: Throwable? = null
            for ((name, backend) in attempts) {
                val engine = Engine(
                        EngineConfig(modelPath = modelPath, backend = backend(), cacheDir = cacheDir, maxNumTokens = MAX_NUM_TOKENS),
                    )
                try {
                    engine.initialize()
                    Log.i(TAG, "model loaded on $name")
                    return LiteRtClassifier(engine, name)
                } catch (e: Exception) {
                    Log.w(TAG, "init on $name failed, trying next backend", e)
                    runCatching { engine.close() }
                    last = e
                }
            }
            throw IllegalStateException("could not load model at $modelPath", last)
        }
    }
}
