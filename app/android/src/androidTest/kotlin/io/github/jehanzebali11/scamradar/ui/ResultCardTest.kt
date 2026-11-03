package io.github.jehanzebali11.scamradar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jehanzebali11.scamradar.core.classify.MessageClassifier
import io.github.jehanzebali11.scamradar.core.verdict.ScamChecker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented (needs a device or emulator). UNVERIFIED: compiled in CI-less sandbox, not run. */
@RunWith(AndroidJUnit4::class)
class ResultCardTest {
    @get:Rule val compose = createComposeRule()

    private fun verdict(line: String, text: String = "test message") =
        runBlocking {
            ScamChecker(
                object : MessageClassifier {
                    override val description = "scripted"
                    override val isRealModel = true

                    override suspend fun classify(text: String) = line
                },
            ).check(text)
        }

    @Test
    fun redCardShowsTypeTitleAndButtons() {
        var shared = 0
        compose.setContent { ResultCard(verdict("scam|fake_prize|asks_for_money"), onSendToFamily = { shared++ }, onReport = {}) }
        compose.onNodeWithTag("result-red").assertIsDisplayed()
        compose.onNodeWithText("Fake prize or lucky draw").assertIsDisplayed()
        compose.onNodeWithTag("report").assertIsDisplayed()
        compose.onNodeWithTag("send-to-family").performClick()
        assertEquals(1, shared)
    }

    @Test
    fun greenCardHasNoReportButton() {
        compose.setContent { ResultCard(verdict("safe|none|none"), onSendToFamily = {}, onReport = {}) }
        compose.onNodeWithTag("result-green").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTagCount("report") == 0)
    }

    @Test
    fun disclosureComesWithAgreeAndDecline() {
        var agreed = false
        compose.setContent { DisclosureScreen(onAgree = { agreed = true }, onDecline = {}) }
        compose.onNodeWithTag("disclosure-decline").assertIsDisplayed()
        compose.onNodeWithTag("disclosure-agree").performClick()
        assertTrue(agreed)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
