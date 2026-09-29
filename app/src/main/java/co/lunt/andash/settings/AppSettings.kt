package co.lunt.andash.settings

import co.lunt.andash.domain.ScheduleConfig
import co.lunt.andash.model.CalendarInfo

data class AppSettings(
    val lookAheadDays: Int = 7,
    val maxUpcoming: Int = 5,
    val graceMinutes: Int = 10,
    val soonMinutes: Int = 5,
    val hideFree: Boolean = false,
    val showAllDayChips: Boolean = true,
    val nightEnabled: Boolean = true,
    val nightStartMinute: Int = 22 * 60,
    val nightEndMinute: Int = 7 * 60,
    val nightClockOnly: Boolean = false,
    /** Calendars the user switched on/off explicitly; everything else follows [CalendarInfo.defaultEnabled]. */
    val explicitlyOn: Set<Long> = emptySet(),
    val explicitlyOff: Set<Long> = emptySet(),
    /** Debug only: shift "now" to test time-dependent behavior. */
    val debugOffsetMinutes: Int = 0,
) {
    fun isEnabled(c: CalendarInfo): Boolean = when (c.id) {
        in explicitlyOn -> true
        in explicitlyOff -> false
        else -> c.defaultEnabled
    }

    val scheduleConfig: ScheduleConfig
        get() = ScheduleConfig(graceMinutes = graceMinutes, soonMinutes = soonMinutes, maxUpcoming = maxUpcoming)
}
