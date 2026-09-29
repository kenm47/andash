package co.lunt.andash.domain

import java.time.Duration

/** What the countdown bar shows: a small [caption] above a large [value]. */
data class CountdownText(val caption: String, val value: String)

object Countdown {
    fun text(state: CountdownState): CountdownText = when (state) {
        is CountdownState.Until -> CountdownText("STARTS IN", format(state.remaining))
        CountdownState.StartingNow -> CountdownText("STARTING", "NOW")
        is CountdownState.InProgress -> CountdownText("ENDS IN", format(state.remaining))
        is CountdownState.FreeRestOfDay -> CountdownText("NO MORE MEETINGS TODAY", "Free")
        CountdownState.NoMeetings -> CountdownText("NO UPCOMING MEETINGS", "Clear")
    }

    /** `2d 4h`, `2h 15m`, or `12:34` under an hour. Seconds round up so the last tick reads 00:01. */
    fun format(remaining: Duration): String {
        val totalSeconds = ceilSeconds(remaining).coerceAtLeast(0)
        val days = totalSeconds / 86_400
        val hours = (totalSeconds % 86_400) / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "%02d:%02d".format(minutes, seconds)
        }
    }

    private fun ceilSeconds(d: Duration): Long {
        val secs = d.seconds
        return if (d.nano > 0) secs + 1 else secs
    }
}
