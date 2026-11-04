package io.github.jehanzebali11.scamradar.core

import io.github.jehanzebali11.scamradar.core.classify.FakeClassifier
import io.github.jehanzebali11.scamradar.core.classify.MessageClassifier
import io.github.jehanzebali11.scamradar.core.text.AdviceText
import io.github.jehanzebali11.scamradar.core.text.ShareText
import io.github.jehanzebali11.scamradar.core.verdict.CautionReason
import io.github.jehanzebali11.scamradar.core.verdict.ScamChecker
import io.github.jehanzebali11.scamradar.core.verdict.Severity
import io.github.jehanzebali11.scamradar.core.verdict.Source
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Returns a fixed raw model output and records what it was asked. */
private class ScriptedClassifier(private val output: String?) : MessageClassifier {
    val seen = mutableListOf<String>()
    override val description = "scripted"
    override val isRealModel = true

    override suspend fun classify(text: String): String {
        seen += text
        return output ?: throw IllegalStateException("model crashed")
    }
}

class VerdictTest {
    @Test
    fun `scam answer is red with merged flags`() =
        runTest {
            val c = ScriptedClassifier("scam|fake_prize|asks_for_money")
            val v = ScamChecker(c).check("Aap ka inaam nikla! Claim: https://bit.ly/x")
            assertEquals(Severity.RED, v.severity)
            assertEquals(setOf(Flag.ASKS_FOR_MONEY, Flag.HAS_LINK), v.flags)
            assertTrue(v.cautions.isEmpty())
            assertEquals(Source.MODEL, v.source)
        }

    @Test
    fun `safe answer plus code request is amber`() =
        runTest {
            val v = ScamChecker(ScriptedClassifier("safe|none|none")).check("Bhai woh code mujhe bhej do jo abhi aaya")
            assertEquals(Severity.AMBER, v.severity)
            assertEquals(setOf(CautionReason.ASKS_FOR_CODE), v.cautions)
            assertTrue(Flag.ASKS_FOR_CODE in v.flags)
        }

    @Test
    fun `safe answer plus shortener link is amber`() =
        runTest {
            val v = ScamChecker(ScriptedClassifier("promo|none|has_link")).check("Big sale! https://bit.ly/sale")
            assertEquals(Severity.AMBER, v.severity)
            assertEquals(setOf(CautionReason.SUSPICIOUS_LINK), v.cautions)
        }

    @Test
    fun `unparseable or failed model output is amber, never green`() =
        runTest {
            for (out in listOf("I am not sure", "", null)) {
                val v = ScamChecker(ScriptedClassifier(out)).check("hello")
                assertEquals(Severity.AMBER, v.severity)
                assertEquals(setOf(CautionReason.MODEL_UNSURE), v.cautions)
                assertNull(v.prediction)
            }
        }

    @Test
    fun `lenient parse accepts messy but valid output`() =
        runTest {
            val v = ScamChecker(ScriptedClassifier(" Scam|Code_Theft|urgency,asks_for_code\n<end_of_turn>")).check("x")
            assertEquals(Severity.RED, v.severity)
            assertEquals("scam|code_theft|asks_for_code,urgency", v.prediction!!.toLine())
        }

    @Test
    fun `8171 allowlist skips the model`() =
        runTest {
            val c = ScriptedClassifier("scam|fake_govt_aid|none")
            val v = ScamChecker(c).check("8171: BISP ki qist jari. Koi fee nahi hai.", sender = "8171")
            assertEquals(Severity.GREEN, v.severity)
            assertEquals(Source.RULES, v.source)
            assertTrue(c.seen.isEmpty())
        }

    @Test
    fun `model gets the trimmed message, cut to max length`() =
        runTest {
            val c = ScriptedClassifier("safe|none|none")
            ScamChecker(c, maxChars = 10).check("   abcdefghijklmnop  ")
            assertEquals(listOf("abcdefghij"), c.seen)
        }

    @Test(expected = IllegalArgumentException::class)
    fun `empty message is rejected`() =
        runTest {
            ScamChecker(ScriptedClassifier("safe|none|none")).check("   ")
        }

    @Test
    fun `fake classifier always emits valid lines and catches seed scams`() =
        runTest {
            val fake = FakeClassifier()
            var scamsCaught = 0
            val seeds = TestFiles.seeds()
            for (seed in seeds) {
                val line = fake.classify(seed.text)
                assertTrue("$line for ${seed.id}", CompactLine.validate(line).isEmpty())
                if (seed.expected.startsWith("scam") && line.startsWith("scam")) scamsCaught++
            }
            assertTrue("fake classifier caught only $scamsCaught", scamsCaught >= 15)
            assertFalse(fake.isRealModel)
        }

    // --- pre-written text ----------------------------------------------

    @Test
    fun `every scam type flag and caution has text in both languages`() {
        val all =
            ScamType.entries.flatMap { listOf(AdviceText.SCAM_TYPE_TITLE.getValue(it), AdviceText.SCAM_TYPE_ADVICE.getValue(it)) } +
                Flag.entries.map { AdviceText.FLAG_CHIP.getValue(it) } +
                CautionReason.entries.map { AdviceText.CAUTION_ADVICE.getValue(it) }
        val urdu = Regex("[\\u0600-\\u06FF]")
        for (b in all) {
            assertTrue(b.en, b.en.isNotBlank() && !urdu.containsMatchIn(b.en))
            assertTrue(b.ur, urdu.containsMatchIn(b.ur))
        }
    }

    @Test
    fun `cards exist for every label and scam type`() =
        runTest {
            val lines = ScamType.entries.map { "scam|${it.value}|none" } + listOf("safe|none|none", "promo|none|none")
            for (line in lines) {
                val v = ScamChecker(ScriptedClassifier(line)).check("plain text")
                val card = AdviceText.card(v)
                assertEquals(v.severity, card.severity)
                assertTrue(card.advice.isNotEmpty())
            }
        }

    @Test
    fun `red card uses type title, never model text`() =
        runTest {
            val v = ScamChecker(ScriptedClassifier("scam|family_emergency|asks_for_money\nSend nothing, call your son!")).check("Ammi paise bhejo")
            val card = AdviceText.card(v)
            assertEquals(AdviceText.SCAM_TYPE_TITLE[ScamType.FAMILY_EMERGENCY], card.title)
            val shown = (listOf(card.headline) + card.advice + card.flagChips).joinToString { it.en + it.ur }
            assertFalse("call your son" in shown)
        }

    @Test
    fun `family share text is bilingual and quotes the message`() =
        runTest {
            val msg = "Mubarak! Inaam ke liye 3500 tax bhejein"
            val v = ScamChecker(ScriptedClassifier("scam|fake_prize|asks_for_money")).check(msg)
            val share = ShareText.forFamily(v, msg)
            assertTrue("Fake prize" in share)
            assertTrue("فراڈ" in share)
            assertTrue("\"$msg\"" in share)
            val report = ShareText.report(v, msg, sender = null)
            assertTrue("unknown" in report && msg in report)
        }
}
