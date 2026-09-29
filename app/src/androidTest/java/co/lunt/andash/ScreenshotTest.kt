package co.lunt.andash

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Renders representative scenarios on the phone and saves PNGs to the app's external files dir
 * (pull with `adb pull /sdcard/Android/data/co.lunt.andash/files/screenshots`) for eyeballing.
 */
class ScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val container = (inst.targetContext.applicationContext as AndashApp).container
    private val cal = TestCalendars(inst.context)
    private val now = Instant.now()
    private fun min(m: Long) = now.plus(Duration.ofMinutes(m))
    private val dir = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }

    @Before
    fun setUp() {
        TestCalendars.grantPermissions()
        cal.cleanUp()
        runBlocking {
            container.settings.clearAll()
            for (c in container.source.calendars()) {
                if (!c.accountName.startsWith(TestCalendars.PREFIX)) container.settings.setCalendarEnabled(c.id, false)
            }
        }
    }

    @After
    fun tearDown() {
        cal.cleanUp()
        runBlocking { container.settings.clearAll() }
    }

    private fun text(tag: String): String? =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }

    private fun shoot(name: String) {
        // Let the tick and any pending layout settle so the frame is stable.
        Thread.sleep(1_200)
        val bmp: Bitmap = inst.uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun seedWorkDay(firstInMinutes: Long) {
        val a = cal.addCalendar(1, "Personal", 0xFF4285F4.toInt())
        val b = cal.addCalendar(2, "Work", 0xFF0B8043.toInt())
        val c = cal.addCalendar(3, "Side project", 0xFFF4511E.toInt())
        cal.addEvent(a, 1, "Product roadmap sync", min(firstInMinutes), 45, uid = "s1", description = "https://meet.google.com/abc-defg-hij")
        cal.addEvent(b, 2, "Product roadmap sync", min(firstInMinutes), 45, uid = "s1")
        cal.addEvent(b, 2, "1:1 with Alex", min(firstInMinutes + 75), 30, description = "https://acme.zoom.us/j/1234")
        cal.addEvent(c, 3, "Design review: onboarding redesign v2 with extended stakeholders", min(firstInMinutes + 140), 60, location = "Room 4B")
        cal.addEvent(a, 1, "Dentist", min(firstInMinutes + 240), 45)
        cal.addEvent(b, 2, "Board prep", min(firstInMinutes + 24 * 60 + 30), 60)
        cal.addAllDayEvent(b, 2, "Offsite day 2", java.time.LocalDate.now())
    }

    private fun await(tag: String, pred: (String) -> Boolean) = rule.waitUntil(20_000) { text(tag)?.let(pred) == true }

    @Test
    fun normalDay() {
        seedWorkDay(38)
        await("next_title") { it.startsWith("Product") }
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("upcoming_row").fetchSemanticsNodes().size >= 4 }
        shoot("1_normal")
    }

    @Test
    fun startingSoon() {
        seedWorkDay(3)
        await("next_title") { it.startsWith("Product") }
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("upcoming_row").fetchSemanticsNodes().size >= 4 }
        shoot("2_soon")
    }

    @Test
    fun inProgress() {
        seedWorkDay(-4)
        await("next_label") { it == "HAPPENING NOW" }
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("upcoming_row").fetchSemanticsNodes().size >= 3 }
        shoot("3_in_progress")
    }

    @Test
    fun freeRestOfDay() {
        val a = cal.addCalendar(1)
        cal.addEvent(a, 1, "Tomorrow standup", min(20 * 60), 30)
        cal.addEvent(a, 1, "Tomorrow lunch", min(23 * 60), 60)
        await("countdown_value") { it == "Free" || it.contains(":") }
        shoot("4_free_or_soon")
    }

    @Test
    fun empty() {
        cal.addCalendar(1)
        await("countdown_value") { it == "Clear" }
        shoot("5_empty")
    }

    @Test
    fun settings() {
        seedWorkDay(38)
        await("next_title") { it.startsWith("Product") }
        rule.onNodeWithTag("clock_section").performTouchInput { longClick() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("settings").fetchSemanticsNodes().isNotEmpty() }
        shoot("6_settings")
    }

    @Test
    fun nightMode() {
        seedWorkDay(38)
        runBlocking {
            // A window covering the whole day, so it is "night" whenever the test runs.
            container.settings.setNightStart(0)
            container.settings.setNightEnd(23 * 60 + 59)
            container.settings.setNightEnabled(true)
        }
        await("next_title") { it.startsWith("Product") }
        shoot("7_night")
    }
}
