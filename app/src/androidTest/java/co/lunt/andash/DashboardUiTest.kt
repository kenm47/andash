package co.lunt.andash

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Drives the real app on the phone against throwaway LOCAL calendars. */
class DashboardUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val container = (inst.targetContext.applicationContext as AndashApp).container
    private val cal = TestCalendars(inst.context)
    private val now = Instant.now()
    private fun min(m: Long) = now.plus(Duration.ofMinutes(m))

    @Before
    fun setUp() {
        TestCalendars.grantPermissions()
        cal.cleanUp()
        runBlocking { container.settings.clearAll() }
        isolateFromRealCalendars()
    }

    @After
    fun tearDown() {
        cal.cleanUp()
        runBlocking { container.settings.clearAll() }
        assertEquals(0, cal.leftovers())
    }

    /** Switch off every real calendar so only the test calendars feed the dashboard. */
    private fun isolateFromRealCalendars() = runBlocking {
        for (c in container.source.calendars()) {
            if (!c.accountName.startsWith(TestCalendars.PREFIX)) container.settings.setCalendarEnabled(c.id, false)
        }
    }

    private fun text(tag: String): String? {
        val nodes = rule.onAllNodesWithTag(tag).fetchSemanticsNodes()
        return nodes.firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
    }

    private fun waitForText(tag: String, timeoutMs: Long = 15_000, predicate: (String) -> Boolean) {
        rule.waitUntil(timeoutMs) { text(tag)?.let(predicate) == true }
    }

    private fun node(tag: String): SemanticsNodeInteraction = rule.onNodeWithTag(tag)

    @Test
    fun showsNextMeetingUpcomingAndCountdown() {
        val a = cal.addCalendar(1); val b = cal.addCalendar(2); val c = cal.addCalendar(3)
        cal.addEvent(a, 1, "Alpha planning", min(20), 30, location = "Room 9")
        cal.addEvent(b, 2, "Beta review", min(60))
        cal.addEvent(c, 3, "Gamma sync", min(100), description = "https://meet.google.com/aaa-bbbb-ccc")

        waitForText("next_title") { it == "Alpha planning" }
        assertClockShowsCurrentTime()
        assertEquals("NEXT UP", text("next_label"))
        assertTrue(text("next_tags")!!.contains("Room 9"))
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("upcoming_row").fetchSemanticsNodes().size == 2 }
        assertEquals("STARTS IN", text("countdown_caption"))
        assertTrue("countdown was ${text("countdown_value")}", Regex("""\d{2}:\d{2}""").matches(text("countdown_value")!!))
        // 20 minutes away: between 19:00 and 20:00.
        assertTrue(text("countdown_value")!!.substringBefore(':').toInt() in 18..20)
    }

    private fun assertClockShowsCurrentTime() {
        val clock = rule.onNodeWithTag("clock", useUnmergedTree = true).fetchSemanticsNode()
        val shown = clock.config.getOrNull(SemanticsProperties.Text)!!.joinToString("") { it.text }
        val is24h = android.text.format.DateFormat.is24HourFormat(inst.targetContext)
        val fmt = java.time.format.DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm")
        val now = java.time.LocalTime.now()
        val acceptable = setOf(fmt.format(now), fmt.format(now.minusMinutes(1)), fmt.format(now.plusMinutes(1)))
        assertTrue("clock shows $shown, expected one of $acceptable", shown in acceptable)
        assertTrue("clock too small: ${clock.boundsInRoot}", clock.boundsInRoot.width > 200f && clock.boundsInRoot.height > 100f)
    }

    @Test
    fun countdownTicksEverySecond() {
        val a = cal.addCalendar(1)
        cal.addEvent(a, 1, "Ticker", min(20))
        waitForText("countdown_value") { Regex("""\d{2}:\d{2}""").matches(it) }
        val first = text("countdown_value")!!
        rule.waitUntil(5_000) { text("countdown_value") != first }
    }

    @Test
    fun meetingInProgressIsMarkedNow() {
        val a = cal.addCalendar(1)
        cal.addEvent(a, 1, "Already started", min(-3), 30)
        cal.addEvent(a, 1, "Up next", min(45), 30)
        waitForText("next_title") { it == "Already started" }
        assertEquals("HAPPENING NOW", text("next_label"))
        assertEquals("ENDS IN", text("countdown_caption"))
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("upcoming_row").fetchSemanticsNodes().size == 1 }
    }

    @Test
    fun sameMeetingOnTwoAccountsAppearsOnce() {
        val a = cal.addCalendar(1); val b = cal.addCalendar(2)
        cal.addEvent(a, 1, "Shared", min(25), uid = "ui-shared")
        cal.addEvent(b, 2, "Shared", min(25), uid = "ui-shared")
        waitForText("next_title") { it == "Shared" }
        val accounts = text("next_accounts")!!
        assertTrue(accounts, accounts.contains("andash-test-1") && accounts.contains("andash-test-2"))
        assertEquals(0, rule.onAllNodesWithTag("upcoming_row").fetchSemanticsNodes().size)
    }

    @Test
    fun emptyStateWhenNothingIsScheduled() {
        cal.addCalendar(1)
        waitForText("countdown_value") { it == "Clear" }
        assertEquals("No upcoming meetings", text("next_empty"))
    }

    @Test
    fun newEventAppearsWithoutRestart() {
        val a = cal.addCalendar(1)
        waitForText("countdown_value") { it == "Clear" }
        cal.addEvent(a, 1, "Added live", min(30))
        waitForText("next_title", 20_000) { it == "Added live" }
    }

    @Test
    fun allDayEventsAreShownSeparatelyAndNeverAsNext() {
        val a = cal.addCalendar(1)
        cal.addAllDayEvent(a, 1, "Company offsite", java.time.LocalDate.now())
        cal.addEvent(a, 1, "Real meeting", min(30))
        waitForText("next_title") { it == "Real meeting" }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("all_day").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun longPressOpensSettingsAndCalendarSwitchHidesItsMeetings() {
        val a = cal.addCalendar(1, "Sw Alpha")
        cal.addEvent(a, 1, "Visible until switched off", min(30))
        waitForText("next_title") { it == "Visible until switched off" }

        node("clock_section").performTouchInput { longClick() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("settings").fetchSemanticsNodes().isNotEmpty() }
        node("cal_switch_$a").performClick()
        node("settings_close").performClick()

        waitForText("next_empty") { true }
        assertEquals("Clear", text("countdown_value"))
    }

    @Test
    fun settingsShowsDiagnosticsAndForceSyncReports() {
        cal.addCalendar(1)
        waitForText("countdown_value") { it == "Clear" }
        node("clock_section").performTouchInput { longClick() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("settings").fetchSemanticsNodes().isNotEmpty() }
        node("settings").performScrollToNode(hasTestTag("diagnostics"))
        assertTrue(text("diagnostics")!!.contains("andash 1.0"))
        node("force_sync").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("sync_message").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun settingsPersistAcrossActivityRecreation() {
        cal.addCalendar(1)
        node("clock_section").performTouchInput { longClick() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("settings").fetchSemanticsNodes().isNotEmpty() }
        val before = text("val_Look ahead (days)")!!.toInt()
        node("inc_Look ahead (days)").performClick()
        rule.waitUntil(5_000) { text("val_Look ahead (days)")!!.toInt() == before + 1 }
        rule.activityRule.scenario.recreate()
        // A fresh activity reads the stored value back.
        val stored = runBlocking { container.settings.settings.first() }.lookAheadDays
        assertEquals(before + 1, stored)
    }

    private fun allDayNight() = runBlocking {
        container.settings.setNightStart(0)
        container.settings.setNightEnd(23 * 60 + 59)
        container.settings.setNightEnabled(true)
    }

    @Test
    fun nightModeDimsTheBacklightAndCanShowOnlyTheClock() {
        val a = cal.addCalendar(1)
        cal.addEvent(a, 1, "Late meeting", min(30))
        waitForText("next_title") { it == "Late meeting" }
        allDayNight()
        rule.waitUntil(10_000) { rule.activity.window.attributes.screenBrightness in 0f..0.05f }
        // Full dashboard is still shown (dimmed) unless "clock only" is on.
        assertTrue(text("next_title") == "Late meeting")
        runBlocking { container.settings.setNightClockOnly(true) }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("next_meeting").fetchSemanticsNodes().isEmpty() }
        assertTrue(rule.onAllNodesWithTag("countdown").fetchSemanticsNodes().isEmpty())
        // Turning night mode off restores system brightness control.
        runBlocking { container.settings.setNightEnabled(false) }
        rule.waitUntil(10_000) { rule.activity.window.attributes.screenBrightness < 0f }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("next_meeting").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun shiftingTheClockMovesTheCountdownAndTheNextMeeting() {
        val a = cal.addCalendar(1)
        cal.addEvent(a, 1, "Shifted", min(50))
        waitForText("countdown_value") { it.startsWith("4") || it.startsWith("5") }
        // Debug clock shift +48 minutes: the meeting is now ~2 minutes away (urgent).
        runBlocking { container.settings.setDebugOffsetMinutes(48) }
        waitForText("countdown_value") { it.startsWith("0") && it.contains(":") }
    }

    private fun topPackage(): String {
        val pfd = inst.uiAutomation.executeShellCommand("dumpsys activity activities")
        val text = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readText()
        val line = text.lineSequence().firstOrNull { it.contains("topResumedActivity=") } ?: return ""
        return Regex("""u0 ([\w.]+)/""").find(line)?.groupValues?.get(1).orEmpty()
    }

    @Test
    fun openDefaultLauncherEscapesTheDashboard() {
        cal.addCalendar(1)
        waitForText("countdown_value") { it == "Clear" }
        assertEquals("co.lunt.andash", topPackage())
        rule.runOnUiThread { co.lunt.andash.ui.openOtherLauncher(rule.activity) }
        rule.waitUntil(10_000) { topPackage().let { it.isNotEmpty() && it != "co.lunt.andash" } }
    }

    @Test
    fun longPressAnywhereOpensSettingsWithAnExitButton() {
        cal.addCalendar(1)
        waitForText("countdown_value") { it == "Clear" }
        node("countdown").performTouchInput { longClick() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("settings").fetchSemanticsNodes().isNotEmpty() }
        node("exit_to_launcher").performClick()
        rule.waitUntil(10_000) { topPackage().let { it.isNotEmpty() && it != "co.lunt.andash" } }
    }
}
