package co.lunt.andash.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** All user-visible time text, following the phone's 12/24h setting. */
class TimeFormats(private val is24h: Boolean, private val locale: Locale = Locale.getDefault()) {
    private val clock = DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm", locale)
    private val ampm = DateTimeFormatter.ofPattern("a", locale)
    private val short = DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm a", locale)
    private val shortNoSuffix = DateTimeFormatter.ofPattern("h:mm", locale)
    private val date = DateTimeFormatter.ofPattern("EEEE, MMMM d", locale)
    private val dayHeader = DateTimeFormatter.ofPattern("EEE d MMM", locale)

    fun clock(now: Instant, zone: ZoneId): String = clock.format(now.atZone(zone))
    fun ampm(now: Instant, zone: ZoneId): String = if (is24h) "" else ampm.format(now.atZone(zone))
    fun date(now: Instant, zone: ZoneId): String = date.format(now.atZone(zone))
    fun time(t: Instant, zone: ZoneId): String = short.format(t.atZone(zone))

    /** `3:30 – 4:00 PM` when both ends share AM/PM, otherwise full times on both ends. */
    fun range(start: Instant, end: Instant, zone: ZoneId): String {
        val s = start.atZone(zone)
        val e = end.atZone(zone)
        if (is24h) return "${short.format(s)} – ${short.format(e)}"
        val sameHalf = ampm.format(s) == ampm.format(e)
        return if (sameHalf) "${shortNoSuffix.format(s)} – ${short.format(e)}" else "${short.format(s)} – ${short.format(e)}"
    }

    fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> dayHeader.format(day)
    }
}
