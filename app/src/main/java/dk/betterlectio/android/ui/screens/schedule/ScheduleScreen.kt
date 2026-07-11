package dk.betterlectio.android.ui.screens.schedule

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.betterlectio.android.R
import dk.betterlectio.android.core.i18n.asString
import dk.betterlectio.android.feature.schedule.EventStatus
import dk.betterlectio.android.feature.schedule.ScheduleEvent
import dk.betterlectio.android.feature.schedule.statusLabelText
import dk.betterlectio.android.feature.schedule.timeLabelText
import dk.betterlectio.android.feature.settings.CalendarStyle
import dk.betterlectio.android.ui.components.AppListPrimary
import dk.betterlectio.android.ui.components.DateStrip
import dk.betterlectio.android.ui.components.DateStripDay
import dk.betterlectio.android.ui.components.DetailSection
import dk.betterlectio.android.ui.components.DetailSheetHeader
import dk.betterlectio.android.ui.components.DetailSheetPadding
import dk.betterlectio.android.ui.components.ErrorBox
import dk.betterlectio.android.ui.components.LoadingBox
import dk.betterlectio.android.ui.components.StatusChip
import dk.betterlectio.android.ui.theme.BetterLectioThemeExtras
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Large day index space so users can swipe across many weeks. */
private const val DAY_CENTER_PAGE = 5000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel(),
    scrollToTopToken: Int = 0,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val calendarStyle by viewModel.calendarStyle.collectAsStateWithLifecycle()
    val extended = BetterLectioThemeExtras.extendedColors
    val context = LocalContext.current

    // Anchor "today" for day-page ↔ date mapping (stable for session).
    val dayAnchor = remember { LocalDate.now() }

    fun dateForPage(page: Int): LocalDate =
        dayAnchor.plusDays((page - DAY_CENTER_PAGE).toLong())

    fun pageForDate(date: LocalDate): Int =
        DAY_CENTER_PAGE + ChronoUnit.DAYS.between(dayAnchor, date).toInt()

    val dayPagerState = rememberPagerState(
        initialPage = pageForDate(state.selectedDate),
        pageCount = { DAY_CENTER_PAGE * 2 },
    )

    val selectDate by rememberUpdatedState(viewModel::selectDate)
    val selectedDate by rememberUpdatedState(state.selectedDate)

    // Day swipe → select date
    LaunchedEffect(dayPagerState) {
        snapshotFlow { dayPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val date = dateForPage(page)
                if (date != selectedDate) {
                    selectDate(date)
                }
            }
    }

    // Programmatic date change (strip / week swipe) → day pager
    LaunchedEffect(state.selectedDate) {
        val target = pageForDate(state.selectedDate)
        if (dayPagerState.settledPage != target && !dayPagerState.isScrollInProgress) {
            dayPagerState.animateScrollToPage(target)
        }
    }

    // Scroll-to-top: jump day pager to today
    LaunchedEffect(scrollToTopToken) {
        if (scrollToTopToken > 0) {
            val today = LocalDate.now()
            selectDate(today)
            dayPagerState.animateScrollToPage(pageForDate(today))
        }
    }

    // Tick every minute for live header
    var nowEpochMinute by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowEpochMinute = System.currentTimeMillis() / 60_000
        }
    }
    val now = remember(nowEpochMinute) { LocalDateTime.now() }
    val liveHeader = remember(state.eventsByDate, nowEpochMinute) {
        computeLiveHeader(
            events = state.eventsByDate[LocalDate.now()].orEmpty()
                .ifEmpty {
                    state.week?.days?.find { it.date == LocalDate.now() }?.events.orEmpty()
                },
            now = now,
            displayTitle = viewModel::displayTitle,
        )
    }

    val locale = Locale.getDefault()
    val dayName = state.selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    val subtitle = stringResource(R.string.schedule_week_subtitle, dayName, state.weekNum)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.tab_schedule))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openPrivateEventSheet,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.private_event_add))
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.week != null,
            onRefresh = { viewModel.refresh(true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading && state.week == null && state.eventsByDate.isEmpty() -> LoadingBox()
                state.error != null && state.week == null && state.eventsByDate.isEmpty() ->
                    ErrorBox(state.error, onRetry = { viewModel.refresh(true) })
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        // Live lesson header (iOS ScheduleHeaderView)
                        LiveLessonHeader(liveHeader)

                        // Week strip — full width, swipe weeks + floating week badge
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                                    clip = false,
                                )
                                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                .background(MaterialTheme.colorScheme.surface),
                        ) {
                            val weekDays = state.week?.days.orEmpty()
                            DateStrip(
                                days = weekDays.map { day ->
                                    DateStripDay(
                                        date = day.date,
                                        hasEvents = day.events.isNotEmpty(),
                                    )
                                },
                                selected = state.selectedDate,
                                onSelect = viewModel::selectDate,
                                onWeekChanged = viewModel::selectDate,
                                hasEvents = { date -> viewModel.hasEvents(date) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                            )
                            // iOS floating week capsule over the strip
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 2.dp),
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 4.dp,
                                tonalElevation = 0.dp,
                            ) {
                                Text(
                                    stringResource(R.string.schedule_week_badge, state.weekNum),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            thickness = 0.5.dp,
                        )

                        // Day content pager — swipe between days
                        HorizontalPager(
                            state = dayPagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            beyondViewportPageCount = 1,
                            key = { page -> dateForPage(page).toString() },
                        ) { page ->
                            val date = dateForPage(page)
                            val events = state.eventsByDate[date]
                                ?: state.week?.days?.find { it.date == date }?.events
                                ?: emptyList()

                            DayPageContent(
                                date = date,
                                events = events,
                                calendarStyle = calendarStyle,
                                viewModel = viewModel,
                                extendedStatus = { status ->
                                    when (status) {
                                        EventStatus.CHANGED -> extended.statusChanged
                                        EventStatus.CANCELLED -> extended.statusCancelled
                                        EventStatus.NORMAL -> extended.statusNormal
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // Lesson detail sheet
    state.selectedEvent?.let { event ->
        val accent = Color(viewModel.accentArgbFor(event))
        val statusColor = when (event.status) {
            EventStatus.CHANGED -> extended.statusChanged
            EventStatus.CANCELLED -> extended.statusCancelled
            EventStatus.NORMAL -> extended.statusNormal
        }
        ModalBottomSheet(onDismissRequest = { viewModel.selectEvent(null) }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                DetailSheetPadding {
                    DetailSheetHeader(
                        title = viewModel.displayTitle(event),
                        subtitle = event.timeLabelText(),
                        meta = listOfNotNull(event.teacher, event.room).joinToString(" · ")
                            .ifBlank { null },
                        trailing = {
                            event.statusLabelText()?.takeIf { it.isNotBlank() }?.let { label ->
                                StatusChip(text = label, color = statusColor)
                            }
                        },
                    )

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(0.25f),
                        thickness = 3.dp,
                        color = if (event.status == EventStatus.NORMAL) accent else statusColor,
                    )

                    if (state.detailLoading) {
                        Spacer(Modifier.height(24.dp))
                        CircularProgressIndicator()
                    }

                    state.lessonDetail?.let { detail ->
                        detail.note?.takeIf { it.isNotBlank() }?.let {
                            DetailSection(stringResource(R.string.label_notes)) {
                                Text(it, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        detail.homework?.takeIf { it.isNotBlank() }?.let {
                            DetailSection(stringResource(R.string.label_homework)) {
                                Text(it, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        if (detail.contentBlocks.isNotEmpty()) {
                            DetailSection(stringResource(R.string.lesson_content)) {
                                detail.contentBlocks.forEach { block ->
                                    when (block.kind) {
                                        "heading" -> Text(
                                            block.text,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                        "note" -> Text(
                                            block.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        else -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                        if (detail.participants.isNotEmpty()) {
                            DetailSection(stringResource(R.string.lesson_participants)) {
                                detail.participants.forEach { p ->
                                    AppListPrimary(
                                        listOfNotNull(p.name, p.role).joinToString(" · "),
                                        maxLines = 2,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                        if (detail.resources.isNotEmpty()) {
                            DetailSection(stringResource(R.string.lesson_resources)) {
                                detail.resources.forEach { r ->
                                    TextButton(
                                        onClick = {
                                            val url = if (r.url.startsWith("http")) {
                                                r.url
                                            } else {
                                                "https://www.lectio.dk${r.url}"
                                            }
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(r.title + if (r.isFile) "  📎" else "")
                                    }
                                }
                            }
                        }
                    }

                    if (viewModel.canEditPrivateEvent(event)) {
                        Spacer(Modifier.height(16.dp))
                        TextButton(
                            onClick = { viewModel.openEditPrivateEvent(event) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.private_event_edit))
                        }
                    }
                    if (viewModel.canDeleteEvent(event)) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { viewModel.deletePrivateEvent(event) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.private_event_delete))
                        }
                    }
                }
            }
        }
    }

    if (state.showPrivateEvent) {
        ModalBottomSheet(onDismissRequest = viewModel::closePrivateEventSheet) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (state.editingPrivateEventId != null) {
                        stringResource(R.string.private_event_edit_title)
                    } else {
                        stringResource(R.string.private_event_title)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                OutlinedTextField(
                    value = state.privateTitle,
                    onValueChange = { viewModel.updatePrivateField(title = it) },
                    label = { Text(stringResource(R.string.private_event_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.privateStartDate,
                    onValueChange = { viewModel.updatePrivateField(startDate = it) },
                    label = { Text(stringResource(R.string.private_event_start_date)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.privateStartTime,
                    onValueChange = { viewModel.updatePrivateField(startTime = it) },
                    label = { Text(stringResource(R.string.private_event_start_time)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.privateEndDate,
                    onValueChange = { viewModel.updatePrivateField(endDate = it) },
                    label = { Text(stringResource(R.string.private_event_end_date)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.privateEndTime,
                    onValueChange = { viewModel.updatePrivateField(endTime = it) },
                    label = { Text(stringResource(R.string.private_event_end_time)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.privateNote,
                    onValueChange = { viewModel.updatePrivateField(note = it) },
                    label = { Text(stringResource(R.string.private_event_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.primary) }
                Button(onClick = viewModel::savePrivateEvent, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.private_event_save))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DayPageContent(
    date: LocalDate,
    events: List<ScheduleEvent>,
    calendarStyle: CalendarStyle,
    viewModel: ScheduleViewModel,
    extendedStatus: (EventStatus) -> Color,
) {
    if (calendarStyle == CalendarStyle.PROFESSIONAL) {
        TimelineDayView(
            date = date,
            events = events,
            displayTitle = { viewModel.displayTitle(it) },
            accentFor = { Color(viewModel.accentArgbFor(it)) },
            statusColor = extendedStatus,
            onEventClick = { viewModel.selectEvent(it) },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        StandardDayList(
            events = events,
            displayTitle = { viewModel.displayTitle(it) },
            accentFor = { Color(viewModel.accentArgbFor(it)) },
            onEventClick = { viewModel.selectEvent(it) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ── Live lesson header (iOS ScheduleHeaderView) ──────────────────────────────

private data class LiveHeaderUi(
    val subjectName: String,
    val room: String?,
    val minutes: Int,
    val progress: Float?,
    val isUpcoming: Boolean,
)

private fun computeLiveHeader(
    events: List<ScheduleEvent>,
    now: LocalDateTime,
    displayTitle: (ScheduleEvent) -> String,
): LiveHeaderUi? {
    val timed = events.filter { !it.isAllDay && it.start != null && it.end != null }
    val nowMin = now.hour * 60 + now.minute

    val current = timed.firstOrNull { e ->
        val s = e.start!!.hour * 60 + e.start.minute
        val en = e.end!!.hour * 60 + e.end.minute
        nowMin in s until en
    }
    if (current != null) {
        val start = current.start!!.hour * 60 + current.start.minute
        val end = current.end!!.hour * 60 + current.end.minute
        val remaining = (end - nowMin).coerceAtLeast(0)
        val duration = (end - start).coerceAtLeast(1)
        val progress = ((nowMin - start).toFloat() / duration).coerceIn(0f, 1f)
        return LiveHeaderUi(
            subjectName = displayTitle(current),
            room = current.room,
            minutes = remaining,
            progress = progress,
            isUpcoming = false,
        )
    }

    val next = timed
        .mapNotNull { e ->
            val s = e.start!!.hour * 60 + e.start.minute
            val until = s - nowMin
            if (until in 1..60) e to until else null
        }
        .minByOrNull { it.second }
        ?: return null

    return LiveHeaderUi(
        subjectName = displayTitle(next.first),
        room = next.first.room,
        minutes = next.second,
        progress = null,
        isUpcoming = true,
    )
}

@Composable
private fun LiveLessonHeader(header: LiveHeaderUi?) {
    if (header == null) return

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 16.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    header.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                header.room?.takeIf { it.isNotBlank() }?.let { room ->
                    Text(
                        "·",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        room,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
            Text(
                if (header.isUpcoming) {
                    stringResource(R.string.schedule_in_minutes, header.minutes)
                } else {
                    stringResource(R.string.schedule_minutes_left, header.minutes)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        header.progress?.let { progress ->
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
