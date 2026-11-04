package io.github.jehanzebali11.scamradar.llm

import io.github.jehanzebali11.scamradar.core.classify.MessageClassifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClassifierHolderTest {
    /** Stands in for the LiteRT model: counts loads and closes. */
    private class CountingModel : MessageClassifier, AutoCloseable {
        var closed = false
        override val description = "counting"
        override val isRealModel = true

        override suspend fun classify(text: String) = "safe|none|none"

        override fun close() {
            closed = true
        }
    }

    private class Harness(scope: TestScope, idle: Long) {
        val models = mutableListOf<CountingModel>()
        val holder =
            ClassifierHolder(
                loader = { Loaded(CountingModel().also { models += it }, ModelState.Ready("CPU")) },
                idleMillis = idle,
                scope = scope.backgroundScope,
            )
    }

    @Test
    fun `nothing is loaded until a message is checked`() =
        runTest {
            val h = Harness(this, idle = 60_000)
            runCurrent()
            assertTrue(h.models.isEmpty())
            assertEquals(ModelState.Asleep, h.holder.state.value)
            h.holder.checker().check("hello")
            assertEquals(1, h.models.size)
            assertEquals(ModelState.Ready("CPU"), h.holder.state.value)
        }

    @Test
    fun `model is unloaded after the idle time and reloaded on the next message`() =
        runTest {
            val h = Harness(this, idle = 60_000)
            h.holder.checker().check("first")
            advanceTimeBy(59_000)
            runCurrent()
            assertTrue("still loaded before the idle time", !h.models[0].closed)
            h.holder.checker().check("second") // resets the timer, reuses the model
            assertEquals(1, h.models.size)
            advanceTimeBy(61_000)
            runCurrent()
            assertTrue(h.models[0].closed)
            assertEquals(ModelState.Asleep, h.holder.state.value)
            h.holder.checker().check("third")
            assertEquals(2, h.models.size)
        }
}
