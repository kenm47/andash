package co.lunt.andash.domain

import co.lunt.andash.model.Meeting
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class ScheduleConfig(
    /** How long a started meeting stays the primary card. */
    val graceMinutes: Int = 10,
    /** At or below this many minutes before a meeting the urgency becomes SOON. */
    val soonMinutes: Int = 5,
    val maxUpcoming: Int = 5,
)

enum class Urgency { NORMAL, SOON, NOW }

sealed interface CountdownState {
    /** The primary meeting has not started; [remaining] until it does. */
    data class Until(val remaining: Duration) : CountdownState

    /** The primary meeting started less than a minute ago. */
    data object StartingNow : CountdownState

    /** The primary meeting is running; [remaining] until it ends. */
    data class InProgress(val remaining: Duration) : CountdownState

    /** Nothing more today. [next] is the first later meeting, if any. */
    data class FreeRestOfDay(val next: Meeting?) : CountdownState

    data object NoMeetings : CountdownState
}

data class DashboardState(
    val primary: Meeting?,
    val primaryInProgress: Boolean,
    val upcoming: List<Meeting>,
    /** All-day events overlapping today. */
    val allDay: List<Meeting>,
    val countdown: CountdownState,
    val urgency: Urgency,
)

object Schedule {
    private val STARTING_NOW_WINDOW: Duration = Duration.ofSeconds(60)

    fun compute(
        now: Instant,
        zone: ZoneId,
        meetings: List<Meeting>,
        config: ScheduleConfig = ScheduleConfig(),
    ): DashboardState {
        val timed = meetings.filter { !it.allDay && it.end > it.start }
            .sortedWith(compareBy({ it.start }, { it.end }, { it.title }))
        val grace = Duration.ofMinutes(config.graceMinutes.toLong())

        val running = timed.filter { it.start <= now && now < it.end }
        val future = timed.filter { it.start > now }
        val inGrace = running.filter { Duration.between(it.start, now) <= grace }

        // A meeting that just started wins; then the next one if it is today; a long-running
        // meeting with nothing sooner is still better than an empty card.
        val todayDate = now.atZone(zone).toLocalDate()
        val next = future.firstOrNull()
        val nextIsToday = next != null && next.start.atZone(zone).toLocalDate() == todayDate
        val primary: Meeting? = inGrace.firstOrNull()
            ?: if (nextIsToday) next else running.firstOrNull()

        val primaryInProgress = primary != null && primary.start <= now

        val upcoming = timed.filter { m ->
            m != primary && (m.start > now || m in inGrace)
        }.take(config.maxUpcoming)

        val allDay = meetings.filter { it.allDay }.filter {
            val startDate = it.start.atZone(zone).toLocalDate()
            val endDate = it.end.atZone(zone).toLocalDate()
            startDate <= todayDate && todayDate < endDate
        }.distinctBy { it.key }

        val countdown: CountdownState
        val urgency: Urgency
        if (primary == null) {
            countdown = if (next == null) CountdownState.NoMeetings else CountdownState.FreeRestOfDay(next)
            urgency = Urgency.NORMAL
        } else if (primaryInProgress) {
            val sinceStart = Duration.between(primary.start, now)
            countdown = if (sinceStart < STARTING_NOW_WINDOW) {
                CountdownState.StartingNow
            } else {
                CountdownState.InProgress(Duration.between(now, primary.end))
            }
            urgency = Urgency.NOW
        } else {
            val remaining = Duration.between(now, primary.start)
            countdown = CountdownState.Until(remaining)
            urgency = if (remaining <= Duration.ofMinutes(config.soonMinutes.toLong())) Urgency.SOON else Urgency.NORMAL
        }

        return DashboardState(
            primary = primary,
            primaryInProgress = primaryInProgress,
            upcoming = upcoming,
            allDay = allDay,
            countdown = countdown,
            urgency = urgency,
        )
    }
}
