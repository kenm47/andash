package co.lunt.andash

import androidx.test.platform.app.InstrumentationRegistry
import co.lunt.andash.data.DeviceCalendarSource
import co.lunt.andash.data.LoadOptions
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DeviceCalendarSourceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val cal = TestCalendars(inst.context)
    private val source = DeviceCalendarSource(inst.targetContext)
    private val now = Instant.now()
    private val from = now.minus(Duration.ofDays(1))
    private val to = now.plus(Duration.ofDays(7))

    private fun min(m: Long) = now.plus(Duration.ofMinutes(m))
    private fun testOnly() = LoadOptions(isEnabled = { it.accountName.startsWith(TestCalendars.PREFIX) })

    @Before
    fun setUp() {
        TestCalendars.grantPermissions()
        cal.cleanUp()
    }

    @After
    fun tearDown() {
        cal.cleanUp()
        assertEquals("test calendars must be removed", 0, cal.leftovers())
    }

    @Test
    fun listsCalendarsFromSixAccounts() = runBlocking {
        val ids = (1..6).map { cal.addCalendar(it, "Calendar $it") }
        val found = source.calendars().filter { it.accountName.startsWith(TestCalendars.PREFIX) }
        assertEquals(ids.toSet(), found.map { it.id }.toSet())
        assertEquals(6, found.map { it.accountName }.distinct().size)
    }

    @Test
    fun readsMeetingsFromEverySixAccountsAndSortsThem() = runBlocking {
        for (n in 1..6) {
            val id = cal.addCalendar(n)
            cal.addEvent(id, n, "Meeting from $n", min(10L * (7 - n)), uid = "distinct-$n")
        }
        val out = source.load(from, to, testOnly())
        assertEquals(6, out.size)
        assertEquals((1..6).map { "Meeting from $it" }.toSet(), out.map { it.title }.toSet())
        assertEquals(out.map { it.start }.sorted(), out.map { it.start })
    }

    @Test
    fun sameMeetingOnTwoAccountsIsMergedIntoOne() = runBlocking {
        val a = cal.addCalendar(1)
        val b = cal.addCalendar(2)
        cal.addEvent(a, 1, "Shared sync", min(30), uid = "shared-uid-1")
        cal.addEvent(b, 2, "Shared sync", min(30), uid = "shared-uid-1")
        cal.addEvent(b, 2, "Only on two", min(90), uid = "solo-uid")
        val out = source.load(from, to, testOnly())
        assertEquals(2, out.size)
        val shared = out.first { it.title == "Shared sync" }
        assertEquals(setOf(TestCalendars.account(1), TestCalendars.account(2)), shared.accounts.toSet())
    }

    @Test
    fun declinedCanceledAndFreeAreFilteredAsConfigured() = runBlocking {
        val a = cal.addCalendar(1)
        cal.addEvent(a, 1, "Keep me", min(20))
        val declined = cal.addEvent(a, 1, "Declined", min(40))
        cal.decline(declined, 1)
        cal.addEvent(a, 1, "Canceled", min(60), canceled = true)
        cal.addEvent(a, 1, "Free block", min(80), free = true)

        val default = source.load(from, to, testOnly()).map { it.title }
        assertTrue("Keep me" in default)
        assertFalse("Declined" in default)
        assertFalse("Canceled" in default)
        assertTrue("Free block" in default)

        val hideFree = source.load(from, to, LoadOptions(isEnabled = testOnly().isEnabled, hideFree = true)).map { it.title }
        assertFalse("Free block" in hideFree)
        assertTrue("Keep me" in hideFree)
    }

    @Test
    fun disabledCalendarsAreExcluded() = runBlocking {
        val a = cal.addCalendar(1, "Keep")
        val b = cal.addCalendar(2, "Drop")
        cal.addEvent(a, 1, "From keep", min(20))
        cal.addEvent(b, 2, "From drop", min(25))
        val out = source.load(from, to, LoadOptions(isEnabled = { it.id == a })).map { it.title }
        assertEquals(listOf("From keep"), out)
    }

    @Test
    fun holidayAndBirthdayCalendarsAreOffByDefault() = runBlocking {
        val h = cal.addCalendar(1, "Holidays in Testland")
        val w = cal.addCalendar(2, "Work")
        cal.addEvent(h, 1, "Public holiday", min(30))
        cal.addEvent(w, 2, "Real meeting", min(40))
        val out = source.load(from, to, LoadOptions(isEnabled = { it.accountName.startsWith(TestCalendars.PREFIX) && it.defaultEnabled }))
        assertEquals(listOf("Real meeting"), out.map { it.title })
    }

    @Test
    fun recurringEventsExpandIntoInstances() = runBlocking {
        val a = cal.addCalendar(1)
        cal.addEvent(a, 1, "Daily standup", min(30), rrule = "FREQ=DAILY;COUNT=3", uid = "recur")
        val out = source.load(from, to, testOnly()).filter { it.title == "Daily standup" }
        assertEquals(3, out.size)
        assertEquals(3, out.map { it.key }.distinct().size)
    }

    @Test
    fun allDayEventsAreAnchoredToLocalMidnight() = runBlocking {
        val a = cal.addCalendar(1)
        val today = LocalDate.now()
        cal.addAllDayEvent(a, 1, "Out of office", today)
        val out = source.load(from, to, testOnly()).first { it.title == "Out of office" }
        assertTrue(out.allDay)
        val zone = java.time.ZoneId.systemDefault()
        assertEquals(today, out.start.atZone(zone).toLocalDate())
        assertEquals(today.plusDays(1), out.end.atZone(zone).toLocalDate())
        assertEquals(0, out.start.atZone(zone).hour)
    }

    @Test
    fun extractsMeetingLinksAndKeepsLocation() = runBlocking {
        val a = cal.addCalendar(1)
        cal.addEvent(a, 1, "With link", min(30), description = "Join https://meet.google.com/abc-defg-hij", location = "Room 2")
        cal.addEvent(a, 1, "Zoom loc", min(90), location = "https://acme.zoom.us/j/999")
        val out = source.load(from, to, testOnly()).associateBy { it.title }
        assertEquals("Meet", out.getValue("With link").link?.kind)
        assertEquals("Room 2", out.getValue("With link").location)
        assertEquals("Zoom", out.getValue("Zoom loc").link?.kind)
    }

    @Test
    fun untitledEventsGetAPlaceholder() = runBlocking {
        val a = cal.addCalendar(1)
        cal.addEvent(a, 1, null, min(30))
        assertEquals("(No title)", source.load(from, to, testOnly()).single().title)
    }

    @Test
    fun windowExcludesEventsOutsideTheRange() = runBlocking {
        val a = cal.addCalendar(1)
        cal.addEvent(a, 1, "Far future", now.plus(Duration.ofDays(30)))
        cal.addEvent(a, 1, "Soon", min(30))
        val out = source.load(from, to, testOnly()).map { it.title }
        assertEquals(listOf("Soon"), out)
    }

    @Test
    fun sourceReportsChangesWhenEventsAreWritten() = runBlocking {
        val a = cal.addCalendar(1)
        val got = kotlinx.coroutines.withTimeoutOrNull(8_000) {
            val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).let { scope ->
                scope.async { source.changes().first() }
            }
            kotlinx.coroutines.delay(500)
            cal.addEvent(a, 1, "Trigger", min(30))
            job.await()
        }
        assertNotNull("expected a change notification", got)
    }

    @Test
    fun noEnabledCalendarsMeansNoMeetings() = runBlocking {
        cal.addCalendar(1)
        assertTrue(source.load(from, to, LoadOptions(isEnabled = { false })).isEmpty())
        assertNull(null)
    }
}
