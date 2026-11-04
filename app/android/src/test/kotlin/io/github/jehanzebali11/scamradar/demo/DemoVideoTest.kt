package io.github.jehanzebali11.scamradar.demo

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.service.notification.StatusBarNotification
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.github.jehanzebali11.scamradar.ScamRadarApp
import io.github.jehanzebali11.scamradar.notify.ScamNotificationListener
import io.github.jehanzebali11.scamradar.ui.MainActivity
import io.github.jehanzebali11.scamradar.ui.Navy
import io.github.jehanzebali11.scamradar.ui.RadarLogo
import io.github.jehanzebali11.scamradar.ui.ScamRadarTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Scripted walkthrough for the demo video, rendered on the JVM by Robolectric
 * (not an emulator), in demo mode (FakeClassifier, no model file).
 *
 * The automatic-protection story uses the real code path: fictional SMS and
 * WhatsApp notifications are posted to the real [ScamNotificationListener],
 * which checks them and posts Scam Radar's real warning notification; the
 * result page is opened from that warning's own tap intent. Android's
 * notification shade cannot be rendered here, so those moments are shown as a
 * clearly labelled illustration built from the real notification's text.
 *
 * Writes PNG frames plus captions.tsv for app/tools/make_demo_video.py.
 * Skipped unless -Dscamradar.demoFrames=<dir> is given.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DemoVideoTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val outDir: File? = System.getProperty("scamradar.demoFrames")?.let(::File)
    private val app get() = ApplicationProvider.getApplicationContext<Application>() as ScamRadarApp
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    @Before fun onlyWhenAsked() = assumeTrue("set -Dscamradar.demoFrames to record", outDir != null)

    // --- frame helpers ---------------------------------------------------

    /** Saves a frame. [tap] marks where a finger is about to press (drawn by make_demo_video.py). */
    private fun frame(name: String, seconds: Double, caption: String, tap: String? = null) {
        compose.waitForIdle()
        val dir = requireNotNull(outDir).also { it.mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val at =
            tap?.let {
                val b = node(it).fetchSemanticsNode().boundsInRoot
                "${b.center.x.toInt()},${b.center.y.toInt()}"
            }.orEmpty()
        File(dir, "captions.tsv").appendText("$name\t$seconds\t$caption\t$at\n")
    }

    private fun node(t: String) = compose.onNode(hasTestTag(t))

    private fun waitFor(t: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty() }

    private fun press(t: String, name: String, caption: String) {
        node(t).performScrollTo()
        frame(name, 0.8, caption, tap = t)
        node(t).performClick()
        compose.waitForIdle()
    }

    // --- notification helpers -------------------------------------------

    private val listener by lazy { Robolectric.setupService(ScamNotificationListener::class.java) }

    /** Posts a fictional incoming message notification from [pkg] to the real listener. */
    @Suppress("DEPRECATION")
    private fun incoming(pkg: String, sender: String, text: String, id: Int) {
        val n =
            NotificationCompat.Builder(app, "incoming")
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle(sender)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .build()
        val sbn = StatusBarNotification(pkg, pkg, id, null, 10_000 + id, 0, 0, n, Process.myUserHandle(), System.currentTimeMillis())
        listener.onNotificationPosted(sbn)
    }

    /** Lets the listener's background check finish; returns Scam Radar's own posted warnings. */
    private fun settle(expectChecked: Int): List<Notification> {
        repeat(400) {
            shadowOf(Looper.getMainLooper()).idle()
            if (app.protection.state.value.checked >= expectChecked) {
                shadowOf(Looper.getMainLooper()).idle()
                return notifications.allNotifications
            }
            Thread.sleep(25)
        }
        error("listener did not check $expectChecked messages")
    }

    private fun showIllustration(
        scenario: ActivityScenario<MainActivity>,
        app: String,
        sender: String,
        text: String,
        warning: Notification?,
    ) {
        scenario.onActivity { a ->
            a.setContent {
                ScamRadarTheme {
                    NotificationIllustration(
                        app,
                        sender,
                        text,
                        warning?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                        warning?.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                    )
                }
            }
        }
    }

    /** Opens the result page exactly the way tapping the warning does. */
    private fun openWarning(warning: Notification): ActivityScenario<MainActivity> {
        val intent = requireNotNull(shadowOf(warning.contentIntent).savedIntent)
        return ActivityScenario.launch(intent)
    }

    private fun grantNotificationAccess() {
        Settings.Secure.putString(
            app.contentResolver,
            "enabled_notification_listeners",
            ComponentName(app, ScamNotificationListener::class.java).flattenToString(),
        )
    }

    // --- the walkthrough --------------------------------------------------

    @Test
    fun walkthrough() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // 1. Turn on automatic protection.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            frame("a00_home", 3.5, "Scam Radar checks every new SMS and WhatsApp message on the phone. First, turn protection on.")
            press("turn-on-protection", "a01_tap_turn_on", "One big button: Turn on protection.")
            waitFor("disclosure")
            frame("a02_disclosure", 4.5, "A clear explanation first, in Urdu and English: what is read, and that nothing leaves the phone.")
            press("disclosure-agree", "a03_tap_agree", "Then Android asks for notification access.")
            grantNotificationAccess()
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitFor("protection-on")
            node("protection-on").performScrollTo()
            frame("a04_protection_on", 4.0, "Protection is on. Now nothing needs to be pasted: messages are checked as they arrive.")
        }

        // 2. A scam SMS arrives.
        val scam = "Assalam o alaikum, ghalti se mera 6 digit code aap ke number par chala gaya hai. Please woh code mujhe wapis bhej dein, bohat zaroori hai."
        incoming("com.google.android.apps.messaging", "0300-0000000", scam, 1)
        val firstWarning = settle(expectChecked = 1).single()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            showIllustration(scenario, "Messages", "0300-0000000", scam, firstWarning)
            frame("b00_sms_arrives", 6.0, "A scam SMS arrives. Within moments, Scam Radar posts a warning, before anyone replies.")
        }
        openWarning(firstWarning).use {
            waitFor("result-page")
            frame("b01_result_page", 5.0, "Tapping the warning opens a full-screen explanation: who sent it, what it said, what it is.")
            node("send-to-family").performScrollTo()
            frame("b02_result_steps", 4.5, "Plain steps in Urdu and English. All advice is pre-written, never invented by the model.")
            press("send-to-family", "b03_tap_family", "One tap to warn the family.")
            press("done", "b04_tap_done", "Done.")
        }

        // 3. Genuine messages stay quiet.
        val otp = "Your FictionBank OTP is 000000 for a transaction of Rs 5,000. Do not share this code with anyone. FictionBank will never ask for your OTP."
        incoming("com.google.android.apps.messaging", "FictionBank", otp, 2)
        assertEquals(1, settle(expectChecked = 2).size)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            showIllustration(scenario, "Messages", "FictionBank", otp, warning = null)
            frame("c00_otp_quiet", 4.5, "A genuine bank OTP that says 'do not share' gets no warning. No false alarms for normal messages.")
        }
        val bisp = "8171: BISP ki janib se aap ki qist jari kar di gayi hai. Raqam wasool karne ke liye apna asal CNIC le kar qareebi BISP campsite tashreef layen. Koi fee nahi hai."
        incoming("com.google.android.apps.messaging", "8171", bisp, 3)
        assertEquals(1, settle(expectChecked = 3).size)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            showIllustration(scenario, "Messages", "8171", bisp, warning = null)
            frame("c01_bisp_quiet", 4.5, "Real BISP messages come only from 8171. Scam Radar knows that and stays quiet.")
        }

        // 4. A WhatsApp scam.
        val family = "Ammi main Bilal, ye mera naya number hai. Mera accident ho gaya hai, hospital walay 15000 maang rahe hain. Jaldi is number par ZetaPay kar dein."
        incoming("com.whatsapp", "+92 300 0000000", family, 4)
        val warnings = settle(expectChecked = 4)
        assertEquals(2, warnings.size)
        val familyWarning = warnings.first { it.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.contains("family", ignoreCase = true) == true }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            showIllustration(scenario, "WhatsApp", "+92 300 0000000", family, familyWarning)
            frame("d00_whatsapp_arrives", 5.5, "WhatsApp too: a 'new number, family emergency' scam gets a warning.")
        }

        // 5. Home shows what protection has done.
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("warning-0")
            node("warning-0").performScrollTo()
            frame("e00_home_summary", 4.5, "The home screen shows what protection has checked and the recent warnings.")
            press("warning-0", "e01_tap_warning", "Any warning can be opened again.")
            waitFor("result-page")
            frame("e02_family_result", 5.0, "Red: fake family emergency. Call the person on their old number first.")
        }
    }
}

/**
 * Illustration of what reaches the phone: the incoming message and the
 * warning Scam Radar posted (title and text copied from the real notification).
 * Labelled as an illustration; it is not Android's notification shade.
 */
@Composable
private fun NotificationIllustration(app: String, sender: String, text: String, warningTitle: String?, warningText: String?) {
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B2A41), Color(0xFF0B1320)))).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Illustration: notification text produced by the app",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        Label("New message · $app")
        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFF4F6FA)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(sender, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(text, fontSize = 16.sp, lineHeight = 23.sp)
            }
        }
        if (warningTitle != null) {
            Label("Scam Radar's warning")
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().background(Color(0xFFC62828)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadarLogo(30.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Scam Radar", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(warningTitle, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Color(0xFFB71C1C))
                        warningText.orEmpty().lines().forEach { line ->
                            val urdu = line.any { it in '؀'..'ۿ' }
                            Text(
                                line,
                                fontSize = 16.sp,
                                lineHeight = if (urdu) 27.sp else 22.sp,
                                style = if (urdu) TextStyle(textDirection = TextDirection.Rtl, textAlign = TextAlign.Right) else TextStyle.Default,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        } else {
            Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF2E7D32), contentColor = Color.White) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("No warning from Scam Radar", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Checked on the phone. Looks genuine, so it stays quiet.", fontSize = 15.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Checked on the phone · offline", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.background(Navy.copy(alpha = 0f)))
    }
}

@Composable
private fun Label(text: String) = Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
