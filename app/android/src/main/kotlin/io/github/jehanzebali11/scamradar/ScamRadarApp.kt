package io.github.jehanzebali11.scamradar

import android.app.Application
import io.github.jehanzebali11.scamradar.llm.ClassifierHolder
import io.github.jehanzebali11.scamradar.notify.AlertNotifier
import io.github.jehanzebali11.scamradar.notify.ProtectionLog

class ScamRadarApp : Application() {
    lateinit var classifiers: ClassifierHolder
        private set
    lateinit var prefs: Prefs
        private set
    val protection = ProtectionLog()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        classifiers = ClassifierHolder(this)
        // No warm-up here: when Android starts the process for the notification
        // listener, the model should load only once a message needs checking.
        AlertNotifier.createChannel(this)
    }
}
