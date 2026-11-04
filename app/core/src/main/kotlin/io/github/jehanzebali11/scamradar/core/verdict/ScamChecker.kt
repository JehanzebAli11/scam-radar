package io.github.jehanzebali11.scamradar.core.verdict

import io.github.jehanzebali11.scamradar.core.CompactLine
import io.github.jehanzebali11.scamradar.core.classify.MessageClassifier
import io.github.jehanzebali11.scamradar.core.rules.RuleEngine
import kotlinx.coroutines.CancellationException

/**
 * The whole pipeline: rule layer first, then (unless the rules decided) the
 * classifier, then the lenient compact-line parser, then [VerdictCombiner].
 */
class ScamChecker(
    private val classifier: MessageClassifier,
    private val maxChars: Int = MAX_CHARS,
) {
    val usesRealModel: Boolean get() = classifier.isRealModel
    val classifierDescription: String get() = classifier.description

    suspend fun check(text: String, sender: String? = null): Verdict {
        val clean = text.trim().take(maxChars)
        require(clean.isNotEmpty()) { "empty message" }
        val rules = RuleEngine.run(clean, sender)
        if (rules.shortCircuit != null) return VerdictCombiner.fromRules(rules)

        val raw =
            try {
                classifier.classify(clean)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        val prediction = raw?.let { CompactLine.parseOrNull(it) }
        return VerdictCombiner.combine(rules, prediction, raw, classifier.isRealModel)
    }

    companion object {
        /** Training used max length 256 tokens; longer messages are cut. */
        const val MAX_CHARS = 1000
    }
}
