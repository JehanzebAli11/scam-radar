package io.github.jehanzebali11.scamradar.core.text

import io.github.jehanzebali11.scamradar.core.verdict.Severity
import io.github.jehanzebali11.scamradar.core.verdict.Verdict

/** Pre-written text for "Send to family" and "Report this scam". Nothing is sent automatically. */
object ShareText {
    const val MAX_QUOTE = 500

    private fun quote(message: String): String {
        val m = message.trim()
        return if (m.length <= MAX_QUOTE) m else m.take(MAX_QUOTE) + "…"
    }

    /** Warning for family members, quoting the suspicious message. */
    fun forFamily(v: Verdict, message: String): String {
        val type = v.prediction?.scamType?.let { AdviceText.SCAM_TYPE_TITLE.getValue(it) }
        val (en, ur) =
            when (v.severity) {
                Severity.RED ->
                    "Scam Radar warning: this message looks like a scam" + (type?.let { " (${it.en})" } ?: "") +
                        ". If you get one like it, don't reply, don't send money or codes, and don't open its links." to
                        "سکیم ریڈار کی وارننگ: یہ پیغام فراڈ لگتا ہے" + (type?.let { " (${it.ur})" } ?: "") +
                        "۔ اگر آپ کو ایسا پیغام آئے تو جواب نہ دیں، پیسے یا کوڈ نہ بھیجیں اور لنک نہ کھولیں۔"
                Severity.AMBER ->
                    "Scam Radar says be careful with messages like this one. Never share codes or PINs." to
                        "سکیم ریڈار کے مطابق ایسے پیغام سے احتیاط کریں۔ کوڈ یا پن کبھی کسی کو نہ بتائیں۔"
                Severity.GREEN ->
                    "Scam Radar found no scam signs in this message, but always stay careful with codes and money." to
                        "سکیم ریڈار کو اس پیغام میں فراڈ کی نشانی نہیں ملی، پھر بھی کوڈ اور پیسوں کے معاملے میں محتاط رہیں۔"
            }
        return "$ur\n\n$en\n\n\"${quote(message)}\""
    }

    /** A report the user can review, copy, and send themselves. */
    fun report(v: Verdict, message: String, sender: String?): String {
        val type = v.prediction?.scamType?.let { AdviceText.SCAM_TYPE_TITLE.getValue(it).en } ?: "suspicious message"
        return buildString {
            appendLine("Scam report / فراڈ کی رپورٹ")
            appendLine("Sender / بھیجنے والا: ${sender?.takeIf { it.isNotBlank() } ?: "unknown"}")
            appendLine("Type: $type")
            appendLine("Message / پیغام:")
            append(quote(message))
        }
    }
}
