package co.lunt.andash.domain

import java.time.LocalTime

/** A daily window that may cross midnight, e.g. 22:00 to 07:00. Minutes are since midnight. */
object NightWindow {
    fun isActive(time: LocalTime, startMinute: Int, endMinute: Int): Boolean {
        val minute = time.hour * 60 + time.minute
        return when {
            startMinute == endMinute -> false
            startMinute < endMinute -> minute in startMinute until endMinute
            else -> minute >= startMinute || minute < endMinute
        }
    }
}
