package co.lunt.andash.data

import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import co.lunt.andash.domain.MeetingLinks
import co.lunt.andash.domain.dedupe
import co.lunt.andash.domain.meetingKey
import co.lunt.andash.model.CalendarInfo
import co.lunt.andash.model.Meeting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Reads every calendar Android has synced onto the phone (all Google accounts share one
 * database), so no OAuth or server is needed.
 */
class DeviceCalendarSource(
    private val context: Context,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : CalendarSource {
    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun calendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            Calendars._ID, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_DISPLAY_NAME, Calendars.CALENDAR_COLOR,
            Calendars.VISIBLE, Calendars.SYNC_EVENTS,
        )
        val out = mutableListOf<CalendarInfo>()
        resolver.query(
            Calendars.CONTENT_URI, projection, null, null,
            "${Calendars.ACCOUNT_NAME} ASC, ${Calendars.CALENDAR_DISPLAY_NAME} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                out += CalendarInfo(
                    id = c.getLong(0),
                    accountName = c.getString(1).orEmpty(),
                    accountType = c.getString(2).orEmpty(),
                    displayName = c.getString(3).orEmpty(),
                    color = c.getInt(4),
                    visible = c.getInt(5) != 0,
                    syncEnabled = c.getInt(6) != 0,
                )
            }
        }
        out
    }

    override suspend fun load(from: Instant, to: Instant, options: LoadOptions): List<Meeting> =
        withContext(Dispatchers.IO) {
            val enabled = calendars().filter(options.isEnabled).associateBy { it.id }
            if (enabled.isEmpty()) return@withContext emptyList()

            val builder: Uri.Builder = Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, from.toEpochMilli())
            ContentUris.appendId(builder, to.toEpochMilli())

            val projection = arrayOf(
                Instances.EVENT_ID, // 0
                Instances.BEGIN, // 1
                Instances.END, // 2
                Instances.TITLE, // 3
                Instances.EVENT_LOCATION, // 4
                Instances.DESCRIPTION, // 5
                Instances.ALL_DAY, // 6
                Instances.CALENDAR_ID, // 7
                Instances.SELF_ATTENDEE_STATUS, // 8
                Instances.AVAILABILITY, // 9
                Instances.STATUS, // 10
                Instances.UID_2445, // 11
            )
            val meetings = mutableListOf<Meeting>()
            resolver.query(builder.build(), projection, null, null, "${Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val calendar = enabled[c.getLong(7)] ?: continue
                    if (c.getIntOrZero(8) == Attendees.ATTENDEE_STATUS_DECLINED) continue
                    if (c.getIntOrZero(10) == Events.STATUS_CANCELED) continue
                    if (options.hideFree && c.getIntOrZero(9) == Events.AVAILABILITY_FREE) continue

                    val allDay = c.getIntOrZero(6) != 0
                    val begin = c.getLong(1)
                    val end = c.getLong(2)
                    val start = if (allDay) localMidnightOfUtcDate(begin) else Instant.ofEpochMilli(begin)
                    val stop = if (allDay) localMidnightOfUtcDate(end) else Instant.ofEpochMilli(end)
                    val title = c.getString(3)?.takeIf { it.isNotBlank() } ?: "(No title)"
                    val location = c.getString(4)?.takeIf { it.isNotBlank() }
                    val description = c.getString(5)
                    meetings += Meeting(
                        key = meetingKey(c.getString(11), title, start.toEpochMilli(), stop.toEpochMilli()),
                        title = title,
                        start = start,
                        end = stop,
                        location = location,
                        link = MeetingLinks.find(location, description),
                        accounts = listOf(calendar.accountName),
                        color = calendar.color,
                        allDay = allDay,
                    )
                }
            }
            dedupe(meetings)
        }

    /** All-day events are stored as UTC midnight dates; re-anchor them to local midnight. */
    private fun localMidnightOfUtcDate(millis: Long): Instant =
        LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC).atStartOfDay(zone()).toInstant()

    private fun Cursor.getIntOrZero(i: Int): Int = if (isNull(i)) 0 else getInt(i)

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.debounce(1_000)

    override suspend fun requestSync(): Int = withContext(Dispatchers.IO) {
        var asked = 0
        val accounts = calendars()
            .filter { it.syncEnabled && it.accountType == "com.google" }
            .map { Account(it.accountName, it.accountType) }
            .distinct()
        for (account in accounts) {
            val extras = Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            try {
                ContentResolver.requestSync(account, CalendarContract.AUTHORITY, extras)
                asked++
            } catch (_: SecurityException) {
                // Not allowed for this account; the periodic sync will still run.
            }
        }
        asked
    }
}
