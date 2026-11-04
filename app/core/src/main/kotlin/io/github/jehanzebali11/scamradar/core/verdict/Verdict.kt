package io.github.jehanzebali11.scamradar.core.verdict

import io.github.jehanzebali11.scamradar.core.Flag
import io.github.jehanzebali11.scamradar.core.Label
import io.github.jehanzebali11.scamradar.core.Prediction
import io.github.jehanzebali11.scamradar.core.rules.Link
import io.github.jehanzebali11.scamradar.core.rules.RuleReport

enum class Severity { GREEN, AMBER, RED }

/** Why a non-scam answer was still turned amber. */
enum class CautionReason {
    /** The rule layer saw a request for an OTP/PIN/code. */
    ASKS_FOR_CODE,

    /** A shortener, IP, punycode or risky-TLD link. */
    SUSPICIOUS_LINK,

    /** Mentions BISP but the (known) sender is not 8171. */
    BISP_NOT_OFFICIAL,

    /** The model's output could not be parsed, or the model failed. */
    MODEL_UNSURE,
}

enum class Source {
    /** The rule layer decided (BISP 8171 allowlist); the model was not run. */
    RULES,

    /** The on-device model (or the fake classifier in demo mode). */
    MODEL,
}

data class Verdict(
    val severity: Severity,
    /** Null only when the model output could not be parsed. */
    val prediction: Prediction?,
    /** Model flags merged with rule flags. */
    val flags: Set<Flag>,
    val cautions: Set<CautionReason>,
    val links: List<Link>,
    val source: Source,
    val rules: RuleReport,
    /** The model's raw output, for the debug line in the UI. Never shown as advice. */
    val rawModelOutput: String? = null,
    val usedRealModel: Boolean = true,
) {
    val label: Label? get() = prediction?.label
    val orderedFlags: List<Flag> get() = flags.sortedBy { it.ordinal }
}

/** Rules + parsed model answer -> traffic-light verdict. Pure and unit-tested. */
object VerdictCombiner {
    fun fromRules(rules: RuleReport): Verdict {
        val pred = requireNotNull(rules.shortCircuit) { "rules did not short-circuit" }
        return Verdict(
            severity = Severity.GREEN,
            prediction = pred,
            flags = pred.flags + rules.flags,
            cautions = emptySet(),
            links = rules.links,
            source = Source.RULES,
            rules = rules,
        )
    }

    fun combine(
        rules: RuleReport,
        prediction: Prediction?,
        rawModelOutput: String?,
        usedRealModel: Boolean,
    ): Verdict {
        val cautions = mutableSetOf<CautionReason>()
        if (prediction == null) cautions += CautionReason.MODEL_UNSURE
        if (rules.asksForCode) cautions += CautionReason.ASKS_FOR_CODE
        if (rules.hasSuspiciousLink) cautions += CautionReason.SUSPICIOUS_LINK
        if (rules.bispFromUnofficialSender) cautions += CautionReason.BISP_NOT_OFFICIAL

        val severity =
            when {
                prediction?.isScam == true -> Severity.RED
                cautions.isNotEmpty() -> Severity.AMBER
                else -> Severity.GREEN
            }
        return Verdict(
            severity = severity,
            prediction = prediction,
            flags = (prediction?.flags ?: emptySet()) + rules.flags,
            cautions = if (severity == Severity.RED) emptySet() else cautions,
            links = rules.links,
            source = Source.MODEL,
            rules = rules,
            rawModelOutput = rawModelOutput,
            usedRealModel = usedRealModel,
        )
    }
}
