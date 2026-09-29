package co.lunt.andash

import co.lunt.andash.domain.CountdownState
import co.lunt.andash.domain.Schedule
import co.lunt.andash.domain.ScheduleConfig
import co.lunt.andash.domain.Urgency
import co.lunt.andash.model.Meeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {
    private val utc = ZoneId.of("UTC")
    private val la = ZoneId.of("America/Los_Angeles")
    private val noon = ZonedDateTime.of(2026, 9, 29, 12, 0, 0, 0, utc).toInstant()

    private fun mtg(title: String, start: Instant, minutes: Long, allDay: Boolean = false) = Meeting(
        key = "$title@$start", title = title, start = start, end = start.plus(Duration.ofMinutes(minutes)), allDay = allDay,
    )

    private fun at(base: Instant, minutes: Long, seconds: Long = 0) = base.plusSeconds(minutes * 60 + seconds)

    @Test
    fun nextMeetingIsPrimaryWithCountdown() {
        val a = mtg("A", at(noon, 30), 30)
        val b = mtg("B", at(noon, 90), 30)
        val s = Schedule.compute(noon, utc, listOf(b, a))
        assertEquals("A", s.primary?.title)
        assertEquals(listOf("B"), s.upcoming.map { it.title })
        assertEquals(CountdownState.Until(Duration.ofMinutes(30)), s.countdown)
        assertEquals(Urgency.NORMAL, s.urgency)
        assertTrue(!s.primaryInProgress)
    }

    @Test
    fun soonAtThresholdAndNotBefore() {
        val a = mtg("A", at(noon, 5), 30)
        assertEquals(Urgency.SOON, Schedule.compute(noon, utc, listOf(a)).urgency)
        val b = mtg("B", at(noon, 5, 1), 30)
        assertEquals(Urgency.NORMAL, Schedule.compute(noon, utc, listOf(b)).urgency)
    }

    @Test
    fun startingNowForFirstMinute() {
        val a = mtg("A", noon.minusSeconds(30), 30)
        val s = Schedule.compute(noon, utc, listOf(a))
        assertEquals("A", s.primary?.title)
        assertTrue(s.primaryInProgress)
        assertEquals(CountdownState.StartingNow, s.countdown)
        assertEquals(Urgency.NOW, s.urgency)
    }

    @Test
    fun startedMeetingStaysPrimaryDuringGraceThenNextTakesOver() {
        val a = mtg("A", at(noon, -3), 30)
        val b = mtg("B", at(noon, 40), 30)
        val early = Schedule.compute(noon, utc, listOf(a, b))
        assertEquals("A", early.primary?.title)
        assertEquals(CountdownState.InProgress(Duration.ofMinutes(27)), early.countdown)
        assertEquals(listOf("B"), early.upcoming.map { it.title })

        val late = Schedule.compute(at(noon, 8), utc, listOf(a, b))
        assertEquals("B", late.primary?.title)
        assertTrue(!late.primaryInProgress)
        assertTrue(late.upcoming.isEmpty())
    }

    @Test
    fun graceIsConfigurable() {
        val a = mtg("A", at(noon, -3), 30)
        val b = mtg("B", at(noon, 40), 30)
        val s = Schedule.compute(noon, utc, listOf(a, b), ScheduleConfig(graceMinutes = 2))
        assertEquals("B", s.primary?.title)
        val zero = Schedule.compute(noon, utc, listOf(mtg("A", noon, 30), b), ScheduleConfig(graceMinutes = 0))
        assertEquals("A", zero.primary?.title)
    }

    @Test
    fun backToBackHandsOverAtTheBoundary() {
        val a = mtg("A", at(noon, -30), 30)
        val b = mtg("B", noon, 30)
        val s = Schedule.compute(noon, utc, listOf(a, b))
        assertEquals("B", s.primary?.title)
        assertEquals(CountdownState.StartingNow, s.countdown)
    }

    @Test
    fun overlappingMeetingsUseEarliestStartAndKeepTheOtherListed() {
        val a = mtg("A", at(noon, -2), 60)
        val b = mtg("B", at(noon, -1), 60)
        val s = Schedule.compute(noon, utc, listOf(b, a))
        assertEquals("A", s.primary?.title)
        assertEquals(listOf("B"), s.upcoming.map { it.title })
    }

    @Test
    fun longRunningMeetingWithNothingNextStillShows() {
        val a = mtg("A", at(noon, -45), 120)
        val s = Schedule.compute(noon, utc, listOf(a))
        assertEquals("A", s.primary?.title)
        assertEquals(CountdownState.InProgress(Duration.ofMinutes(75)), s.countdown)
    }

    @Test
    fun allDayEventsNeverBecomePrimaryButAreReported() {
        val today = ZonedDateTime.of(2026, 9, 29, 0, 0, 0, 0, utc).toInstant()
        val holiday = mtg("OOO", today, 24 * 60, allDay = true)
        val s = Schedule.compute(noon, utc, listOf(holiday))
        assertNull(s.primary)
        assertEquals(CountdownState.NoMeetings, s.countdown)
        assertEquals(listOf("OOO"), s.allDay.map { it.title })
        val yesterdayOnly = mtg("Old", today.minus(Duration.ofDays(2)), 24 * 60, allDay = true)
        assertTrue(Schedule.compute(noon, utc, listOf(yesterdayOnly)).allDay.isEmpty())
    }

    @Test
    fun nothingLeftTodayShowsFreeAndTomorrowInUpcoming() {
        val tomorrow = ZonedDateTime.of(2026, 9, 30, 9, 0, 0, 0, utc).toInstant()
        val a = mtg("T1", tomorrow, 30)
        val b = mtg("T2", at(tomorrow, 60), 30)
        val s = Schedule.compute(noon, utc, listOf(a, b))
        assertNull(s.primary)
        assertEquals(CountdownState.FreeRestOfDay(a), s.countdown)
        assertEquals(listOf("T1", "T2"), s.upcoming.map { it.title })
        assertEquals(Urgency.NORMAL, s.urgency)
    }

    @Test
    fun midnightRolloverJustBeforeMidnight() {
        val now = ZonedDateTime.of(2026, 9, 29, 23, 50, 0, 0, utc).toInstant()
        val a = mtg("Early", ZonedDateTime.of(2026, 9, 30, 0, 10, 0, 0, utc).toInstant(), 30)
        val s = Schedule.compute(now, utc, listOf(a))
        assertNull(s.primary)
        assertEquals(CountdownState.FreeRestOfDay(a), s.countdown)
        // Ten minutes later it is "today" and the meeting is primary.
        val later = Schedule.compute(at(now, 15), utc, listOf(a))
        assertEquals("Early", later.primary?.title)
    }

    @Test
    fun emptyCalendar() {
        val s = Schedule.compute(noon, utc, emptyList())
        assertNull(s.primary)
        assertEquals(CountdownState.NoMeetings, s.countdown)
        assertTrue(s.upcoming.isEmpty())
    }

    @Test
    fun maxUpcomingIsRespected() {
        val list = (1..10).map { mtg("M$it", at(noon, 10L * it), 5) }
        val s = Schedule.compute(noon, utc, list, ScheduleConfig(maxUpcoming = 3))
        assertEquals("M1", s.primary?.title)
        assertEquals(listOf("M2", "M3", "M4"), s.upcoming.map { it.title })
    }

    @Test
    fun countdownAcrossSpringForwardUsesRealElapsedTime() {
        // 2026-03-08: clocks jump from 02:00 to 03:00 in Los Angeles.
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 3, 8, 1, 30), la).toInstant()
        val start = ZonedDateTime.of(LocalDateTime.of(2026, 3, 8, 3, 30), la).toInstant()
        val s = Schedule.compute(now, la, listOf(mtg("Spring", start, 30)))
        assertEquals(CountdownState.Until(Duration.ofHours(1)), s.countdown)
        assertNotNull(s.primary)
    }

    @Test
    fun todayIsDecidedInTheLocalZone() {
        // 23:30 in LA on Sep 29 is 06:30 on Sep 30 in UTC. A meeting at 00:10 LA time is after
        // local midnight (not today in LA) but on the same UTC date (today in UTC).
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 9, 29, 23, 30), la).toInstant()
        val start = ZonedDateTime.of(LocalDateTime.of(2026, 9, 30, 0, 10), la).toInstant()
        val m = mtg("Late", start, 5)
        assertNull(Schedule.compute(now, la, listOf(m)).primary)
        assertEquals("Late", Schedule.compute(now, utc, listOf(m)).primary?.title)
    }

    @Test
    fun zeroLengthEventsAreIgnored() {
        assertNull(Schedule.compute(noon, utc, listOf(mtg("Zero", at(noon, 5), 0))).primary)
    }
}
