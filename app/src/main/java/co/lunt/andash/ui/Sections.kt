package co.lunt.andash.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import co.lunt.andash.domain.Countdown
import co.lunt.andash.domain.CountdownState
import co.lunt.andash.domain.Urgency
import co.lunt.andash.model.Meeting
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClockSection(
    now: Instant,
    zone: ZoneId,
    formats: TimeFormats,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .testTag("clock_section"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formats.clock(now, zone),
                modifier = Modifier.weight(1f, fill = false).testTag("clock"),
                color = Palette.Primary,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                textAlign = TextAlign.Center,
                autoSize = TextAutoSize.StepBased(minFontSize = 48.sp, maxFontSize = 160.sp),
            )
            val suffix = formats.ampm(now, zone)
            if (suffix.isNotEmpty()) {
                Text(
                    text = suffix,
                    modifier = Modifier.padding(start = 8.dp, top = 40.dp).testTag("ampm"),
                    color = Palette.Secondary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        Text(
            text = formats.date(now, zone),
            modifier = Modifier.testTag("date"),
            color = Palette.Secondary,
            fontSize = 22.sp,
        )
    }
}

@Composable
fun NextMeetingSection(
    meeting: Meeting?,
    inProgress: Boolean,
    urgency: Urgency,
    zone: ZoneId,
    formats: TimeFormats,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().testTag("next_meeting"), contentAlignment = Alignment.CenterStart) {
        if (meeting == null) {
            Text(
                text = emptyText,
                modifier = Modifier.fillMaxWidth().testTag("next_empty"),
                color = Palette.Dim,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
            )
            return@Box
        }
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(meeting.color)),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text(
                    text = if (inProgress) "HAPPENING NOW" else "NEXT UP",
                    modifier = Modifier.testTag("next_label"),
                    color = if (inProgress) Palette.accent(urgency) else Palette.Dim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = meeting.title,
                    modifier = Modifier.weight(1f, fill = false).testTag("next_title"),
                    color = Palette.Primary,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 1.15.em,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = 44.sp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = formats.range(meeting.start, meeting.end, zone),
                    modifier = Modifier.testTag("next_time"),
                    color = Palette.Secondary,
                    fontSize = 24.sp,
                )
                // When the location is just the meeting link, the "Meet"/"Zoom" tag already says it.
                val location = meeting.location?.takeUnless { meeting.link != null && it.contains(meeting.link.url) }
                val tags = listOfNotNull(meeting.link?.kind, location)
                if (tags.isNotEmpty()) {
                    Text(
                        text = tags.joinToString("  ·  "),
                        modifier = Modifier.testTag("next_tags"),
                        color = Palette.Dim,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = accountsLabel(meeting),
                    modifier = Modifier.testTag("next_accounts"),
                    color = Palette.Dim,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun accountsLabel(m: Meeting): String = m.accounts.joinToString(", ") { it.substringBefore('@') }

@Composable
fun UpcomingSection(
    meetings: List<Meeting>,
    today: java.time.LocalDate,
    zone: ZoneId,
    formats: TimeFormats,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().testTag("upcoming")) {
        val shown = fitUpcoming(meetings, today, zone, maxHeight.value, LocalDensity.current.fontScale)
        Column(verticalArrangement = Arrangement.Top) {
            // Items today need no header; anything later gets a day label.
            var lastDay: java.time.LocalDate = today
            for (m in shown) {
                val day = m.start.atZone(zone).toLocalDate()
                if (day != lastDay) {
                    Text(
                        text = formats.dayLabel(day, today).uppercase(),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).testTag("upcoming_day"),
                        color = Palette.Dim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    lastDay = day
                }
                UpcomingRow(m, zone, formats)
            }
        }
    }
}

@Composable
private fun UpcomingRow(m: Meeting, zone: ZoneId, formats: TimeFormats) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("upcoming_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(m.color)))
        Spacer(Modifier.width(12.dp))
        Text(
            text = formats.time(m.start, zone),
            modifier = Modifier.width(92.dp),
            color = Palette.Secondary,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
        Text(
            text = m.title,
            modifier = Modifier.weight(1f),
            color = Palette.Secondary,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AllDayChips(events: List<Meeting>, modifier: Modifier = Modifier) {
    if (events.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp).testTag("all_day")) {
        for (e in events.take(3)) {
            Text(
                text = "All day · ${e.title}",
                color = Palette.Dim,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CountdownSection(
    countdown: CountdownState,
    urgency: Urgency,
    zone: ZoneId,
    formats: TimeFormats,
    today: java.time.LocalDate,
    modifier: Modifier = Modifier,
) {
    val text = Countdown.text(countdown)
    Column(
        modifier = modifier.fillMaxWidth().testTag("countdown"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text.caption,
            modifier = Modifier.testTag("countdown_caption"),
            color = Palette.Dim,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
        )
        Text(
            text = text.value,
            modifier = Modifier.weight(1f, fill = false).testTag("countdown_value"),
            color = Palette.accent(urgency),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 84.sp),
        )
        if (countdown is CountdownState.FreeRestOfDay && countdown.next != null) {
            val day = countdown.next.start.atZone(zone).toLocalDate()
            Text(
                text = "Next: ${formats.dayLabel(day, today)} ${formats.time(countdown.next.start, zone)}",
                modifier = Modifier.testTag("countdown_next"),
                color = Palette.Secondary,
                fontSize = 18.sp,
            )
        }
    }
}

/** Empty on purpose: future features (weather, notes, a busy light) get a section here. */
@Composable
fun ReservedSlot(modifier: Modifier = Modifier, content: (@Composable () -> Unit)? = null) {
    if (content == null) return
    Box(modifier = modifier.fillMaxWidth().testTag("reserved_slot")) { content() }
}
