package co.lunt.andash

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Creates throwaway calendars in LOCAL accounts (never synced to Google) so tests can run on a
 * real phone without touching real calendars. Everything is removed by [cleanUp].
 */
class TestCalendars(private val context: Context) {
    companion object {
        const val PREFIX = "andash-test-"
        const val DOMAIN = "@example.invalid"
        fun account(n: Int) = "$PREFIX$n$DOMAIN"

        /** Permissions come from `installOptions += "-g"` in build.gradle.kts; fail clearly if missing. */
        fun grantPermissions() {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            for (perm in listOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR)) {
                check(ctx.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    "app lacks $perm; install with -g"
                }
            }
        }
    }

    private fun syncUri(base: Uri, account: String? = null): Uri = base.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, account ?: "unused")
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    fun addCalendar(n: Int, displayName: String = "Cal $n", color: Int = 0xFF000000.toInt() or (n * 0x2A1F33)): Long {
        val account = account(n)
        val v = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, account)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, displayName)
            put(Calendars.CALENDAR_DISPLAY_NAME, displayName)
            put(Calendars.CALENDAR_COLOR, color)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, account)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
        }
        val uri = context.contentResolver.insert(syncUri(Calendars.CONTENT_URI, account), v)!!
        return ContentUris.parseId(uri)
    }

    fun addEvent(
        calendarId: Long,
        account: Int,
        title: String?,
        start: Instant,
        minutes: Long = 30,
        uid: String? = null,
        description: String? = null,
        location: String? = null,
        allDay: Boolean = false,
        free: Boolean = false,
        canceled: Boolean = false,
        rrule: String? = null,
    ): Long {
        val v = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            if (title != null) put(Events.TITLE, title)
            put(Events.DTSTART, start.toEpochMilli())
            if (rrule != null) {
                put(Events.RRULE, rrule)
                put(Events.DURATION, "PT${minutes}M")
            } else {
                put(Events.DTEND, start.plus(Duration.ofMinutes(minutes)).toEpochMilli())
            }
            put(Events.EVENT_TIMEZONE, if (allDay) "UTC" else "UTC")
            put(Events.ALL_DAY, if (allDay) 1 else 0)
            description?.let { put(Events.DESCRIPTION, it) }
            location?.let { put(Events.EVENT_LOCATION, it) }
            uid?.let { put(Events.UID_2445, it) }
            put(Events.AVAILABILITY, if (free) Events.AVAILABILITY_FREE else Events.AVAILABILITY_BUSY)
            if (canceled) put(Events.STATUS, Events.STATUS_CANCELED)
        }
        val uri = context.contentResolver.insert(syncUri(Events.CONTENT_URI, account(account)), v)!!
        return ContentUris.parseId(uri)
    }

    fun addAllDayEvent(calendarId: Long, account: Int, title: String, day: LocalDate): Long =
        addEvent(
            calendarId, account, title,
            start = day.atStartOfDay(ZoneOffset.UTC).toInstant(), minutes = 24 * 60, allDay = true,
        )

    fun decline(eventId: Long, account: Int) {
        val v = ContentValues().apply {
            put(Attendees.EVENT_ID, eventId)
            put(Attendees.ATTENDEE_EMAIL, account(account))
            put(Attendees.ATTENDEE_NAME, "Test")
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
            put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_DECLINED)
        }
        context.contentResolver.insert(syncUri(Attendees.CONTENT_URI, account(account)), v)
    }

    /** Deletes every test calendar (and, by cascade, its events). */
    fun cleanUp() {
        val rows = context.contentResolver.query(
            Calendars.CONTENT_URI, arrayOf(Calendars._ID, Calendars.ACCOUNT_NAME),
            "${Calendars.ACCOUNT_TYPE} = ? AND ${Calendars.ACCOUNT_NAME} LIKE ?",
            arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, "$PREFIX%"), null,
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) } }.orEmpty()
        for ((id, account) in rows) {
            context.contentResolver.delete(
                syncUri(ContentUris.withAppendedId(Calendars.CONTENT_URI, id), account), null, null,
            )
        }
    }

    fun leftovers(): Int = context.contentResolver.query(
        Calendars.CONTENT_URI, arrayOf(Calendars._ID),
        "${Calendars.ACCOUNT_TYPE} = ? AND ${Calendars.ACCOUNT_NAME} LIKE ?",
        arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, "$PREFIX%"), null,
    )?.use { it.count } ?: 0
}
