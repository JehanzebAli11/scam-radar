package io.github.jehanzebali11.scamradar.core.rules

/**
 * "Asks for a code" detector: does the message ask the reader to send, tell,
 * forward or reply with a one-time code / OTP / PIN / password?
 *
 * Genuine OTP messages say the opposite ("do not share this code"), and courier
 * messages ask you to show a code to the rider at the door; both are excluded.
 * Works per sentence in English, Roman Urdu and Urdu script. This is a rule, not
 * a classifier: it raises the `asks_for_code` flag and can turn a "safe" model
 * answer amber, never red on its own.
 */
object CodeRequestDetector {
    /** Urdu-script word: not preceded by a letter; [prefix] allows suffixes (verb stems). */
    private fun ur(vararg words: String, prefix: Boolean = false): String =
        words.joinToString("|") { w -> "(?<!\\p{L})$w" + if (prefix) "" else "(?!\\p{L})" }

    private val CODE_WORDS =
        Regex(
            """(?i)\b(?:otp|o\.t\.p|pin|code|codes|password|passcode|verification\s+code|tpin|mpin|cvv)\b|""" +
                ur("کوڈ", "پن", "او\\s?ٹی\\s?پی", "پاس\\s?ورڈ"),
        )

    private val REQUEST_WORDS =
        Regex(
            // English
            """(?i)\b(?:send|share|forward|tell|give|reply|provide|text|read\s+out|type)\b""" +
                // Roman Urdu: bhej/bhejo/bhejein/bhej dein, bata/batao/batayen, share karein, de dein
                """|\b(?:bhej\w*|bheej\w*|bata\w*|btao|btado|de\s?dein|de\s?do|forward\s?kar\w*|share\s?kar\w*|reply\s?kar\w*|likh\s?kar\w*)\b|""" +
                // Urdu script
                ur("بھیج", "بتا", "شیئر", "فارورڈ", "لکھ", prefix = true) + "|" + ur("دیں", "دے\\s?دیں"),
        )

    private val NEGATIONS =
        Regex(
            """(?i)\b(?:do\s?not|don'?t|never|not|no\s?one|nobody|anyone|kabhi|kisi|mat|na|nahi|nahin)\b|""" +
                ur("مت", "نہ", "نہیں", "کبھی", "کسی"),
        )

    /** Codes legitimately shown to a delivery rider. */
    private val COURIER_CONTEXT =
        Regex("""(?i)\b(?:rider|courier|delivery\s?(?:boy|man|person))\b|""" + ur("رائیڈر", "ڈیلیوری"))

    /** Sentence ends, plus "..., warna/otherwise ..." which starts a new clause. */
    private val SENTENCE_SPLIT = Regex("""(?<=[.!?۔؟\n])\s*|\s*[,،]\s*(?=(?:warna|otherwise|or)\b)""")

    fun asksForCode(text: String): Boolean = matchingSentences(text).isNotEmpty()

    /**
     * The sentence (or pair of neighbouring sentences, for "my code went to your
     * number. Please send it back.") that triggered the rule.
     */
    fun matchingSentences(text: String): List<String> {
        val sentences = text.split(SENTENCE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        val windows = sentences + sentences.zipWithNext { a, b -> "$a $b" }
        return windows.filter { s ->
            CODE_WORDS.containsMatchIn(s) &&
                REQUEST_WORDS.containsMatchIn(s) &&
                !NEGATIONS.containsMatchIn(s) &&
                !COURIER_CONTEXT.containsMatchIn(s)
        }
    }
}
