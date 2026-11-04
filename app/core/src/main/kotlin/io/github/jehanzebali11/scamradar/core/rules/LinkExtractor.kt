package io.github.jehanzebali11.scamradar.core.rules

/** A link found in a message. [host] is lowercase, without `www.` or port. */
data class Link(
    val raw: String,
    val host: String,
    val reasons: Set<LinkReason> = emptySet(),
) {
    /** Plain http alone is common in genuine messages, so it does not count on its own. */
    val isSuspicious: Boolean get() = reasons.any { it != LinkReason.NOT_HTTPS }
}

enum class LinkReason {
    /** bit.ly and friends hide where the link goes. */
    SHORTENER,

    /** A bare IP address instead of a domain name. */
    IP_ADDRESS,

    /** Punycode (`xn--`) hosts can imitate real brand names. */
    PUNYCODE,

    /** Plain http, no encryption. */
    NOT_HTTPS,

    /** Top-level domains that are cheap and heavily used for scams. */
    RISKY_TLD,
}

/**
 * Finds links in message text: `http(s)://...`, `www....` and bare domains such
 * as `example.pk/claim`. Pure string work; nothing is opened or resolved.
 */
object LinkExtractor {
    private val TLDS =
        listOf(
            "com", "pk", "net", "org", "info", "xyz", "top", "site", "online", "live", "click",
            "link", "app", "io", "co", "me", "ly", "gl", "gd", "cc", "ws", "biz", "shop", "store",
            "vip", "icu", "buzz", "tk", "ml", "ga", "cf", "gq", "example",
        )
    private val SHORTENERS =
        setOf(
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "cutt.ly", "rb.gy", "ow.ly",
            "shorturl.at", "tiny.cc", "rebrand.ly", "s.id", "t.ly", "v.gd",
        )
    private val RISKY_TLDS = setOf("xyz", "top", "icu", "buzz", "tk", "ml", "ga", "cf", "gq", "click", "vip")

    private val SCHEME_OR_WWW = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"'()\[\]]+""")
    private val BARE_DOMAIN =
        Regex(
            """(?i)(?<![@\w.\-/])(?:[a-z0-9](?:[a-z0-9\-]{0,61}[a-z0-9])?\.)+(?:${TLDS.joinToString("|")})""" +
                """(?![a-z0-9\-])(?:/[^\s<>"'()\[\]]*)?""",
        )
    private val IPV4 = Regex("""^\d{1,3}(?:\.\d{1,3}){3}$""")
    private val TRAILING_PUNCT = charArrayOf('.', ',', ';', ':', '!', '?', '،', '۔')

    fun extract(text: String): List<Link> {
        val found = linkedMapOf<String, Link>()
        val covered = mutableListOf<IntRange>()
        for (m in SCHEME_OR_WWW.findAll(text)) {
            val raw = m.value.trimEnd(*TRAILING_PUNCT)
            covered += m.range
            toLink(raw)?.let { found.putIfAbsent(raw.lowercase(), it) }
        }
        for (m in BARE_DOMAIN.findAll(text)) {
            if (covered.any { m.range.first in it }) continue
            val raw = m.value.trimEnd(*TRAILING_PUNCT)
            toLink(raw)?.let { found.putIfAbsent(raw.lowercase(), it) }
        }
        return found.values.toList()
    }

    private fun toLink(raw: String): Link? {
        val lower = raw.lowercase()
        val scheme = Regex("""^([a-z]+)://""").find(lower)?.groupValues?.get(1)
        val rest = lower.substringAfter("://")
        val host =
            rest.substringBefore('/').substringBefore('?').substringBefore('#')
                .substringAfter('@').substringBefore(':').removePrefix("www.")
        if (host.isEmpty() || !host.contains('.')) return null
        val reasons = mutableSetOf<LinkReason>()
        if (host in SHORTENERS) reasons += LinkReason.SHORTENER
        if (IPV4.matches(host)) reasons += LinkReason.IP_ADDRESS
        if (host.split('.').any { it.startsWith("xn--") }) reasons += LinkReason.PUNYCODE
        if (scheme == "http") reasons += LinkReason.NOT_HTTPS
        if (host.substringAfterLast('.') in RISKY_TLDS) reasons += LinkReason.RISKY_TLD
        return Link(raw = raw, host = host, reasons = reasons)
    }
}
