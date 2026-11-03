package io.github.jehanzebali11.scamradar.notify

/** The parts of a notification we read. Plain data so the logic is unit-testable. */
data class NotificationFields(
    val packageName: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    /** MessagingStyle messages, oldest first: (sender, text). */
    val messages: List<Pair<String?, String>> = emptyList(),
    val isGroupSummary: Boolean = false,
    val isOngoing: Boolean = false,
)

data class IncomingMessage(val sender: String?, val text: String)

/** Which apps are watched, and how a message is pulled out of a notification. */
object NotificationMessage {
    /** SMS and WhatsApp apps. The phone's default SMS app is always added at runtime. */
    val WATCHED_PACKAGES =
        setOf(
            "com.google.android.apps.messaging", // Google Messages
            "com.samsung.android.messaging", // Samsung Messages
            "com.android.mms",
            "com.android.messaging",
            "com.whatsapp",
            "com.whatsapp.w4b", // WhatsApp Business
        )

    fun isWatched(packageName: String, defaultSmsPackage: String?): Boolean =
        packageName in WATCHED_PACKAGES || packageName == defaultSmsPackage

    /**
     * The newest message in the notification, or null if there is nothing to check
     * (group summaries, ongoing notifications, "3 new messages" style counters).
     */
    fun extract(f: NotificationFields): IncomingMessage? {
        if (f.isGroupSummary || f.isOngoing) return null
        val last = f.messages.lastOrNull { it.second.isNotBlank() }
        if (last != null) return IncomingMessage(last.first?.takeIf { it.isNotBlank() } ?: f.title, last.second.trim())
        val body = (f.bigText?.takeIf { it.isNotBlank() } ?: f.text)?.trim()
        if (body.isNullOrEmpty() || COUNTER.matches(body)) return null
        return IncomingMessage(f.title?.trim(), body)
    }

    private val COUNTER = Regex("""(?i)^\d+\s+(new\s+)?messages?(\s+from\s+\d+\s+chats?)?$""")
}

/** Skips re-checking the same message when apps re-post or update a notification. */
class RecentMessages(private val capacity: Int = 50) {
    private val seen = LinkedHashSet<Int>()

    /** True if this message was seen recently; records it otherwise. */
    @Synchronized
    fun checkAndAdd(packageName: String, msg: IncomingMessage): Boolean {
        val key = (packageName + "\u0000" + msg.sender + "\u0000" + msg.text).hashCode()
        if (!seen.add(key)) return true
        if (seen.size > capacity) seen.remove(seen.first())
        return false
    }
}
