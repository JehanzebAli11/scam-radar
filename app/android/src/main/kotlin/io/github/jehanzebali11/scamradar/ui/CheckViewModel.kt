package io.github.jehanzebali11.scamradar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jehanzebali11.scamradar.core.verdict.Verdict
import io.github.jehanzebali11.scamradar.llm.CheckerProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CheckUiState(
    val input: String = "",
    /** Known only for messages that came from a notification; pasted text has no sender. */
    val sender: String? = null,
    val checking: Boolean = false,
    val verdict: Verdict? = null,
    /** The text the current [verdict] is about (input may have been edited since). */
    val checkedText: String? = null,
    val error: String? = null,
)

class CheckViewModel(private val provider: CheckerProvider) : ViewModel() {
    private val _state = MutableStateFlow(CheckUiState())
    val state: StateFlow<CheckUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onInputChange(text: String) {
        // Edited or pasted text no longer comes from a known sender.
        _state.update { it.copy(input = text, sender = null, error = null) }
    }

    /** From the share sheet or a scam alert: fill the box and check right away. */
    fun checkIncoming(text: String, sender: String?) {
        _state.update { CheckUiState(input = text, sender = sender) }
        check()
    }

    fun check() {
        val s = _state.value
        val text = s.input.trim()
        if (text.isEmpty()) {
            _state.update { it.copy(error = "empty") }
            return
        }
        job?.cancel()
        _state.update { it.copy(checking = true, error = null) }
        job =
            viewModelScope.launch {
                val verdict = provider.checker().check(text, s.sender)
                _state.update { it.copy(checking = false, verdict = verdict, checkedText = text) }
            }
    }

    fun clear() {
        job?.cancel()
        _state.value = CheckUiState()
    }
}
