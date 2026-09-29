package co.lunt.andash.model

import java.time.Instant

/** A link for joining a meeting online, e.g. Meet, Zoom, Teams. */
data class MeetingLink(val kind: String, val url: String)

/**
 * One occurrence of a calendar event. [key] identifies the meeting across accounts, so the
 * same meeting synced from several accounts collapses into one [Meeting] with several [accounts].
 */
data class Meeting(
    val key: String,
    val title: String,
    val start: Instant,
    val end: Instant,
    val location: String? = null,
    val link: MeetingLink? = null,
    val accounts: List<String> = emptyList(),
    val color: Int = 0xFF4285F4.toInt(),
    val allDay: Boolean = false,
)

/** A calendar that exists on the device (one Google account can own several). */
data class CalendarInfo(
    val id: Long,
    val accountName: String,
    val accountType: String,
    val displayName: String,
    val color: Int,
    val visible: Boolean,
    val syncEnabled: Boolean,
) {
    /** Whether the calendar is used when the user has not made an explicit choice. */
    val defaultEnabled: Boolean
        get() {
            if (!visible) return false
            val name = displayName.lowercase()
            return !(name.contains("holiday") || name.contains("birthday") ||
                name.contains("week number"))
        }
}
