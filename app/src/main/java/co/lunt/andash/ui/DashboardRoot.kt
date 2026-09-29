package co.lunt.andash.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.lunt.andash.DashboardViewModel
import co.lunt.andash.data.TimeSource
import co.lunt.andash.domain.NightWindow
import co.lunt.andash.domain.Schedule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** "Now", ticking on each second boundary. */
@Composable
fun rememberNow(time: TimeSource): State<Instant> = produceState(time.now(), time) {
    while (true) {
        value = time.now()
        delay(1_000 - (value.toEpochMilli() % 1_000) + 5)
    }
}

@Composable
fun DashboardRoot(vm: DashboardViewModel, onRequestPermission: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val now by rememberNow(vm.time)
    val zone = vm.time.zone()
    val is24h = DateFormat.is24HourFormat(context)
    val formats = remember(is24h) { TimeFormats(is24h) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val state = remember(now, data.meetings, settings) {
        Schedule.compute(now, zone, data.meetings, settings.scheduleConfig)
    }
    val night = settings.nightEnabled &&
        NightWindow.isActive(now.atZone(zone).toLocalTime(), settings.nightStartMinute, settings.nightEndMinute)

    // Dim the backlight at night; hand control back to the system otherwise.
    LaunchedEffect(night) {
        (context as? ComponentActivity)?.window?.let { w ->
            val attrs = w.attributes
            attrs.screenBrightness = if (night) NIGHT_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            w.attributes = attrs
        }
    }

    BackHandler(enabled = showSettings) { showSettings = false }

    if (showSettings) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val versionName = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
        val repo = vm.settingsRepo
        fun <T> set(block: suspend (T) -> Unit): (T) -> Unit = { v -> scope.launch { block(v) } }
        SettingsScreen(
            settings = settings,
            data = data,
            syncMessage = syncMessage,
            versionName = versionName,
            debuggable = debuggable,
            now = now,
            zone = zone,
            actions = SettingsActions(
                setCalendarEnabled = { id, on -> scope.launch { repo.setCalendarEnabled(id, on) } },
                setLookAheadDays = set(repo::setLookAheadDays),
                setMaxUpcoming = set(repo::setMaxUpcoming),
                setGraceMinutes = set(repo::setGraceMinutes),
                setSoonMinutes = set(repo::setSoonMinutes),
                setHideFree = set(repo::setHideFree),
                setShowAllDayChips = set(repo::setShowAllDayChips),
                setNightEnabled = set(repo::setNightEnabled),
                setNightStart = set(repo::setNightStart),
                setNightEnd = set(repo::setNightEnd),
                setNightClockOnly = set(repo::setNightClockOnly),
                setDebugOffsetMinutes = set(repo::setDebugOffsetMinutes),
                forceSync = {
                    vm.forceSync { n ->
                        syncMessage = if (n == 0) "No Google accounts to sync." else "Asked $n account(s) to sync."
                        vm.refresh()
                    }
                },
                openAndroidSettings = { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                openDefaultLauncher = { openOtherLauncher(context) },
                close = { showSettings = false },
            ),
        )
        return
    }

    if (data.loaded && !data.permissionGranted) {
        PermissionScreen(onRequestPermission)
        return
    }

    // Move the whole layout a few pixels every ten minutes to spread out burn-in.
    val step = ((now.epochSecond / 600) % 4).toInt()
    val shift = when (step) { 0 -> IntOffset(0, 0); 1 -> IntOffset(8, 4); 2 -> IntOffset(0, 8); else -> IntOffset(-8, 4) }
    val stale = data.loaded && data.loadedAt?.let { Duration.between(it, now) > STALE_AFTER } == true

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        DashboardScreen(
            state = state,
            now = now,
            zone = zone,
            formats = formats,
            showAllDay = settings.showAllDayChips,
            night = night,
            clockOnly = night && settings.nightClockOnly,
            onLongPressClock = { showSettings = true },
            modifier = Modifier.offset { shift },
        )
        if (stale || data.error != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp).size(10.dp)
                    .clip(CircleShape).background(Palette.Warning).testTag("stale_dot"),
            )
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().background(Palette.Background).padding(32.dp).testTag("permission_screen"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Calendar access needed", color = Palette.Primary, fontSize = 26.sp)
        Text(
            "andash reads the calendars already synced to this phone. Nothing leaves the device.",
            color = Palette.Secondary, fontSize = 16.sp, modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onRequest, modifier = Modifier.testTag("grant_permission")) { Text("Allow calendar access") }
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Open app settings") }
    }
}

/**
 * Escape hatch: starts the stock launcher (any Home app other than this one). If there is none,
 * opens Android's Home-app picker so a launcher can be chosen there.
 */
fun openOtherLauncher(context: Context) {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val other = context.packageManager.queryIntentActivities(home, 0)
        .map { it.activityInfo }
        .firstOrNull { it.packageName != context.packageName && !it.name.endsWith("FallbackHome") }
    val intent = if (other != null) {
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setClassName(other.packageName, other.name)
    } else {
        Intent(Settings.ACTION_HOME_SETTINGS)
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private const val NIGHT_BRIGHTNESS = 0.01f
private val STALE_AFTER: Duration = Duration.ofMinutes(15)
