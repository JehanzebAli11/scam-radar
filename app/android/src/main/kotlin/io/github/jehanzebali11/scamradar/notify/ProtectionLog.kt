package io.github.jehanzebali11.scamradar.notify

import io.github.jehanzebali11.scamradar.core.verdict.Severity
import io.github.jehanzebali11.scamradar.core.verdict.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What automatic protection has done since the app process started: a count
 * of checked messages and the most recent warnings. Memory only; nothing is
 * written to disk, so it is gone when Android stops the app.
 */
class ProtectionLog(private val keep: Int = 10) {
    data class Warning(val sender: String?, val text: String, val verdict: Verdict, val atMillis: Long)

    data class State(val checked: Int = 0, val warnings: List<Warning> = emptyList())

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun record(msg: IncomingMessage, verdict: Verdict, nowMillis: Long = System.currentTimeMillis()) {
        _state.update { s ->
            val warnings =
                if (verdict.severity == Severity.GREEN) {
                    s.warnings
                } else {
                    (listOf(Warning(msg.sender, msg.text, verdict, nowMillis)) + s.warnings).take(keep)
                }
            State(checked = s.checked + 1, warnings = warnings)
        }
    }
}
