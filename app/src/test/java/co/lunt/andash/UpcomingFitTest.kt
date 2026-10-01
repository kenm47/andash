package co.lunt.andash

import co.lunt.andash.model.Meeting
import co.lunt.andash.ui.fitUpcoming
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class UpcomingFitTest {
    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 1)
    private fun m(title: String, day: Int, hour: Int) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, utc).toInstant().let {
        Meeting(key = title, title = title, start = it, end = it.plusSeconds(1800))
    }

    @Test
    fun cutsOffRowsThatDoNotFit() {
        val list = (1..6).map { m("T$it", 1, 10 + it) }
        // Each row costs 8 + 25 = 33dp at fontScale 1.
        assertEquals(3, fitUpcoming(list, today, utc, 100f, 1f).size)
        assertEquals(0, fitUpcoming(list, today, utc, 20f, 1f).size)
        assertEquals(6, fitUpcoming(list, today, utc, 1000f, 1f).size)
    }

    @Test
    fun aDayHeaderIsCountedWithItsRow() {
        val list = listOf(m("Today", 1, 15), m("Tomorrow", 2, 9))
        // Row 33 + (header 27 + row 33) = 93: both fit; 92 leaves out the second.
        assertEquals(2, fitUpcoming(list, today, utc, 93f, 1f).size)
        assertEquals(1, fitUpcoming(list, today, utc, 92f, 1f).size)
    }

    @Test
    fun largerFontsFitFewerRows() {
        val list = (1..8).map { m("T$it", 1, 9 + it) }
        val normal = fitUpcoming(list, today, utc, 200f, 1f).size
        val large = fitUpcoming(list, today, utc, 200f, 1.5f).size
        assertEquals(true, large < normal)
    }
}
