package io.github.jehanzebali11.scamradar

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** The only things the app stores: two switches. Message text is never stored. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** In-app switch for the notification checker (separate from the Android permission). */
    var watchNotifications: Boolean
        get() = sp.getBoolean(KEY_WATCH, false)
        set(value) = sp.edit { putBoolean(KEY_WATCH, value) }

    /** Opt-in: show "Report this scam" on result cards. Off by default. */
    var reportEnabled: Boolean
        get() = sp.getBoolean(KEY_REPORT, false)
        set(value) = sp.edit { putBoolean(KEY_REPORT, value) }

    private companion object {
        const val KEY_WATCH = "watch_notifications"
        const val KEY_REPORT = "report_enabled"
    }
}
