package io.github.jehanzebali11.scamradar.core

import io.github.jehanzebali11.scamradar.core.rules.BispAllowlist
import io.github.jehanzebali11.scamradar.core.rules.CodeRequestDetector
import io.github.jehanzebali11.scamradar.core.rules.LinkExtractor
import io.github.jehanzebali11.scamradar.core.rules.LinkReason
import io.github.jehanzebali11.scamradar.core.rules.RuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    // --- link extraction -------------------------------------------------

    @Test
    fun `extracts scheme www and bare links`() {
        val links =
            LinkExtractor.extract(
                "Claim at http://pm-relief-check.example/verify, or www.zetapay-help.com. Also see fictionbank.pk/login!",
            )
        assertEquals(listOf("pm-relief-check.example", "zetapay-help.com", "fictionbank.pk"), links.map { it.host })
        assertEquals("http://pm-relief-check.example/verify", links[0].raw)
        assertEquals("fictionbank.pk/login", links[2].raw)
    }

    @Test
    fun `flags suspicious links`() {
        val links = LinkExtractor.extract("Go to https://bit.ly/abc or http://192.168.0.1/x or https://xn--fictinbank-abc.com now")
        assertEquals(setOf(LinkReason.SHORTENER), links[0].reasons)
        assertTrue(LinkReason.IP_ADDRESS in links[1].reasons)
        assertTrue(LinkReason.PUNYCODE in links[2].reasons)
        assertTrue(links.all { it.isSuspicious })
    }

    @Test
    fun `plain http alone is not suspicious`() {
        val link = LinkExtractor.extract("http://parcel-redelivery.example/pk").single()
        assertEquals(setOf(LinkReason.NOT_HTTPS), link.reasons)
        assertFalse(link.isSuspicious)
    }

    @Test
    fun `no false links in amounts emails or ussd codes`() {
        val text = "Rs 2,500.00 debited. Balance Rs 18,240.00. Dial *000# or mail help@fictionbank.com. Ver 1.2.3"
        assertEquals(emptyList<String>(), LinkExtractor.extract(text).map { it.raw })
    }

    @Test
    fun `seed messages with links are found`() {
        for (seed in TestFiles.seeds()) {
            val expectsLink = "has_link" in seed.expected
            assertEquals(seed.id, expectsLink, LinkExtractor.extract(seed.text).isNotEmpty())
        }
    }

    // --- asks for a code ------------------------------------------------

    @Test
    fun `detects code requests in three scripts`() {
        val asks =
            listOf(
                "Ghalti se mera code aap ke number par chala gaya, please wapis bhej dein",
                "Please send me the OTP you just received",
                "Apna 4 digit PIN reply karein",
                "مہربانی کر کے وہ کوڈ مجھے بھیج دیں",
                "Kindly forward the verification code to me, it's urgent.",
                // code in one sentence, the request in the next
                "Ghalti se mera code aap ke number par chala gaya. Please wapis bhej dein.",
            )
        for (t in asks) assertTrue(t, CodeRequestDetector.asksForCode(t))
    }

    @Test
    fun `genuine otp and courier messages do not trigger`() {
        val genuine =
            listOf(
                "Your OTP is 000000. Do not share this code with anyone.",
                "Yeh code kisi ke saath share na karein.",
                "یہ کوڈ کسی کو مت بتائیں۔",
                "Share delivery code 0000 with the rider only at the time of delivery.",
                "Rider ko parcel lete waqt 4 digit code 0000 batayen.",
                // "اپنا" contains the letters of "پن" (PIN); must not count as a code word
                "اپنا شناختی کارڈ لے کر قریبی مرکز جائیں اور تفصیل بتائیں",
            )
        for (t in genuine) assertFalse(t, CodeRequestDetector.asksForCode(t))
    }

    @Test
    fun `agrees with every seed's asks_for_code flag`() {
        for (seed in TestFiles.seeds()) {
            val expected = "asks_for_code" in seed.expected
            assertEquals("${seed.id}: ${seed.text}", expected, CodeRequestDetector.asksForCode(seed.text))
        }
    }

    @Test
    fun `negation in one sentence does not hide a request in another`() {
        val t = "Don't worry. Send me the code you got now."
        assertTrue(CodeRequestDetector.asksForCode(t))
    }

    // --- BISP 8171 ------------------------------------------------------

    @Test
    fun `official sender must be exactly 8171`() {
        assertTrue(BispAllowlist.isOfficialSender("8171"))
        assertTrue(BispAllowlist.isOfficialSender(" 8171 "))
        assertFalse(BispAllowlist.isOfficialSender("+92 300 0000000"))
        assertFalse(BispAllowlist.isOfficialSender("08171"))
        assertFalse(BispAllowlist.isOfficialSender("81710"))
        assertFalse(BispAllowlist.isOfficialSender(null))
    }

    @Test
    fun `8171 sender short-circuits only for clean messages`() {
        val genuine = TestFiles.seeds().filter { it.category == "hard_negative_bisp_8171" }
        assertEquals(3, genuine.size)
        for (seed in genuine) {
            val r = RuleEngine.run(seed.text, sender = "8171")
            assertNotNull(seed.id, r.shortCircuit)
            assertEquals(seed.expected, r.shortCircuit!!.toLine())
        }
        val withLink = RuleEngine.run("8171: BISP qist ke liye yahan click karein http://bisp-check.example", "8171")
        assertNull(withLink.shortCircuit)
    }

    @Test
    fun `8171 written in the text from a normal number is not allowlisted`() {
        val r = RuleEngine.run("8171: BISP ki qist mubarak! 1000 fee bhejein", sender = "0300-0000000")
        assertNull(r.shortCircuit)
        assertTrue(r.bispFromUnofficialSender)
        // Pasted text has no sender: no allowlist, no impersonation claim.
        val pasted = RuleEngine.run("8171: BISP ki qist mubarak!", sender = null)
        assertNull(pasted.shortCircuit)
        assertFalse(pasted.bispFromUnofficialSender)
        assertTrue(pasted.mentionsBisp)
    }

    @Test
    fun `rule flags come from links and code requests`() {
        val r = RuleEngine.run("Send me the OTP or open https://bit.ly/x")
        assertEquals(setOf(Flag.ASKS_FOR_CODE, Flag.HAS_LINK), r.flags)
        assertTrue(r.hasSuspiciousLink)
    }
}
