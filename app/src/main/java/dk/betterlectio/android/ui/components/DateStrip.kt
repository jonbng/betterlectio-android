package dk.betterlectio.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

data class DateStripDay(
    val date: LocalDate,
    val hasEvents: Boolean = false,
)

private const val TOTAL_WEEKS = 104
private const val CENTER_WEEK_INDEX = 52

/**
 * iOS-style calendar week strip:
 * - Full-width week (7 equal columns, no LazyRow gaps)
 * - Horizontal swipe between weeks
 * - Selected day: primary fill with top-only rounded corners
 * - Empty days (no events): muted tint
 * - Today (unselected): primary text color
 */
@Composable
fun DateStrip(
    days: List<DateStripDay>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
    onWeekChanged: ((LocalDate) -> Unit)? = null,
    hasEvents: ((LocalDate) -> Boolean)? = null,
) {
    // Prefer explicit hasEvents lookup; fall back to the provided week days list.
    val hasEventsLookup by rememberUpdatedState(
        hasEvents ?: { date -> days.find { it.date == date }?.hasEvents == true },
    )
    val onSelectState by rememberUpdatedState(onSelect)
    val onWeekChangedState by rememberUpdatedState(onWeekChanged)

    // Anchor Monday of the week containing the first known selection / today.
    val anchorMonday = remember {
        selected.with(DayOfWeek.MONDAY)
    }

    fun weekStartForIndex(index: Int): LocalDate =
        anchorMonday.plusWeeks((index - CENTER_WEEK_INDEX).toLong())

    fun indexForDate(date: LocalDate): Int {
        val monday = date.with(DayOfWeek.MONDAY)
        val weeks = ChronoUnit.WEEKS.between(anchorMonday, monday).toInt()
        return (CENTER_WEEK_INDEX + weeks).coerceIn(0, TOTAL_WEEKS - 1)
    }

    val pagerState = rememberPagerState(
        initialPage = indexForDate(selected),
        pageCount = { TOTAL_WEEKS },
    )

    // Keep pager in sync when selected date jumps (day pager / programmatic).
    LaunchedEffect(selected) {
        val target = indexForDate(selected)
        if (pagerState.settledPage != target && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }

    // Week swipe settled → keep same weekday offset.
    var suppressWeekCallback by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (suppressWeekCallback) {
                    suppressWeekCallback = false
                    return@collect
                }
                val newWeekStart = weekStartForIndex(page)
                val currentWeekStart = selected.with(DayOfWeek.MONDAY)
                if (newWeekStart == currentWeekStart) return@collect
                val dayOffset = (selected.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
                val targetDate = newWeekStart.plusDays(dayOffset.toLong())
                onWeekChangedState?.invoke(targetDate) ?: onSelectState(targetDate)
            }
    }

    // When parent drives selected into another week (e.g. day swipe), don't re-fire week callback.
    LaunchedEffect(selected) {
        val target = indexForDate(selected)
        if (pagerState.currentPage != target) {
            suppressWeekCallback = true
        }
    }

    val haptics = LocalHapticFeedback.current
    var lastHapticDate by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected != lastHapticDate) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastHapticDate = selected
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        beyondViewportPageCount = 1,
    ) { page ->
        val weekStart = weekStartForIndex(page)
        WeekRow(
            weekStart = weekStart,
            selected = selected,
            locale = locale,
            hasEvents = hasEventsLookup,
            onSelect = onSelectState,
        )
    }
}

@Composable
private fun WeekRow(
    weekStart: LocalDate,
    selected: LocalDate,
    locale: Locale,
    hasEvents: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (i in 0 until 7) {
            val date = weekStart.plusDays(i.toLong())
            DayCell(
                date = date,
                selected = date == selected,
                isToday = date == LocalDate.now(),
                empty = !hasEvents(date),
                locale = locale,
                onClick = { onSelect(date) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    empty: Boolean,
    locale: Locale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(160),
        label = "dayScale",
    )

    val dayNumber = date.dayOfMonth.toString()
    val dayName = date.dayOfWeek
        .getDisplayName(TextStyle.SHORT, locale)
        .replace(".", "")
        .take(3)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val todayColor = primary

    val contentColor: Color = when {
        selected -> onPrimary
        isToday -> todayColor
        empty -> muted
        else -> onSurface.copy(alpha = 0.55f)
    }

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(if (selected) primary else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = dayNumber,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
        )
        Text(
            text = dayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
        )
    }
}
