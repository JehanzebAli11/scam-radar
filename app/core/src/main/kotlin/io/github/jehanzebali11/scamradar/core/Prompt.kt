package io.github.jehanzebali11.scamradar.core

/**
 * The single user turn the model was trained on (mirror of `scamradar/prompting.py`).
 * The chat template bundled in the .litertlm file wraps it in Gemma turn markers.
 * tests/test_schema_vectors.py checks this prefix matches USER_PREFIX.
 */
object Prompt {
    const val USER_PREFIX = "Classify this message:"

    fun userContent(text: String): String = "$USER_PREFIX\n$text"
}
