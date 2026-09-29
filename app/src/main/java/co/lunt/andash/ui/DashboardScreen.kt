package co.lunt.andash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import co.lunt.andash.domain.CountdownState
import co.lunt.andash.domain.DashboardState
import java.time.Instant
import java.time.ZoneId

/** The whole dashboard as a pure function of its inputs, so previews and tests can drive it. */
@Composable
fun DashboardScreen(
    state: DashboardState,
    now: Instant,
    zone: ZoneId,
    formats: TimeFormats,
    showAllDay: Boolean,
    night: Boolean,
    clockOnly: Boolean,
    onLongPressClock: () -> Unit,
    modifier: Modifier = Modifier,
    reserved: (@Composable () -> Unit)? = null,
) {
    val today = now.atZone(zone).toLocalDate()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.Background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .alpha(if (night) 0.55f else 1f)
            .testTag("dashboard"),
    ) {
        ClockSection(now, zone, formats, onLongPressClock, Modifier.weight(if (clockOnly) 1f else 0.26f))
        if (!clockOnly) {
            if (showAllDay) AllDayChips(state.allDay)
            NextMeetingSection(
                meeting = state.primary,
                inProgress = state.primaryInProgress,
                urgency = state.urgency,
                zone = zone,
                formats = formats,
                emptyText = if (state.countdown is CountdownState.NoMeetings) "No upcoming meetings" else "Nothing else today",
                modifier = Modifier.weight(0.27f),
            )
            Spacer(Modifier.height(8.dp))
            UpcomingSection(state.upcoming, today, zone, formats, Modifier.weight(0.29f))
            CountdownSection(state.countdown, state.urgency, zone, formats, today, Modifier.weight(0.18f))
            ReservedSlot(content = reserved)
        }
    }
}
