package co.lunt.andash

import co.lunt.andash.model.CalendarInfo
import co.lunt.andash.settings.AppSettings
import co.lunt.andash.ui.TimeFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class FormatsAndSettingsTest {
    private val utc = ZoneId.of("UTC")
    private fun t(h: Int, m: Int) = ZonedDateTime.of(2026, 9, 29, h, m, 0, 0, utc).toInstant()
    private val f12 = TimeFormats(false, Locale.US)
    private val f24 = TimeFormats(true, Locale.US)

    @Test
    fun clockFollowsTheTwelveOrTwentyFourHourSetting() {
        assertEquals("3:05", f12.clock(t(15, 5), utc))
        assertEquals("PM", f12.ampm(t(15, 5), utc))
        assertEquals("15:05", f24.clock(t(15, 5), utc))
        assertEquals("", f24.ampm(t(15, 5), utc))
    }

    @Test
    fun rangeSharesAmPmWhenPossible() {
        assertEquals("3:30 – 4:00 PM", f12.range(t(15, 30), t(16, 0), utc))
        assertEquals("11:30 AM – 12:15 PM", f12.range(t(11, 30), t(12, 15), utc))
        assertEquals("15:30 – 16:00", f24.range(t(15, 30), t(16, 0), utc))
    }

    @Test
    fun dayLabels() {
        val today = LocalDate.of(2026, 9, 29)
        assertEquals("Today", f12.dayLabel(today, today))
        assertEquals("Tomorrow", f12.dayLabel(today.plusDays(1), today))
        assertEquals("Fri 2 Oct", f12.dayLabel(today.plusDays(3), today))
    }

    private fun cal(id: Long, name: String, visible: Boolean = true) =
        CalendarInfo(id, "a@x.com", "com.google", name, 0, visible, true)

    @Test
    fun defaultEnablementSkipsHolidaysBirthdaysAndHidden() {
        assertTrue(cal(1, "Work").defaultEnabled)
        assertFalse(cal(2, "Holidays in United States").defaultEnabled)
        assertFalse(cal(3, "Birthdays").defaultEnabled)
        assertFalse(cal(4, "Work", visible = false).defaultEnabled)
    }

    @Test
    fun explicitChoicesOverrideDefaults() {
        val s = AppSettings(explicitlyOn = setOf(2), explicitlyOff = setOf(1))
        assertTrue(s.isEnabled(cal(2, "Holidays")))
        assertFalse(s.isEnabled(cal(1, "Work")))
        assertTrue(s.isEnabled(cal(9, "Other")))
    }
}
