package io.github.jehanzebali11.scamradar.core.rules

import io.github.jehanzebali11.scamradar.core.Flag
import io.github.jehanzebali11.scamradar.core.Label
import io.github.jehanzebali11.scamradar.core.Prediction

/** What the rules found before the model runs. */
data class RuleReport(
    val links: List<Link>,
    val asksForCode: Boolean,
    val mentionsBisp: Boolean,
    /** The sender id is the official BISP short code 8171. */
    val fromOfficialBisp: Boolean,
    /** Mentions BISP, the sender is known, and it is not 8171. */
    val bispFromUnofficialSender: Boolean,
    /** When set, the rules decided and the model is skipped. */
    val shortCircuit: Prediction?,
) {
    /** Flags the rules can vouch for, merged into the model's flags. */
    val flags: Set<Flag>
        get() =
            buildSet {
                if (links.isNotEmpty()) add(Flag.HAS_LINK)
                if (asksForCode) add(Flag.ASKS_FOR_CODE)
            }

    val hasSuspiciousLink: Boolean get() = links.any { it.isSuspicious }
}

/**
 * Deterministic checks run before the model.
 *
 * Only one rule short-circuits: a message whose sender id is exactly 8171 and
 * that has no link and no request for a code is treated as genuine BISP. A
 * message from 8171 that does carry a link or asks for a code still goes to the
 * model (sender ids can be spoofed).
 */
object RuleEngine {
    fun run(text: String, sender: String? = null): RuleReport {
        val links = LinkExtractor.extract(text)
        val asksForCode = CodeRequestDetector.asksForCode(text)
        val mentionsBisp = BispAllowlist.mentionsBisp(text)
        val official = BispAllowlist.isOfficialSender(sender)
        val unofficial = mentionsBisp && !sender.isNullOrBlank() && !official
        val shortCircuit =
            if (official && links.isEmpty() && !asksForCode) {
                Prediction(Label.SAFE, null, setOf(Flag.CLAIMS_OFFICIAL))
            } else {
                null
            }
        return RuleReport(links, asksForCode, mentionsBisp, official, unofficial, shortCircuit)
    }
}
