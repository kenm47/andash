package co.lunt.andash

import co.lunt.andash.domain.MeetingLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeetingLinksTest {
    @Test
    fun findsMeetInDescription() {
        val l = MeetingLinks.find(null, "Join: https://meet.google.com/abc-defg-hij?authuser=0 thanks")
        assertEquals("Meet", l?.kind)
        assertEquals("https://meet.google.com/abc-defg-hij?authuser=0", l?.url)
    }

    @Test
    fun findsZoomInLocation() {
        val l = MeetingLinks.find("https://us02web.zoom.us/j/123456789?pwd=abc", "")
        assertEquals("Zoom", l?.kind)
    }

    @Test
    fun findsTeamsAndWebex() {
        assertEquals("Teams", MeetingLinks.find("https://teams.microsoft.com/l/meetup-join/19%3ameeting_x/0?context=y")?.kind)
        assertEquals("Webex", MeetingLinks.find("https://acme.webex.com/meet/joe")?.kind)
    }

    @Test
    fun stripsTrailingPunctuation() {
        assertEquals("https://meet.google.com/abc-defg-hij", MeetingLinks.find("see https://meet.google.com/abc-defg-hij.")?.url)
    }

    @Test
    fun ignoresPlainTextAndOtherLinks() {
        assertNull(MeetingLinks.find("Conference room 4", null))
        assertNull(MeetingLinks.find("https://example.com/zoom", null))
        assertNull(MeetingLinks.find(null, null))
    }
}
