package io.github.jehanzebali11.scamradar.core.text

import io.github.jehanzebali11.scamradar.core.Flag
import io.github.jehanzebali11.scamradar.core.Label
import io.github.jehanzebali11.scamradar.core.ScamType
import io.github.jehanzebali11.scamradar.core.verdict.CautionReason
import io.github.jehanzebali11.scamradar.core.verdict.Severity
import io.github.jehanzebali11.scamradar.core.verdict.Source
import io.github.jehanzebali11.scamradar.core.verdict.Verdict

/** One piece of user-facing text in both languages. */
data class Bilingual(val en: String, val ur: String)

/** Everything a result card shows. All of it is pre-written; none comes from the model. */
data class CardText(
    val severity: Severity,
    val headline: Bilingual,
    val title: Bilingual?,
    val advice: List<Bilingual>,
    val flagChips: List<Bilingual>,
)

/**
 * Pre-written advice. The model only picks a label, a scam type and flags; the
 * words the user reads come from here. Urdu text should be reviewed by a native
 * speaker before release (see app/README.md).
 */
object AdviceText {
    val RED_HEADLINE = Bilingual("Likely scam", "یہ فراڈ لگتا ہے")
    val AMBER_HEADLINE = Bilingual("Be careful", "احتیاط کریں")
    val GREEN_HEADLINE = Bilingual("No scam signs found", "فراڈ کی کوئی نشانی نہیں ملی")

    val RED_ACTIONS =
        Bilingual(
            "Don't reply. Don't send money, codes or CNIC details. Block the number.",
            "جواب نہ دیں۔ پیسے، کوڈ یا شناختی کارڈ کی تفصیل نہ بھیجیں۔ نمبر بلاک کر دیں۔",
        )

    val SCAM_TYPE_TITLE: Map<ScamType, Bilingual> =
        mapOf(
            ScamType.FAKE_GOVT_AID to Bilingual("Fake government aid", "جعلی سرکاری امداد"),
            ScamType.FAKE_PRIZE to Bilingual("Fake prize or lucky draw", "جعلی انعام یا لکی ڈرا"),
            ScamType.CODE_THEFT to Bilingual("Someone wants your code", "کوئی آپ کا کوڈ چرانا چاہتا ہے"),
            ScamType.BANK_OR_WALLET_IMPERSONATION to
                Bilingual("Fake bank or wallet message", "بینک یا والٹ کے نام پر جعلی پیغام"),
            ScamType.FAMILY_EMERGENCY to Bilingual("Fake family emergency", "جعلی خاندانی ایمرجنسی"),
            ScamType.FAKE_JOB_OR_INVESTMENT to Bilingual("Fake job or investment", "جعلی نوکری یا سرمایہ کاری"),
            ScamType.PHISHING_LINK to Bilingual("Dangerous link", "خطرناک لنک"),
            ScamType.OTHER to Bilingual("Scam signs", "فراڈ کی نشانیاں"),
        )

    val SCAM_TYPE_ADVICE: Map<ScamType, Bilingual> =
        mapOf(
            ScamType.FAKE_GOVT_AID to
                Bilingual(
                    "Government programmes like BISP never ask for a fee, a code or your PIN. " +
                        "Real BISP messages come only from 8171.",
                    "بی آئی ایس پی جیسے سرکاری پروگرام کبھی فیس، کوڈ یا پن نہیں مانگتے۔ " +
                        "اصل بی آئی ایس پی پیغام صرف 8171 سے آتا ہے۔",
                ),
            ScamType.FAKE_PRIZE to
                Bilingual(
                    "You cannot win a draw you never entered. Nobody genuine asks for 'tax' or a fee to hand over a prize.",
                    "جس قرعہ اندازی میں آپ نے حصہ ہی نہیں لیا اس میں آپ جیت نہیں سکتے۔ " +
                        "کوئی اصلی ادارہ انعام دینے کے لیے ٹیکس یا فیس نہیں مانگتا۔",
                ),
            ScamType.CODE_THEFT to
                Bilingual(
                    "Never share a code you got by SMS, not even with family or 'customer support'. " +
                        "Whoever gets it can take over your WhatsApp, bank or wallet.",
                    "ایس ایم ایس پر آیا ہوا کوڈ کبھی کسی کو نہ بتائیں، چاہے وہ رشتہ دار ہو یا 'کسٹمر سپورٹ'۔ " +
                        "جس کے پاس یہ کوڈ چلا گیا وہ آپ کا واٹس ایپ، بینک یا والٹ اپنے قبضے میں لے سکتا ہے۔",
                ),
            ScamType.BANK_OR_WALLET_IMPERSONATION to
                Bilingual(
                    "Your bank or wallet will never ask for your PIN, OTP or card number. " +
                        "Call the number on your card or in the official app yourself, not the number in this message.",
                    "آپ کا بینک یا والٹ کبھی پن، او ٹی پی یا کارڈ نمبر نہیں مانگتا۔ " +
                        "اس پیغام والے نمبر کے بجائے اپنے کارڈ یا آفیشل ایپ پر لکھے نمبر پر خود فون کریں۔",
                ),
            ScamType.FAMILY_EMERGENCY to
                Bilingual(
                    "Scammers pretend to be a relative on a new number. " +
                        "Before sending anything, call the person on their old number or check with family.",
                    "فراڈیے نئے نمبر سے رشتہ دار بن کر پیسے مانگتے ہیں۔ " +
                        "کچھ بھیجنے سے پہلے اس شخص کو اس کے پرانے نمبر پر فون کریں یا گھر والوں سے تصدیق کریں۔",
                ),
            ScamType.FAKE_JOB_OR_INVESTMENT to
                Bilingual(
                    "Real jobs don't charge a registration or visa fee by message, and nobody can guarantee daily profit.",
                    "اصلی نوکری کے لیے پیغام پر رجسٹریشن یا ویزا فیس نہیں مانگی جاتی، " +
                        "اور روزانہ پکے منافع کی ضمانت کوئی نہیں دے سکتا۔",
                ),
            ScamType.PHISHING_LINK to
                Bilingual(
                    "Don't open the link or enter any details. Fake pages copy courier, tax and phone company sites to steal your information.",
                    "یہ لنک نہ کھولیں اور کوئی معلومات درج نہ کریں۔ " +
                        "جعلی ویب سائٹیں کورئیر، ٹیکس اور موبائل کمپنیوں کی نقل کر کے آپ کی معلومات چراتی ہیں۔",
                ),
            ScamType.OTHER to
                Bilingual(
                    "This message has the signs of a scam. Check with someone you trust before doing anything it asks.",
                    "اس پیغام میں فراڈ کی نشانیاں ہیں۔ جو یہ کہہ رہا ہے وہ کرنے سے پہلے کسی قابلِ اعتماد شخص سے پوچھ لیں۔",
                ),
        )

    val CAUTION_ADVICE: Map<CautionReason, Bilingual> =
        mapOf(
            CautionReason.ASKS_FOR_CODE to
                Bilingual(
                    "This message asks for a code. Never share an OTP, PIN or password with anyone.",
                    "یہ پیغام کوڈ مانگ رہا ہے۔ او ٹی پی، پن یا پاس ورڈ کبھی کسی کو نہ بتائیں۔",
                ),
            CautionReason.SUSPICIOUS_LINK to
                Bilingual(
                    "It has a link that hides where it goes or looks unusual. Don't open it unless you are sure who sent it.",
                    "اس میں ایک مشکوک لنک ہے۔ جب تک بھیجنے والے کا یقین نہ ہو، اسے نہ کھولیں۔",
                ),
            CautionReason.BISP_NOT_OFFICIAL to
                Bilingual(
                    "It mentions BISP but did not come from 8171. Real BISP messages come only from 8171.",
                    "اس میں بی آئی ایس پی کا ذکر ہے لیکن یہ 8171 سے نہیں آیا۔ اصل بی آئی ایس پی پیغام صرف 8171 سے آتے ہیں۔",
                ),
            CautionReason.MODEL_UNSURE to
                Bilingual(
                    "Scam Radar could not check this message properly. Treat it with care.",
                    "سکیم ریڈار اس پیغام کو ٹھیک سے جانچ نہیں سکا۔ احتیاط سے کام لیں۔",
                ),
        )

    val GREEN_SAFE =
        Bilingual(
            "This looks like a normal message. Still, never share codes or PINs with anyone.",
            "یہ ایک عام پیغام لگتا ہے۔ پھر بھی کوڈ یا پن کبھی کسی کو نہ بتائیں۔",
        )
    val GREEN_PROMO_TITLE = Bilingual("Looks like an advert", "یہ ایک اشتہار لگتا ہے")
    val GREEN_PROMO =
        Bilingual(
            "This looks like a normal offer. If you want it, use the company's official app or number.",
            "یہ ایک عام آفر لگتی ہے۔ اگر آپ کو چاہیے تو کمپنی کی آفیشل ایپ یا نمبر استعمال کریں۔",
        )
    val GREEN_BISP_TITLE = Bilingual("From BISP's official number 8171", "بی آئی ایس پی کے سرکاری نمبر 8171 سے")
    val GREEN_BISP =
        Bilingual(
            "BISP never charges a fee. To collect money, go to a BISP centre with your original CNIC.",
            "بی آئی ایس پی کوئی فیس نہیں لیتا۔ رقم کے لیے اپنا اصل شناختی کارڈ لے کر بی آئی ایس پی مرکز جائیں۔",
        )
    val BISP_NOTE =
        Bilingual(
            "Real BISP messages come only from 8171 and never ask for a fee.",
            "اصل بی آئی ایس پی پیغام صرف 8171 سے آتے ہیں اور کبھی فیس نہیں مانگتے۔",
        )

    val FLAG_CHIP: Map<Flag, Bilingual> =
        mapOf(
            Flag.ASKS_FOR_CODE to Bilingual("Asks for a code", "کوڈ مانگ رہا ہے"),
            Flag.ASKS_FOR_MONEY to Bilingual("Asks for money", "پیسے مانگ رہا ہے"),
            Flag.HAS_LINK to Bilingual("Has a link", "لنک موجود ہے"),
            Flag.URGENCY to Bilingual("Rushes you", "جلدی کا دباؤ"),
            Flag.CLAIMS_OFFICIAL to Bilingual("Claims to be official", "آفیشل ہونے کا دعویٰ"),
        )

    val DEMO_MODE =
        Bilingual(
            "Demo mode: the model file is not loaded, so this is only a simple keyword check.",
            "ڈیمو موڈ: ماڈل فائل لوڈ نہیں ہوئی، اس لیے یہ صرف ایک سادہ جانچ ہے۔",
        )

    /** Builds the card for a verdict. Total: every severity/label/type has text. */
    fun card(v: Verdict): CardText {
        val pred = v.prediction
        val chips = v.orderedFlags.map { FLAG_CHIP.getValue(it) }
        return when (v.severity) {
            Severity.RED -> {
                val type = requireNotNull(pred?.scamType) { "red verdict without scam type" }
                CardText(
                    Severity.RED,
                    RED_HEADLINE,
                    SCAM_TYPE_TITLE.getValue(type),
                    listOf(SCAM_TYPE_ADVICE.getValue(type), RED_ACTIONS),
                    chips,
                )
            }
            Severity.AMBER ->
                CardText(
                    Severity.AMBER,
                    AMBER_HEADLINE,
                    null,
                    CautionReason.entries.filter { it in v.cautions }.map { CAUTION_ADVICE.getValue(it) },
                    chips,
                )
            Severity.GREEN -> {
                val (title, body) =
                    when {
                        v.source == Source.RULES -> GREEN_BISP_TITLE to GREEN_BISP
                        pred?.label == Label.PROMO -> GREEN_PROMO_TITLE to GREEN_PROMO
                        else -> null to GREEN_SAFE
                    }
                val advice = listOf(body) + if (v.rules.mentionsBisp && v.source != Source.RULES) listOf(BISP_NOTE) else emptyList()
                CardText(Severity.GREEN, GREEN_HEADLINE, title, advice, emptyList())
            }
        }
    }
}
