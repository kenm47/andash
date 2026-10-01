package co.lunt.andash.ui

import co.lunt.andash.model.Meeting
import java.time.LocalDate
import java.time.ZoneId

/**
 * How many upcoming rows fit in [heightDp] without a half-cut row at the bottom. Rows and day
 * headers have rough heights that grow with the user's font scale; a header is only counted
 * together with the row it introduces.
 */
fun fitUpcoming(
    meetings: List<Meeting>,
    today: LocalDate,
    zone: ZoneId,
    heightDp: Float,
    fontScale: Float,
): List<Meeting> {
    val rowCost = 8f + 25f * fontScale
    val headerCost = 10f + 17f * fontScale
    var used = 0f
    var lastDay = today
    val shown = mutableListOf<Meeting>()
    for (m in meetings) {
        val day = m.start.atZone(zone).toLocalDate()
        val cost = rowCost + if (day != lastDay) headerCost else 0f
        if (used + cost > heightDp) break
        used += cost
        lastDay = day
        shown += m
    }
    return shown
}
