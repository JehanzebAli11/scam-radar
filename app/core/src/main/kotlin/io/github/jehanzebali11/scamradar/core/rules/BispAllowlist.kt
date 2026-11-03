package io.github.jehanzebali11.scamradar.core.rules

/**
 * BISP (Benazir Income Support Programme) sends its SMS only from the short
 * code 8171. Scammers copy the name and even write "8171" in the text, but they
 * send from ordinary mobile numbers and usually ask for a fee, a code or a click.
 */
object BispAllowlist {
    const val OFFICIAL_SENDER = "8171"

    private val BISP_MENTION =
        Regex("""(?i)\bbisp\b|\bbenazir\b|\bbenazir\s+income\b|\behsaas\b|\b8171\b|بینظیر|بے\s?نظیر|احساس|انکم\s?سپورٹ""")

    /** True when the sender id is exactly the official short code (spaces/dashes ignored). */
    fun isOfficialSender(sender: String?): Boolean =
        sender != null && sender.filter { it.isLetterOrDigit() } == OFFICIAL_SENDER

    fun mentionsBisp(text: String): Boolean = BISP_MENTION.containsMatchIn(text)
}
