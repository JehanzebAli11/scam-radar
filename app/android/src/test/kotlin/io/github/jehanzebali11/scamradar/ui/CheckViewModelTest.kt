package io.github.jehanzebali11.scamradar.ui

import io.github.jehanzebali11.scamradar.core.classify.FakeClassifier
import io.github.jehanzebali11.scamradar.core.classify.MessageClassifier
import io.github.jehanzebali11.scamradar.core.verdict.ScamChecker
import io.github.jehanzebali11.scamradar.core.verdict.Severity
import io.github.jehanzebali11.scamradar.core.verdict.Source
import io.github.jehanzebali11.scamradar.llm.CheckerProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class Recording(private val line: String) : MessageClassifier {
        val calls = mutableListOf<String>()
        override val description = "recording"
        override val isRealModel = true

        override suspend fun classify(text: String): String {
            calls += text
            return line
        }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(classifier: MessageClassifier) = CheckViewModel(CheckerProvider { ScamChecker(classifier) })

    @Test
    fun `pasted scam turns red with the fake classifier`() =
        runTest(dispatcher) {
            val vm = vm(FakeClassifier())
            vm.onInputChange("Assalam o alaikum, ghalti se mera code aap ke number par chala gaya. Please wapis bhej dein.")
            vm.check()
            assertTrue(vm.state.value.checking)
            advanceUntilIdle()
            val s = vm.state.value
            assertFalse(s.checking)
            assertEquals(Severity.RED, s.verdict!!.severity)
            assertFalse(s.verdict.usedRealModel)
        }

    @Test
    fun `blank input is not checked`() =
        runTest(dispatcher) {
            val c = Recording("safe|none|none")
            val vm = vm(c)
            vm.onInputChange("   ")
            vm.check()
            advanceUntilIdle()
            assertEquals("empty", vm.state.value.error)
            assertNull(vm.state.value.verdict)
            assertTrue(c.calls.isEmpty())
        }

    @Test
    fun `alert from 8171 keeps its sender and is allowlisted`() =
        runTest(dispatcher) {
            val c = Recording("scam|fake_govt_aid|none")
            val vm = vm(c)
            vm.checkIncoming("8171: BISP ki qist jari. Koi fee nahi hai.", sender = "8171")
            advanceUntilIdle()
            assertEquals(Source.RULES, vm.state.value.verdict!!.source)
            assertTrue(c.calls.isEmpty())
        }

    @Test
    fun `editing the text forgets the sender`() =
        runTest(dispatcher) {
            val vm = vm(Recording("safe|none|none"))
            vm.checkIncoming("8171: BISP", sender = "8171")
            advanceUntilIdle()
            vm.onInputChange("8171: BISP edited")
            assertNull(vm.state.value.sender)
        }

    @Test
    fun `clear resets everything`() =
        runTest(dispatcher) {
            val vm = vm(Recording("safe|none|none"))
            vm.checkIncoming("hello", null)
            advanceUntilIdle()
            vm.clear()
            assertEquals(CheckUiState(), vm.state.value)
        }
}
