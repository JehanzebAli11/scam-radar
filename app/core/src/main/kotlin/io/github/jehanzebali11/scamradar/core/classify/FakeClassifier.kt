package io.github.jehanzebali11.scamradar.core.classify

import io.github.jehanzebali11.scamradar.core.Flag
import io.github.jehanzebali11.scamradar.core.Label
import io.github.jehanzebali11.scamradar.core.Prediction
import io.github.jehanzebali11.scamradar.core.ScamType
import io.github.jehanzebali11.scamradar.core.rules.CodeRequestDetector
import io.github.jehanzebali11.scamradar.core.rules.LinkExtractor

/**
 * Keyword stand-in for the model, so the UI and rule layer work without the
 * .litertlm file. It is deliberately simple and NOT a substitute for the model;
 * the UI labels its results as demo mode.
 */
class FakeClassifier : MessageClassifier {
    override val description: String = "demo (keyword) mode"
    override val isRealModel: Boolean = false

    override suspend fun classify(text: String): String = guess(text).toLine()

    fun guess(text: String): Prediction {
        val t = text.lowercase()
        val flags = mutableSetOf<Flag>()
        if (CodeRequestDetector.asksForCode(text)) flags += Flag.ASKS_FOR_CODE
        if (LinkExtractor.extract(text).isNotEmpty()) flags += Flag.HAS_LINK
        if (MONEY.any { it in t }) flags += Flag.ASKS_FOR_MONEY
        if (URGENCY.any { it in t }) flags += Flag.URGENCY
        if (OFFICIAL.any { it in t }) flags += Flag.CLAIMS_OFFICIAL

        val type =
            when {
                Flag.ASKS_FOR_CODE in flags && BANK.any { it in t } -> ScamType.BANK_OR_WALLET_IMPERSONATION
                Flag.ASKS_FOR_CODE in flags -> ScamType.CODE_THEFT
                GOVT.any { it in t } && Flag.ASKS_FOR_MONEY in flags -> ScamType.FAKE_GOVT_AID
                PRIZE.any { it in t } -> ScamType.FAKE_PRIZE
                FAMILY.any { it in t } && Flag.ASKS_FOR_MONEY in flags -> ScamType.FAMILY_EMERGENCY
                JOB.any { it in t } && Flag.ASKS_FOR_MONEY in flags -> ScamType.FAKE_JOB_OR_INVESTMENT
                Flag.HAS_LINK in flags && PHISH.any { it in t } -> ScamType.PHISHING_LINK
                else -> null
            }
        if (type != null) return Prediction(Label.SCAM, type, flags)
        val label = if (PROMO.any { it in t }) Label.PROMO else Label.SAFE
        return Prediction(label, null, flags - Flag.ASKS_FOR_MONEY)
    }

    private companion object {
        val MONEY = listOf("fee", "fees", "jama", "deposit", "jazzcash", "easypaisa", "transfer", "bhej", "فیس", "روپے بھیج", "رقم بھیج", "registration")
        val URGENCY = listOf("urgent", "immediately", "within 24", "jaldi", "fori", "foran", "abhi", "today only", "aaj hi", "فوری", "فوراً", "warna")
        val OFFICIAL = listOf("bank", "bisp", "fbr", "pta", "govt", "government", "support", "helpline", "بینک", "پروگرام")
        val BANK = listOf("bank", "wallet", "account", "atm", "card", "بینک", "کارڈ")
        val GOVT = listOf("bisp", "ehsaas", "relief", "imdad", "qist", "احساس", "امداد", "بینظیر")
        val PRIZE = listOf("lucky draw", "inaam", "prize", "jeet", "won", "انعام")
        val FAMILY = listOf("ammi", "abbu", "khala", "beta", "naya number", "new number", "accident", "ابو", "امی", "نیا نمبر")
        val JOB = listOf("job", "invest", "profit", "kamayen", "earn", "visa", "نوکری")
        val PHISH = listOf("verify", "update", "claim", "refund", "click", "parcel", "login")
        val PROMO = listOf("offer", "sale", "% off", "bundle", "discount", "deal", "subscribe", "آفر", "ڈیل")
    }
}
