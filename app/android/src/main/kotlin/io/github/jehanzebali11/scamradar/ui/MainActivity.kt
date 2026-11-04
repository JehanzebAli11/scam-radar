package io.github.jehanzebali11.scamradar.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jehanzebali11.scamradar.ScamRadarApp
import io.github.jehanzebali11.scamradar.core.text.ShareText
import io.github.jehanzebali11.scamradar.notify.AlertNotifier
import io.github.jehanzebali11.scamradar.notify.ScamNotificationListener
import io.github.jehanzebali11.scamradar.share.ShareIntents

private enum class Screen { HOME, SETTINGS, DISCLOSURE }

class MainActivity : ComponentActivity() {
    private val app get() = application as ScamRadarApp
    private val vm: CheckViewModel by viewModels {
        viewModelFactory { initializer { CheckViewModel((application as ScamRadarApp).classifiers) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            ScamRadarTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.safeDrawingPadding()) { Root() }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** "Share to Scam Radar" and taps on our own scam alerts. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val text =
            when (intent.action) {
                Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ACTION_CHECK -> intent.getStringExtra(Intent.EXTRA_TEXT)
                else -> null
            }
        if (!text.isNullOrBlank()) {
            if (intent.action == ACTION_CHECK) {
                vm.checkIncoming(text, intent.getStringExtra(EXTRA_SENDER), Origin.ALERT)
            } else {
                vm.checkIncoming(text, null, Origin.SHARED)
            }
        }
    }

    private fun listenerGranted(): Boolean = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)

    private fun openListenerSettings() {
        val detail =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(this, ScamNotificationListener::class.java).flattenToString(),
                )
            } else {
                null
            }
        val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        try {
            startActivity(detail ?: fallback)
        } catch (e: Exception) {
            startActivity(fallback)
        }
    }

    @Composable
    private fun Root() {
        val state by vm.state.collectAsStateWithLifecycle()
        val modelState by app.classifiers.state.collectAsStateWithLifecycle()
        var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
        var showReport by remember { mutableStateOf(false) }
        var reportEnabled by remember { mutableStateOf(app.prefs.reportEnabled) }
        var watch by remember { mutableStateOf(app.prefs.watchNotifications) }
        // Bumped on resume so permission state is re-read after visiting Android settings.
        var resumeTick by remember { mutableIntStateOf(0) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }

        val notifPermission =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { openListenerSettings() }

        val protectionLog by app.protection.state.collectAsStateWithLifecycle()
        val protection =
            remember(resumeTick, watch, protectionLog) {
                ProtectionUi(on = watch && listenerGranted(), checked = protectionLog.checked, warnings = protectionLog.warnings)
            }
        val sendToFamily = {
            state.verdict?.let { v -> ShareIntents.shareText(this, ShareText.forFamily(v, state.checkedText.orEmpty()), "Send to family") }
            Unit
        }

        BackHandler(enabled = screen != Screen.HOME || state.showsResultPage) {
            if (state.showsResultPage) vm.clear() else screen = Screen.HOME
        }

        val resultVerdict = state.verdict
        if (state.showsResultPage && resultVerdict != null) {
            ResultScreen(
                verdict = resultVerdict,
                message = state.checkedText.orEmpty(),
                sender = state.sender,
                origin = state.origin,
                reportEnabled = reportEnabled,
                onSendToFamily = sendToFamily,
                onReport = { showReport = true },
                onDone = vm::clear,
            )
        } else when (screen) {
            Screen.HOME ->
                HomeScreen(
                    state = state,
                    modelState = modelState,
                    reportEnabled = reportEnabled,
                    onInputChange = vm::onInputChange,
                    onPaste = {
                        val clip = getSystemService(ClipboardManager::class.java).primaryClip
                        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
                        if (!text.isNullOrBlank()) vm.onInputChange(text)
                    },
                    onCheck = vm::check,
                    onClear = vm::clear,
                    onSendToFamily = sendToFamily,
                    onReport = { showReport = true },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    protection = protection,
                    onTurnOnProtection = { screen = Screen.DISCLOSURE },
                    onOpenWarning = { w -> vm.showWarning(w.sender, w.text, w.verdict) },
                )
            Screen.SETTINGS -> {
                val settings =
                    remember(resumeTick, watch, reportEnabled, modelState) {
                        SettingsState(modelState, listenerGranted(), watch, AlertNotifier.canPost(this), reportEnabled)
                    }
                SettingsScreen(
                    s = settings,
                    onBack = { screen = Screen.HOME },
                    onReloadModel = { app.classifiers.reload() },
                    onSetUpWatching = { screen = Screen.DISCLOSURE },
                    onWatchChange = {
                        app.prefs.watchNotifications = it
                        watch = it
                    },
                    onReportChange = {
                        app.prefs.reportEnabled = it
                        reportEnabled = it
                    },
                )
            }
            Screen.DISCLOSURE ->
                DisclosureScreen(
                    onAgree = {
                        app.prefs.watchNotifications = true
                        watch = true
                        screen = Screen.HOME
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !AlertNotifier.canPost(this)) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openListenerSettings()
                        }
                    },
                    onDecline = { screen = Screen.HOME },
                )
        }

        val v = state.verdict
        if (showReport && v != null) {
            ReportDialog(ShareText.report(v, state.checkedText.orEmpty(), state.sender)) { showReport = false }
        }
    }

    companion object {
        const val ACTION_CHECK = "io.github.jehanzebali11.scamradar.CHECK"
        const val EXTRA_SENDER = "io.github.jehanzebali11.scamradar.SENDER"
    }
}
