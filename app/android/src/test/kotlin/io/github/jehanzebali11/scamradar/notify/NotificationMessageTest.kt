package io.github.jehanzebali11.scamradar.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationMessageTest {
    private fun f(
        title: String? = "0300-0000000",
        text: String? = null,
        bigText: String? = null,
        messages: List<Pair<String?, String>> = emptyList(),
        summary: Boolean = false,
    ) = NotificationFields("com.whatsapp", title, text, bigText, messages, isGroupSummary = summary)

    @Test
    fun `watches sms and whatsapp plus the default sms app`() {
        assertTrue(NotificationMessage.isWatched("com.whatsapp", null))
        assertTrue(NotificationMessage.isWatched("com.google.android.apps.messaging", null))
        assertTrue(NotificationMessage.isWatched("org.example.sms", "org.example.sms"))
        assertFalse(NotificationMessage.isWatched("com.example.game", "org.example.sms"))
    }

    @Test
    fun `prefers the newest messaging style message`() {
        val msg = NotificationMessage.extract(f(title = "Group", text = "2 new messages", messages = listOf("A" to "hi", "Bilal" to "code bhej do")))
        assertEquals(IncomingMessage("Bilal", "code bhej do"), msg)
    }

    @Test
    fun `falls back to big text then text`() {
        assertEquals("full body", NotificationMessage.extract(f(text = "short", bigText = "full body"))!!.text)
        assertEquals("short", NotificationMessage.extract(f(text = "short"))!!.text)
        assertEquals("0300-0000000", NotificationMessage.extract(f(text = "short"))!!.sender)
    }

    @Test
    fun `skips summaries counters and empty notifications`() {
        assertNull(NotificationMessage.extract(f(text = "hello", summary = true)))
        assertNull(NotificationMessage.extract(f(text = "4 new messages")))
        assertNull(NotificationMessage.extract(f(text = "12 messages from 3 chats")))
        assertNull(NotificationMessage.extract(f(text = "  ")))
    }

    @Test
    fun `recent messages dedupes and is bounded`() {
        val r = RecentMessages(capacity = 2)
        val a = IncomingMessage("x", "a")
        assertFalse(r.checkAndAdd("p", a))
        assertTrue(r.checkAndAdd("p", a))
        r.checkAndAdd("p", IncomingMessage("x", "b"))
        r.checkAndAdd("p", IncomingMessage("x", "c"))
        assertFalse("oldest entry was evicted", r.checkAndAdd("p", a))
    }
}
