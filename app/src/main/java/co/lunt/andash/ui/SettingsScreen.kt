package co.lunt.andash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.lunt.andash.DataState
import co.lunt.andash.settings.AppSettings
import java.time.Instant
import java.time.ZoneId

/** Everything the settings screen can change, as plain callbacks so it stays previewable. */
class SettingsActions(
    val setCalendarEnabled: (Long, Boolean) -> Unit,
    val setLookAheadDays: (Int) -> Unit,
    val setMaxUpcoming: (Int) -> Unit,
    val setGraceMinutes: (Int) -> Unit,
    val setSoonMinutes: (Int) -> Unit,
    val setHideFree: (Boolean) -> Unit,
    val setShowAllDayChips: (Boolean) -> Unit,
    val setNightEnabled: (Boolean) -> Unit,
    val setNightStart: (Int) -> Unit,
    val setNightEnd: (Int) -> Unit,
    val setNightClockOnly: (Boolean) -> Unit,
    val setDebugOffsetMinutes: (Int) -> Unit,
    val forceSync: () -> Unit,
    val openAndroidSettings: () -> Unit,
    val openDefaultLauncher: () -> Unit,
    val close: () -> Unit,
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    data: DataState,
    syncMessage: String?,
    versionName: String,
    debuggable: Boolean,
    now: Instant,
    zone: ZoneId,
    actions: SettingsActions,
) {
    val byAccount = data.calendars.groupBy { it.accountName }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp)
            .testTag("settings"),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", color = Palette.Primary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Button(onClick = actions.close, modifier = Modifier.testTag("settings_close")) { Text("Done") }
            }
        }
        item {
            OutlinedButton(
                onClick = actions.openDefaultLauncher,
                modifier = Modifier.fillMaxWidth().testTag("exit_to_launcher"),
            ) { Text("Exit to phone home screen") }
        }

        item { Header("Calendars") }
        if (byAccount.isEmpty()) {
            item { Note("No calendars found. Add a Google account in Android settings and turn on Calendar sync.") }
        }
        for ((account, calendars) in byAccount) {
            item(key = "acct:$account") {
                Text(account, color = Palette.Secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            }
            for (c in calendars) {
                item(key = "cal:${c.id}") {
                    val on = settings.isEnabled(c)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.color)))
                        Spacer(Modifier.width(10.dp))
                        Text(c.displayName, color = Palette.Primary, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Switch(checked = on, onCheckedChange = { actions.setCalendarEnabled(c.id, it) }, modifier = Modifier.testTag("cal_switch_${c.id}"))
                    }
                }
            }
        }

        item { Header("Display") }
        item { Stepper("Look ahead (days)", settings.lookAheadDays, 1, 30, actions.setLookAheadDays) }
        item { Stepper("Meetings listed after next", settings.maxUpcoming, 0, 12, actions.setMaxUpcoming) }
        item { Stepper("Keep started meeting (min)", settings.graceMinutes, 0, 60, actions.setGraceMinutes) }
        item { Stepper("\"Soon\" warning (min)", settings.soonMinutes, 1, 30, actions.setSoonMinutes) }
        item { ToggleRow("Hide events marked free", settings.hideFree, actions.setHideFree) }
        item { ToggleRow("Show all-day events", settings.showAllDayChips, actions.setShowAllDayChips) }

        item { Header("Night mode") }
        item { ToggleRow("Dim at night", settings.nightEnabled, actions.setNightEnabled) }
        item { Stepper("Starts", settings.nightStartMinute, 0, 24 * 60 - 30, actions.setNightStart, step = 30, format = ::minuteText, wrap = true) }
        item { Stepper("Ends", settings.nightEndMinute, 0, 24 * 60 - 30, actions.setNightEnd, step = 30, format = ::minuteText, wrap = true) }
        item { ToggleRow("Show only the clock at night", settings.nightClockOnly, actions.setNightClockOnly) }

        item { Header("Actions") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.forceSync, modifier = Modifier.fillMaxWidth().testTag("force_sync")) { Text("Force calendar sync now") }
                if (syncMessage != null) Text(syncMessage, color = Palette.Secondary, fontSize = 14.sp, modifier = Modifier.testTag("sync_message"))
                OutlinedButton(onClick = actions.openAndroidSettings, modifier = Modifier.fillMaxWidth()) { Text("Open Android settings") }
                OutlinedButton(onClick = actions.openDefaultLauncher, modifier = Modifier.fillMaxWidth().testTag("open_launcher")) { Text("Open default launcher") }
            }
        }

        if (debuggable) {
            item { Header("Debug") }
            item {
                Stepper("Shift clock (min)", settings.debugOffsetMinutes, -24 * 60, 7 * 24 * 60, actions.setDebugOffsetMinutes, step = 15)
            }
            item { TextButton(onClick = { actions.setDebugOffsetMinutes(0) }) { Text("Reset clock shift") } }
        }

        item { Header("About") }
        item {
            val last = data.loadedAt?.atZone(zone)?.toLocalTime()?.withNano(0)?.toString() ?: "never"
            Text(
                "andash $versionName\n${data.calendars.size} calendars · ${data.meetings.size} events loaded\n" +
                    "Last refresh $last" + (data.error?.let { "\nLast error: $it" } ?: ""),
                color = Palette.Dim, fontSize = 14.sp, modifier = Modifier.padding(bottom = 32.dp).testTag("diagnostics"),
            )
        }
    }
}

fun minuteText(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

@Composable
private fun Header(text: String) {
    Text(text.uppercase(), color = Palette.Now, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(top = 24.dp, bottom = 6.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, color = Palette.Dim, fontSize = 14.sp)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.Primary, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    step: Int = 1,
    format: (Int) -> String = Int::toString,
    wrap: Boolean = false,
) {
    fun next(v: Int): Int = if (wrap) (v.mod(max + step)) else v.coerceIn(min, max)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.Primary, fontSize = 17.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(next(value - step)) }, modifier = Modifier.testTag("dec_$label")) { Text("−", fontSize = 22.sp) }
        Text(format(value), color = Palette.Primary, fontSize = 17.sp, modifier = Modifier.width(64.dp).testTag("val_$label"), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(onClick = { onChange(next(value + step)) }, modifier = Modifier.testTag("inc_$label")) { Text("+", fontSize = 22.sp) }
    }
}
