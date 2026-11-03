package io.github.jehanzebali11.scamradar

import android.app.Application
import io.github.jehanzebali11.scamradar.llm.ClassifierHolder
import io.github.jehanzebali11.scamradar.notify.AlertNotifier

class ScamRadarApp : Application() {
    lateinit var classifiers: ClassifierHolder
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        classifiers = ClassifierHolder(this)
        classifiers.warmUp()
        AlertNotifier.createChannel(this)
    }
}
