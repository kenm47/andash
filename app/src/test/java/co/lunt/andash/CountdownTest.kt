package co.lunt.andash

import co.lunt.andash.domain.Countdown
import co.lunt.andash.domain.CountdownState
import co.lunt.andash.domain.CountdownText
import co.lunt.andash.domain.NightWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalTime

class CountdownTest {
    @Test
    fun underAnHourShowsMinutesAndSeconds() {
        assertEquals("12:34", Countdown.format(Duration.ofSeconds(12 * 60 + 34)))
        assertEquals("59:59", Countdown.format(Duration.ofSeconds(3599)))
        assertEquals("00:00", Countdown.format(Duration.ZERO))
    }

    @Test
    fun partialSecondsRoundUp() {
        assertEquals("00:01", Countdown.format(Duration.ofMillis(200)))
        assertEquals("12:35", Countdown.format(Duration.ofSeconds(12 * 60 + 34).plusMillis(1)))
    }

    @Test
    fun hoursAndDays() {
        assertEquals("1h 0m", Countdown.format(Duration.ofHours(1)))
        assertEquals("2h 15m", Countdown.format(Duration.ofMinutes(135).plusSeconds(20)))
        assertEquals("1d 3h", Countdown.format(Duration.ofHours(27)))
    }

    @Test
    fun negativeIsClamped() {
        assertEquals("00:00", Countdown.format(Duration.ofSeconds(-5)))
    }

    @Test
    fun textForEachState() {
        assertEquals(CountdownText("STARTS IN", "05:00"), Countdown.text(CountdownState.Until(Duration.ofMinutes(5))))
        assertEquals(CountdownText("STARTING", "NOW"), Countdown.text(CountdownState.StartingNow))
        assertEquals(CountdownText("ENDS IN", "18:00"), Countdown.text(CountdownState.InProgress(Duration.ofMinutes(18))))
        assertEquals("Free", Countdown.text(CountdownState.FreeRestOfDay(null)).value)
        assertEquals("Clear", Countdown.text(CountdownState.NoMeetings).value)
    }

    @Test
    fun nightWindowWithinADay() {
        assertTrue(NightWindow.isActive(LocalTime.of(3, 0), 1 * 60, 5 * 60))
        assertFalse(NightWindow.isActive(LocalTime.of(5, 0), 1 * 60, 5 * 60))
    }

    @Test
    fun nightWindowAcrossMidnight() {
        val start = 22 * 60
        val end = 7 * 60
        assertTrue(NightWindow.isActive(LocalTime.of(23, 30), start, end))
        assertTrue(NightWindow.isActive(LocalTime.of(0, 0), start, end))
        assertTrue(NightWindow.isActive(LocalTime.of(6, 59), start, end))
        assertFalse(NightWindow.isActive(LocalTime.of(7, 0), start, end))
        assertFalse(NightWindow.isActive(LocalTime.of(12, 0), start, end))
        assertTrue(NightWindow.isActive(LocalTime.of(22, 0), start, end))
    }

    @Test
    fun emptyNightWindowIsNeverActive() {
        assertFalse(NightWindow.isActive(LocalTime.of(3, 0), 300, 300))
    }
}
