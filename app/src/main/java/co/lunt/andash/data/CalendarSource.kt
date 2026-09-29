package co.lunt.andash.data

import co.lunt.andash.model.CalendarInfo
import co.lunt.andash.model.Meeting
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class LoadOptions(
    val isEnabled: (CalendarInfo) -> Boolean = { it.defaultEnabled },
    val hideFree: Boolean = false,
)

/** Where meetings come from. The device calendar is the primary source; others can be added. */
interface CalendarSource {
    suspend fun calendars(): List<CalendarInfo>

    /** Meetings overlapping [from, to), already filtered and de-duplicated across accounts. */
    suspend fun load(from: Instant, to: Instant, options: LoadOptions = LoadOptions()): List<Meeting>

    /** Emits whenever the underlying data may have changed. */
    fun changes(): Flow<Unit>

    /** Asks the sync framework to refresh; returns how many accounts were asked. */
    suspend fun requestSync(): Int = 0
}
