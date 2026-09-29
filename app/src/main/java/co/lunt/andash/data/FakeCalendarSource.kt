package co.lunt.andash.data

import co.lunt.andash.domain.dedupe
import co.lunt.andash.domain.meetingKey
import co.lunt.andash.model.CalendarInfo
import co.lunt.andash.model.Meeting
import co.lunt.andash.model.MeetingLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Duration
import java.time.Instant

/** Sample data for previews and tests. Meetings are placed relative to [anchor]. */
class FakeCalendarSource(private val anchor: () -> Instant = { Instant.now() }) : CalendarSource {
    private val calendars = listOf(
        CalendarInfo(1, "kendall@example.com", "com.google", "Kendall", 0xFF4285F4.toInt(), true, true),
        CalendarInfo(2, "work@example.org", "com.google", "Work", 0xFF0B8043.toInt(), true, true),
    )

    override suspend fun calendars(): List<CalendarInfo> = calendars

    override suspend fun load(from: Instant, to: Instant, options: LoadOptions): List<Meeting> =
        sample(anchor()).filter { it.end > from && it.start < to }

    override fun changes(): Flow<Unit> = emptyFlow()

    companion object {
        fun sample(now: Instant): List<Meeting> {
            fun m(title: String, startMin: Long, lenMin: Long, account: String, color: Int, link: MeetingLink? = null, loc: String? = null): Meeting {
                val s = now.plus(Duration.ofMinutes(startMin))
                val e = s.plus(Duration.ofMinutes(lenMin))
                return Meeting(
                    key = meetingKey(null, title, s.toEpochMilli(), e.toEpochMilli()),
                    title = title, start = s, end = e, location = loc, link = link,
                    accounts = listOf(account), color = color,
                )
            }
            val blue = 0xFF4285F4.toInt()
            val green = 0xFF0B8043.toInt()
            return dedupe(
                listOf(
                    m("Product sync", 12, 30, "kendall", blue, MeetingLink("Meet", "https://meet.google.com/abc-defg-hij")),
                    m("Product sync", 12, 30, "work", green),
                    m("1:1 with Alex", 60, 30, "work", green, MeetingLink("Zoom", "https://zoom.us/j/123456789")),
                    m("Design review with a very long title that needs to wrap", 120, 45, "kendall", blue, loc = "Room 4B"),
                    m("Board prep", 165, 60, "work", green),
                    m("Dinner", 60 * 26, 90, "kendall", blue),
                ),
            )
        }
    }
}
