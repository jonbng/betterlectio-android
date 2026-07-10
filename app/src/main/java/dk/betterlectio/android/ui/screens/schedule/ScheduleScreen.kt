package dk.betterlectio.android.ui.screens.schedule

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import dk.betterlectio.android.ui.components.AppListDivider
import dk.betterlectio.android.ui.components.AppListMeta
import dk.betterlectio.android.ui.components.AppListPrimary
import dk.betterlectio.android.ui.components.AppListRow
import dk.betterlectio.android.ui.components.AppListSecondary
import dk.betterlectio.android.ui.components.DateStrip
import dk.betterlectio.android.ui.components.DateStripDay
import dk.betterlectio.android.ui.components.DetailSection
import dk.betterlectio.android.ui.components.DetailSheetHeader
import dk.betterlectio.android.ui.components.DetailSheetPadding
import dk.betterlectio.android.ui.components.EmptyBox
import dk.betterlectio.android.ui.components.ErrorBox
import dk.betterlectio.android.ui.components.LeadingAccentBar
import dk.betterlectio.android.ui.components.LoadingBox
import dk.betterlectio.android.ui.components.StatusChip
import dk.betterlectio.android.ui.theme.BetterLectioThemeExtras
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel(),
    scrollToTopToken: Int = 0,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val calendarStyle by viewModel.calendarStyle.collectAsStateWithLifecycle()
    val extended = BetterLectioThemeExtras.extendedColors
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(scrollToTopToken) {
        if (scrollToTopToken > 0) listState.animateScrollToItem(0)
    }

    val locale = Locale.getDefault()
    val subtitle = state.selectedDate.let { d ->
        val dayName = d.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        "$dayName · uge ${state.weekNum}"
    }

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
                actions = {
                    IconButton(onClick = viewModel::prevWeek) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_prev_week))
                    }
                    IconButton(onClick = viewModel::nextWeek) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next_week))
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
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                state.loading && state.week == null -> LoadingBox()
                state.error != null && state.week == null ->
                    ErrorBox(state.error, onRetry = { viewModel.refresh(true) })
                state.week == null -> EmptyBox(stringResource(R.string.empty_schedule))
                else -> {
                    val week = state.week!!
                    Column(Modifier.fillMaxSize()) {
                        DateStrip(
                            days = week.days.map { day ->
                                DateStripDay(date = day.date, hasEvents = day.events.isNotEmpty())
                            },
                            selected = state.selectedDate,
                            onSelect = viewModel::selectDate,
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp,
                        )

                        val events = week.days.find { it.date == state.selectedDate }?.events.orEmpty()
                        if (events.isEmpty()) {
                            EmptyBox(stringResource(R.string.empty_schedule_day))
                        } else if (calendarStyle == CalendarStyle.PROFESSIONAL) {
                            TimelineDayView(
                                date = state.selectedDate,
                                events = events,
                                displayTitle = { viewModel.displayTitle(it) },
                                accentFor = { Color(viewModel.accentArgbFor(it)) },
                                statusColor = { status ->
                                    when (status) {
                                        EventStatus.CHANGED -> extended.statusChanged
                                        EventStatus.CANCELLED -> extended.statusCancelled
                                        EventStatus.NORMAL -> extended.statusNormal
                                    }
                                },
                                onEventClick = { viewModel.selectEvent(it) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(events, key = { it.id }, contentType = { "event" }) { event ->
                                    val statusColor = when (event.status) {
                                        EventStatus.CHANGED -> extended.statusChanged
                                        EventStatus.CANCELLED -> extended.statusCancelled
                                        EventStatus.NORMAL -> extended.statusNormal
                                    }
                                    val accent = Color(viewModel.accentArgbFor(event))
                                    ScheduleEventCard(
                                        event = event,
                                        title = viewModel.displayTitle(event),
                                        style = CalendarStyle.STANDARD,
                                        accentColor = accent,
                                        statusColor = statusColor,
                                        onClick = { viewModel.selectEvent(event) },
                                    )
                                    AppListDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rich lesson detail
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

                    // Accent strip under header for subject identity
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
fun ScheduleEventCard(
    event: ScheduleEvent,
    title: String = event.title,
    style: CalendarStyle,
    accentColor: Color,
    statusColor: Color,
    onClick: () -> Unit,
) {
    // Both styles share the same flat row language; professional mode mainly affects the day grid.
    FlatScheduleEventRow(
        event = event,
        title = title,
        accentColor = if (event.status == EventStatus.NORMAL) accentColor else statusColor,
        statusColor = statusColor,
        dense = style == CalendarStyle.PROFESSIONAL,
        onClick = onClick,
    )
}

@Composable
private fun FlatScheduleEventRow(
    event: ScheduleEvent,
    title: String,
    accentColor: Color,
    statusColor: Color,
    dense: Boolean,
    onClick: () -> Unit,
) {
    val meta = buildList {
        event.teacher?.takeIf { it.isNotBlank() }?.let(::add)
        event.room?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" · ")

    AppListRow(
        onClick = onClick,
        leading = { LeadingAccentBar(accentColor) },
        trailing = {
            event.statusLabelText()?.takeIf { it.isNotBlank() }?.let { label ->
                StatusChip(text = label, color = statusColor)
            }
        },
    ) {
        AppListPrimary(title, emphasized = !dense)
        AppListSecondary(event.timeLabelText())
        if (meta.isNotBlank() && !dense) {
            AppListMeta(meta)
        }
    }
}
