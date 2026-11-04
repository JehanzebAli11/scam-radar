package io.github.jehanzebali11.scamradar.core

/**
 * Kotlin mirror of `scamradar/schema.py`: the compact line `label|scam_type|flags`
 * the model emits, e.g. `scam|code_theft|asks_for_code,urgency` or `safe|none|none`.
 *
 * Keep this file in lockstep with schema.py. `tests/fixtures/schema_vectors.jsonl`
 * is generated from schema.py and checked here (SchemaVectorsTest) and in pytest
 * (tests/test_schema_vectors.py, which also checks the enum values below).
 */
const val NONE = "none"
private const val SEP = "|"
private const val FLAG_SEP = ","

enum class Label(val value: String) {
    SAFE("safe"),
    PROMO("promo"),
    SCAM("scam"),
    ;

    companion object {
        fun of(value: String): Label? = entries.firstOrNull { it.value == value }
    }
}

enum class ScamType(val value: String) {
    FAKE_GOVT_AID("fake_govt_aid"),
    FAKE_PRIZE("fake_prize"),
    CODE_THEFT("code_theft"),
    BANK_OR_WALLET_IMPERSONATION("bank_or_wallet_impersonation"),
    FAMILY_EMERGENCY("family_emergency"),
    FAKE_JOB_OR_INVESTMENT("fake_job_or_investment"),
    PHISHING_LINK("phishing_link"),
    OTHER("other"),
    ;

    companion object {
        fun of(value: String): ScamType? = entries.firstOrNull { it.value == value }
    }
}

/** Declaration order is the canonical flag order (same as FLAG_ORDER in schema.py). */
enum class Flag(val value: String) {
    ASKS_FOR_CODE("asks_for_code"),
    ASKS_FOR_MONEY("asks_for_money"),
    HAS_LINK("has_link"),
    URGENCY("urgency"),
    CLAIMS_OFFICIAL("claims_official"),
    ;

    companion object {
        fun of(value: String): Flag? = entries.firstOrNull { it.value == value }
    }
}

class SchemaException(val errors: List<String>) : IllegalArgumentException(errors.joinToString("; "))

data class Prediction(
    val label: Label,
    val scamType: ScamType? = null,
    val flags: Set<Flag> = emptySet(),
) {
    init {
        require((label == Label.SCAM) == (scamType != null)) {
            "scam_type must be set iff label is scam"
        }
    }

    val isScam: Boolean get() = label == Label.SCAM

    /** Flags in canonical order. */
    val orderedFlags: List<Flag> get() = flags.sortedBy { it.ordinal }

    fun toLine(): String = CompactLine.format(this)
}

object CompactLine {
    /** Strict validation, same rules as `validate_line`. Empty list means valid. */
    fun validate(line: String): List<String> {
        val errors = mutableListOf<String>()
        if (line != line.trim()) errors += "leading/trailing whitespace"
        val parts = line.trim().split(SEP)
        if (parts.size != 3) {
            return errors + "expected 3 fields separated by '|', got ${parts.size}"
        }
        val (label, scamType, flags) = parts
        val knownLabel = Label.of(label)
        if (knownLabel == null) errors += "unknown label '$label'"
        if (knownLabel == Label.SCAM) {
            if (scamType == NONE) {
                errors += "label 'scam' requires a scam_type"
            } else if (ScamType.of(scamType) == null) {
                errors += "unknown scam_type '$scamType'"
            }
        } else if (knownLabel != null && scamType != NONE) {
            errors += "scam_type must be 'none' when label is '$label'"
        }

        if (flags.isEmpty()) {
            errors += "flags field is empty (use 'none')"
        } else if (flags != NONE) {
            val items = flags.split(FLAG_SEP)
            if (NONE in items) errors += "'none' cannot be combined with other flags"
            val unknown = items.filter { Flag.of(it) == null && it != NONE }
            if (unknown.isNotEmpty()) errors += "unknown flags $unknown"
            val hasDuplicates = items.toSet().size != items.size
            if (hasDuplicates) errors += "duplicate flags"
            val known = items.mapNotNull { Flag.of(it) }
            if (unknown.isEmpty() && !hasDuplicates && known != known.sortedBy { it.ordinal }) {
                errors += "flags not in canonical order ${Flag.entries.map { it.value }}"
            }
        }
        return errors
    }

    /**
     * Parse a compact line, same behaviour as `parse_line`.
     *
     * With [strict] any deviation throws. Lenient mode (what the app uses on model
     * output) tolerates whitespace, case, backticks, text after the first line and
     * flag order/duplicates, but still rejects unknown values.
     */
    fun parse(line: String, strict: Boolean = true): Prediction {
        val text: String
        if (strict) {
            val errors = validate(line)
            if (errors.isNotEmpty()) throw SchemaException(errors)
            text = line
        } else {
            val stripped = line.trim().trim('`').trim()
            val first = if (stripped.isEmpty()) "" else stripped.lines().first().trim().lowercase()
            text = first.split(SEP).joinToString(SEP) { it.trim() }
        }
        val parts = text.split(SEP)
        if (parts.size != 3) {
            throw SchemaException(listOf("expected 3 fields separated by '|', got ${parts.size}"))
        }
        val (labelS, typeS, flagsS) = parts
        val label = Label.of(labelS) ?: throw SchemaException(listOf("unknown label '$labelS'"))
        var scamType =
            if (typeS == NONE) null else ScamType.of(typeS) ?: throw SchemaException(listOf("unknown scam_type '$typeS'"))
        val rawFlags =
            if (flagsS == NONE || flagsS.isEmpty()) emptyList() else flagsS.split(FLAG_SEP).map { it.trim() }
        val flags =
            rawFlags.filter { it.isNotEmpty() }.map {
                Flag.of(it) ?: throw SchemaException(listOf("unknown flag '$it'"))
            }.toSet()
        if (label == Label.SCAM && scamType == null) {
            throw SchemaException(listOf("label 'scam' requires a scam_type"))
        }
        if (label != Label.SCAM && scamType != null) {
            if (strict) throw SchemaException(listOf("scam_type must be 'none' when label is '$labelS'"))
            scamType = null
        }
        return Prediction(label, scamType, flags)
    }

    /** Lenient parse that returns null instead of throwing. */
    fun parseOrNull(line: String): Prediction? =
        try {
            parse(line, strict = false)
        } catch (e: SchemaException) {
            null
        }

    fun format(pred: Prediction): String {
        val type = pred.scamType?.value ?: NONE
        val flags = if (pred.flags.isEmpty()) NONE else pred.orderedFlags.joinToString(FLAG_SEP) { it.value }
        return listOf(pred.label.value, type, flags).joinToString(SEP)
    }
}
