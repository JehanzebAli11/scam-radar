package io.github.jehanzebali11.scamradar.core.classify

/**
 * Anything that turns a message into the model's raw compact line
 * (`label|scam_type|flags`). The on-device LiteRT-LM model implements this in the
 * app module; [FakeClassifier] implements it for tests and demo mode.
 *
 * Implementations return the raw text; parsing and all advice happen outside,
 * so nothing the model writes beyond the compact line ever reaches the user.
 */
interface MessageClassifier {
    /** Shown in the UI and in logs, e.g. "on-device model" or "demo (keyword) mode". */
    val description: String

    /** False for the fake/demo classifier, so the UI can say results are a rough guess. */
    val isRealModel: Boolean

    suspend fun classify(text: String): String
}
