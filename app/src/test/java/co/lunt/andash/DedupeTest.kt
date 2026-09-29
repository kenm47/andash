package co.lunt.andash

import co.lunt.andash.domain.dedupe
import co.lunt.andash.domain.meetingKey
import co.lunt.andash.model.Meeting
import co.lunt.andash.model.MeetingLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant

class DedupeTest {
    private fun m(uid: String?, title: String, s: Long, account: String, link: MeetingLink? = null, loc: String? = null) = Meeting(
        key = meetingKey(uid, title, s, s + 1800_000), title = title,
        start = Instant.ofEpochMilli(s), end = Instant.ofEpochMilli(s + 1800_000),
        accounts = listOf(account), link = link, location = loc,
    )

    @Test
    fun sameUidAndStartAcrossAccountsMerges() {
        val out = dedupe(listOf(m("u1", "Sync", 1000, "a"), m("u1", "Sync", 1000, "b"), m("u1", "Sync", 1000, "a")))
        assertEquals(1, out.size)
        assertEquals(listOf("a", "b"), out[0].accounts)
    }

    @Test
    fun recurringInstancesWithSameUidStayApart() {
        val out = dedupe(listOf(m("u1", "Standup", 1000, "a"), m("u1", "Standup", 90_000_000, "a")))
        assertEquals(2, out.size)
    }

    @Test
    fun withoutUidFallsBackToTitleAndTimes() {
        val out = dedupe(listOf(m(null, "Lunch", 5000, "a"), m(null, " lunch ", 5000, "b"), m(null, "Lunch", 6000, "b")))
        assertEquals(2, out.size)
        assertEquals(listOf("a", "b"), out[0].accounts)
    }

    @Test
    fun mergeKeepsFirstNonNullLocationAndLink() {
        val link = MeetingLink("Meet", "https://meet.google.com/x")
        val out = dedupe(listOf(m("u", "T", 1, "a"), m("u", "T", 1, "b", link = link, loc = "Room")))
        assertEquals(link, out[0].link)
        assertEquals("Room", out[0].location)
    }

    @Test
    fun keysDifferForDifferentEvents() {
        assertNotEquals(meetingKey("a", "T", 1, 2), meetingKey("b", "T", 1, 2))
        assertNotEquals(meetingKey(null, "T", 1, 2), meetingKey(null, "T", 1, 3))
    }

    @Test
    fun orderIsPreserved() {
        val out = dedupe(listOf(m("b", "B", 2, "a"), m("a", "A", 1, "a"), m("b", "B", 2, "c")))
        assertEquals(listOf("B", "A"), out.map { it.title })
    }
}
