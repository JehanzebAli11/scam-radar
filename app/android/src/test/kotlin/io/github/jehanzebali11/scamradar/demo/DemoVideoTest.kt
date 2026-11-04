package io.github.jehanzebali11.scamradar.demo

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jehanzebali11.scamradar.ui.ScamRadarTheme
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.github.jehanzebali11.scamradar.ui.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Scripted walkthrough of the real app UI, rendered on the JVM by Robolectric
 * (not an emulator), in demo mode (FakeClassifier, no model file). Writes PNG
 * frames plus captions.tsv for app/tools/make_demo_video.sh.
 *
 * Skipped unless -Dscamradar.demoFrames=<dir> is given, so normal test runs
 * are not slowed down.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DemoVideoTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val outDir: File? = System.getProperty("scamradar.demoFrames")?.let(::File)

    @Before fun onlyWhenAsked() = assumeTrue("set -Dscamradar.demoFrames to record", outDir != null)

    private fun frame(name: String, seconds: Double, caption: String) {
        compose.waitForIdle()
        val dir = requireNotNull(outDir).also { it.mkdirs() }
        fun save(file: String, bmp: Bitmap) = File(dir, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        save("$name.png", compose.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap())
        File(dir, "captions.tsv").appendText("$name\t$seconds\t$caption\n")
    }

    private fun tag(t: String) = compose.onNode(hasTestTag(t), useUnmergedTree = false)

    private fun waitFor(t: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty() }

    private fun typeAndCheck(prefix: String, text: String, result: String, captionTyping: String, captionResult: String) {
        tag("input").performTextReplacement("")
        val steps = 6
        for (i in 1..steps) {
            tag("input").performTextReplacement(text.take(text.length * i / steps))
            frame("${prefix}_type$i", if (i == steps) 1.5 else 0.25, captionTyping)
        }
        tag("check").performClick()
        waitFor(result)
        tag(result).performScrollTo()
        frame("${prefix}_result", 4.5, captionResult)
    }

    private fun launch(intent: Intent? = null): ActivityScenario<MainActivity> =
        if (intent == null) ActivityScenario.launch(MainActivity::class.java) else ActivityScenario.launch(intent)

    @Test
    fun a_pasteBox() {
        launch().use {
            frame("a00_home", 3.0, "Scam Radar opens. No model file pushed yet, so it runs in demo mode (keyword stand-in).")
            typeAndCheck(
                "a01", "Assalam o alaikum bhai, ghalti se mera 6 digit code aap ke number par chala gaya hai. Please woh code mujhe wapis bhej dein, bohat zaroori hai.",
                "result-red", "Paste a message: the classic 'my code went to your number' scam (Roman Urdu).",
                "Red card. All advice is pre-written Urdu + English for this scam type, never model text.",
            )
            typeAndCheck(
                "a02", "Your FictionBank OTP is 000000 for a transaction of Rs 5,000. Do not share this code with anyone. FictionBank will never ask for your OTP.",
                "result-green", "A genuine bank OTP that says 'do not share'.",
                "Green: the 'asks for a code' rule ignores 'do not share' messages.",
            )
            typeAndCheck(
                "a03", "احساس پروگرام: آپ کی 12000 روپے کی امداد منظور ہو گئی ہے۔ فوری طور پر اپنا شناختی کارڈ نمبر اور 500 روپے فیس اس نمبر پر بھیجیں 0300-0000000",
                "result-red", "Urdu script: fake government aid asking for a fee.",
                "Red: fake government aid. Real BISP messages come only from 8171.",
            )
            typeAndCheck(
                "a04", "NovaTel offer! 5GB weekly bundle sirf Rs 99. Details: https://bit.ly/nt-offer",
                "result-amber", "An advert with a shortened link.",
                "Amber: looks like a promo, but the link extraction rule flags the bit.ly shortener.",
            )
        }
    }

    @Test
    fun b_familyAndReport() {
        launch().use { scenario ->
            typeAndCheck(
                "b01", "Ammi main Bilal, ye mera naya number hai. Mera accident ho gaya hai, hospital walay 15000 maang rahe hain. Jaldi is number par ZetaPay kar dein.",
                "result-red", "A 'new number, family emergency' scam.",
                "Red: fake family emergency.",
            )
            tag("send-to-family").performClick()
            compose.waitForIdle()
            scenario.onActivity {
                val chooser = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
                @Suppress("DEPRECATION")
                val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                val text = send?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                File(requireNotNull(outDir), "b02_share.txt").writeText(text)
            }
            tag("open-settings").performScrollTo().performClick()
            frame("b03_settings", 3.0, "Settings: 'Report this scam' is opt-in and off by default.")
            tag("report-switch").performClick()
            frame("b04_settings_on", 2.0, "Turn it on.")
            compose.onNode(androidx.compose.ui.test.hasText("Back")).performClick()
            waitFor("report")
            tag("report").performScrollTo()
            frame("b05_report_button", 2.5, "Red cards now show 'Report this scam'.")
            tag("report").performClick()
            frame("b06_report_dialog", 5.0, "It only prepares the report. Nothing is sent unless you send it yourself.")
        }
    }

    @Test
    fun c_disclosure() {
        launch().use {
            tag("open-settings").performClick()
            tag("setup-watching").performClick()
            waitFor("disclosure")
            frame("c01_disclosure", 5.0, "Before asking for notification access: a disclosure of what is read and what happens to it.")
            tag("disclosure-decline").performScrollTo()
            frame("c02_disclosure_end", 4.0, "Messages are checked on the phone, never stored or uploaded. 'Continue' then opens Android settings.")
        }
    }

    @Test
    fun d_shareIntent() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .setAction(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Your parcel could not be delivered due to an incomplete address. Update your details here: http://parcel-redelivery.example/pk")
        launch(intent).use {
            waitFor("result-red")
            frame("d01_shared", 3.0, "'Share to Scam Radar' from SMS or WhatsApp: the app opens and checks straight away.")
            tag("result-red").performScrollTo()
            frame("d02_shared_result", 4.0, "Red: dangerous link.")
        }
    }

    /** Renders the text that "Send to family" handed to the share sheet (captured in b_). */
    @Test
    fun e_shareTextPreview() {
        val text = File(requireNotNull(outDir), "b02_share.txt").readText()
        launch().use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent { ScamRadarTheme { SharePreview(text) } }
            }
            frame("b02_share", 6.0, "'Send to family' opens Android's share sheet with this pre-written bilingual warning. You pick who gets it.")
        }
    }
}

@Composable
private fun SharePreview(text: String) {
    val rtl = Regex("[\\u0600-\\u06FF]")
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Text handed to the share sheet", style = MaterialTheme.typography.titleLarge)
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                text.split("\n\n").forEach { para ->
                    val isUrdu = rtl.containsMatchIn(para) && !para.startsWith("\"")
                    Text(
                        para,
                        fontSize = 18.sp,
                        lineHeight = 28.sp,
                        style = if (isUrdu) TextStyle(textDirection = TextDirection.Rtl, textAlign = TextAlign.Right) else TextStyle.Default,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
