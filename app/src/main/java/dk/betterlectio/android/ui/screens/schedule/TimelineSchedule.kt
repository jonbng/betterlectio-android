package dk.betterlectio.android.ui.screens.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dk.betterlectio.android.R
import dk.betterlectio.android.feature.schedule.EventStatus
import dk.betterlectio.android.feature.schedule.ScheduleEvent
import dk.betterlectio.android.feature.schedule.timeLabelText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.max

/**
 * Hour-grid timeline for professional calendar style (iOS TimelineListView inspiration).
 */
@Composable
fun TimelineDayView(
    date: LocalDate,
    events: List<ScheduleEvent>,
    displayTitle: (ScheduleEvent) -> String,
    accentFor: (ScheduleEvent) -> Color,
    statusColor: (EventStatus) -> Color,
    onEventClick: (ScheduleEvent) -> Unit,
    modifier: Modifier = Modifier,
    dayStartHour: Int = 7,
    dayEndHour: Int = 18,
    hourHeight: Dp = 64.dp,
) {
    val allDay = events.filter { it.isAllDay }
    val timed = events.filter { !it.isAllDay }
    val hours = dayEndHour - dayStartHour
    val totalHeight = hourHeight * hours
    val scroll = rememberScrollState()
    val now = LocalDateTime.now()
    val showNow = now.toLocalDate() == date

    Column(modifier) {
        if (allDay.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(stringResource(R.string.event_all_day), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                allDay.forEach { ev ->
                    Text(
                        displayTitle(ev),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentFor(ev).copy(alpha = 0.15f))
                            .clickable { onEventClick(ev) }
                            .padding(8.dp),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .height(totalHeight + 8.dp),
        ) {
            // Hour labels
            Column(Modifier.width(48.dp).height(totalHeight)) {
                repeat(hours) { i ->
                    val hour = dayStartHour + i
                    Box(Modifier.height(hourHeight).fillMaxWidth().padding(end = 4.dp)) {
                        Text(
                            "%02d:00".format(hour),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .height(totalHeight)
                    .padding(end = 8.dp),
            ) {
                val density = LocalDensity.current
                val heightPx = with(density) { totalHeight.toPx() }
                val minutesSpan = (hours * 60).toFloat()

                // Grid lines
                Canvas(Modifier.fillMaxSize()) {
                    val step = size.height / hours
                    for (i in 0..hours) {
                        val y = i * step
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.25f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f,
                        )
                    }
                }

                // Events
                timed.forEach { event ->
                    val start = event.start ?: LocalDateTime.of(date, LocalTime.of(dayStartHour, 0))
                    val end = event.end ?: start.plusHours(1)
                    val startMin = max(0, (start.hour * 60 + start.minute) - dayStartHour * 60)
                    val endMin = max(startMin + 30, (end.hour * 60 + end.minute) - dayStartHour * 60)
                    val top = (startMin / minutesSpan) * heightPx
                    val h = ((endMin - startMin) / minutesSpan) * heightPx
                    val topDp = with(density) { top.toDp() }
                    val hDp = with(density) { h.toDp() }
                    val accent = if (event.status == EventStatus.NORMAL) accentFor(event) else statusColor(event.status)

                    Row(
                        Modifier
                            .offset(y = topDp)
                            .height(hDp.coerceAtLeast(28.dp))
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .clickable { onEventClick(event) },
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(3.dp)
                                .background(accent),
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                displayTitle(event),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Text(
                                event.timeLabelText(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            event.room?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                // Now line
                if (showNow) {
                    val nowMin = (now.hour * 60 + now.minute) - dayStartHour * 60
                    if (nowMin in 0 until (hours * 60)) {
                        val y = (nowMin / minutesSpan) * heightPx
                        val yDp = with(density) { y.toDp() }
                        Box(
                            Modifier
                                .offset(y = yDp)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.error),
                        )
                    }
                }
            }
        }
    }
}
