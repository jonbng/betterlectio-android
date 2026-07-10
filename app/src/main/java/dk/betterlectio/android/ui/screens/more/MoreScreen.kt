package dk.betterlectio.android.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dk.betterlectio.android.R
import dk.betterlectio.android.core.i18n.asString
import dk.betterlectio.android.feature.schedule.timeLabelText
import dk.betterlectio.android.feature.directory.DirectoryEntity
import dk.betterlectio.android.feature.directory.DirectoryEntityKind
import dk.betterlectio.android.feature.settings.AppLanguage
import dk.betterlectio.android.feature.settings.AppearanceMode
import dk.betterlectio.android.feature.settings.CalendarStyle
import dk.betterlectio.android.feature.absence.AbsenceChartSeries
import dk.betterlectio.android.ui.components.AbsenceBarChart
import dk.betterlectio.android.ui.components.AbsenceRing
import dk.betterlectio.android.ui.components.AppListDivider
import dk.betterlectio.android.ui.components.AppListMeta
import dk.betterlectio.android.ui.components.AppListPrimary
import dk.betterlectio.android.ui.components.AppListRow
import dk.betterlectio.android.ui.components.AppListSecondary
import dk.betterlectio.android.ui.components.InitialsAvatar
import dk.betterlectio.android.ui.components.LoadingBox
import dk.betterlectio.android.ui.components.SectionHeader
import java.time.format.TextStyle
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(
    viewModel: MoreViewModel = hiltViewModel(),
    scrollToTopToken: Int = 0,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val calendarStyle by viewModel.calendarStyle.collectAsStateWithLifecycle()
    val notifEvents by viewModel.notifEvents.collectAsStateWithLifecycle()
    val notifMessages by viewModel.notifMessages.collectAsStateWithLifecycle()
    val notifAssignments by viewModel.notifAssignments.collectAsStateWithLifecycle()
    val subjectColors by viewModel.subjectColors.collectAsStateWithLifecycle()
    val subjectNames by viewModel.subjectNames.collectAsStateWithLifecycle()
    val notificationHistory by viewModel.notificationHistory.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val showBack = state.destination != MoreDestination.ROOT ||
        state.gradeDetail != null ||
        state.directoryParent != null ||
        state.planDetail != null ||
        state.roomEntity != null

    LaunchedEffect(scrollToTopToken) {
        if (scrollToTopToken > 0) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            state.gradeDetail != null -> state.gradeDetail!!.row.subject
                            state.planDetail != null -> state.planDetail!!.title
                            state.roomEntity != null -> state.roomEntity!!.name
                            state.directoryParent != null -> state.directoryParent!!.name
                            state.destination == MoreDestination.ROOT -> stringResource(R.string.tab_more)
                            state.destination == MoreDestination.GRADES -> stringResource(R.string.more_grades)
                            state.destination == MoreDestination.ABSENCE -> stringResource(R.string.more_absence)
                            state.destination == MoreDestination.DIRECTORY -> stringResource(R.string.more_directory)
                            state.destination == MoreDestination.ROOMS -> stringResource(R.string.more_rooms)
                            state.destination == MoreDestination.STUDIEKORT -> stringResource(R.string.more_studiekort)
                            state.destination == MoreDestination.PLANS -> stringResource(R.string.more_plans)
                            state.destination == MoreDestination.MODULE_STATS -> stringResource(R.string.more_module_stats)
                            state.destination == MoreDestination.TERM -> stringResource(R.string.more_term)
                            state.destination == MoreDestination.SETTINGS -> stringResource(R.string.more_settings)
                            state.destination == MoreDestination.HELP -> stringResource(R.string.more_help)
                            else -> stringResource(R.string.tab_more)
                        },
                    )
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = viewModel::back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (state.destination) {
            MoreDestination.ROOT -> MoreRoot(
                padding = padding,
                listState = listState,
                studentName = state.student?.name ?: state.student?.studentId.orEmpty(),
                classLabel = state.student?.classLabel,
                photoUrl = state.profilePhotoUrl,
                onNavigate = viewModel::navigate,
                onLogout = viewModel::logout,
            )
            MoreDestination.GRADES -> {
                if (state.loading) LoadingBox(Modifier.padding(padding))
                else if (state.gradeDetail != null) {
                    val detail = state.gradeDetail!!
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                                Text(
                                    detail.row.subject,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (detail.row.team.isNotBlank()) {
                                    Text(
                                        detail.row.team,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    detail.row.gradeSummary.ifBlank { "—" },
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp,
                            )
                        }
                        item { SectionHeader(stringResource(R.string.grades_notes)) }
                        if (detail.notes.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.grades_no_notes),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        } else {
                            items(detail.notes) { note ->
                                AppListRow { AppListPrimary(note, maxLines = 8) }
                                AppListDivider()
                            }
                        }
                    }
                } else LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    items(state.grades, key = { it.subject + it.team }) { g ->
                        AppListRow(
                            onClick = { viewModel.openGradeDetail(g) },
                            trailing = {
                                Text(
                                    g.gradeSummary.ifBlank { "—" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                        ) {
                            AppListPrimary(g.subject, emphasized = true)
                            if (g.team.isNotBlank()) AppListSecondary(g.team)
                            if (g.notes.isNotEmpty()) {
                                AppListMeta(stringResource(R.string.grades_notes_count, g.notes.size))
                            }
                        }
                        AppListDivider()
                    }
                }
            }
            MoreDestination.ABSENCE -> {
                if (state.loading) LoadingBox(Modifier.padding(padding))
                else LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    item {
                        SectionHeader(stringResource(R.string.absence_overview))
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            state.absence?.teams.orEmpty().forEach { row ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    AbsenceRing(fraction = row.regularCurrentPercent)
                                    Text(
                                        row.team,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp,
                        )
                    }
                    item {
                        val bars = AbsenceChartSeries.fromTeams(state.absence?.teams.orEmpty())
                        if (bars.isNotEmpty()) {
                            SectionHeader(stringResource(R.string.absence_chart))
                            AbsenceBarChart(bars = bars)
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp,
                            )
                        }
                    }
                    items(state.absence?.teams.orEmpty(), key = { "team-${it.team}" }) { row ->
                        AppListRow(
                            leading = {
                                AbsenceRing(fraction = row.regularCurrentPercent, size = 44.dp)
                            },
                        ) {
                            AppListPrimary(row.team, emphasized = true)
                            AppListSecondary(
                                stringResource(
                                    R.string.absence_current_final,
                                    "%.1f".format(row.regularCurrentPercent * 100),
                                    "%.1f".format(row.regularFinalPercent * 100),
                                ),
                            )
                            if (row.assignmentCurrentPercent > 0) {
                                AppListMeta(stringResource(R.string.absence_assignments_pct, "%.1f".format(row.assignmentCurrentPercent * 100)))
                            }
                        }
                        AppListDivider()
                    }
                    item { SectionHeader(stringResource(R.string.more_absence_regs)) }
                    items(state.absence?.registrations.orEmpty(), key = { it.id }) { reg ->
                        Column(Modifier.fillMaxWidth()) {
                            AppListRow {
                                AppListPrimary("${reg.date} · ${reg.team}", emphasized = true)
                                AppListSecondary("${reg.cause} · ${reg.status}")
                            }
                            FlowRow(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                viewModel.absenceCauses.forEach { cause ->
                                    FilterChip(
                                        selected = reg.cause.equals(cause, ignoreCase = true),
                                        onClick = { viewModel.updateAbsenceCause(reg.id, cause) },
                                        label = { Text(cause) },
                                    )
                                }
                            }
                            AppListDivider()
                        }
                    }
                }
            }
            MoreDestination.DIRECTORY -> Column(Modifier.padding(padding).fillMaxSize()) {
                when {
                    state.roomEntity != null -> {
                        if (state.loading) LoadingBox()
                        else {
                            val week = state.roomSchedule
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item {
                                    Text(
                                        stringResource(R.string.room_schedule) +
                                            if (week != null) " · uge ${week.week}" else "",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                if (week == null || week.days.all { it.events.isEmpty() }) {
                                    item {
                                        Text(
                                            stringResource(R.string.room_schedule_empty),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    week.days.forEach { day ->
                                        if (day.events.isEmpty()) return@forEach
                                        item(key = "day-${day.date}") {
                                            Text(
                                                day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                                                    " ${day.date.dayOfMonth}/${day.date.monthValue}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 8.dp),
                                            )
                                        }
                                        items(day.events, key = { it.id }) { ev ->
                                            AppListRow {
                                                AppListPrimary(ev.title, emphasized = true)
                                                AppListSecondary(ev.timeLabelText())
                                                ev.teacher?.let { AppListMeta(it) }
                                            }
                                            AppListDivider()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    state.directoryParent != null -> {
                        if (state.loading) LoadingBox()
                        else LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            item {
                                SectionHeader(
                                    stringResource(
                                        R.string.directory_members_of,
                                        state.directoryParent!!.name,
                                    ),
                                )
                            }
                            if (state.directoryMembers.isEmpty()) {
                                item {
                                    Text(
                                        stringResource(R.string.directory_no_members),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            } else {
                                items(state.directoryMembers, key = { it.id }) { e ->
                                    AppListRow(
                                        leading = {
                                            InitialsAvatar(e.name)
                                        },
                                    ) {
                                        AppListPrimary(e.name, emphasized = true)
                                        val meta = listOfNotNull(
                                            e.kind.name.lowercase().replaceFirstChar { c -> c.uppercase() },
                                            e.subtitle,
                                        ).joinToString(" · ")
                                        if (meta.isNotBlank()) AppListSecondary(meta)
                                    }
                                    AppListDivider()
                                }
                            }
                        }
                    }
                    else -> {
                        Column(Modifier.fillMaxSize()) {
                            OutlinedTextField(
                                value = state.directoryQuery,
                                onValueChange = viewModel::onDirectoryQuery,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                placeholder = { Text(stringResource(R.string.more_directory_search)) },
                                singleLine = true,
                                shape = RoundedCornerShape(28.dp),
                            )
                            FlowRow(
                                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = state.directoryKind == null,
                                    onClick = { viewModel.onDirectoryKind(null) },
                                    label = { Text(stringResource(R.string.filter_all)) },
                                )
                                DirectoryEntityKind.entries.take(6).forEach { kind ->
                                    FilterChip(
                                        selected = state.directoryKind == kind,
                                        onClick = { viewModel.onDirectoryKind(kind) },
                                        label = {
                                            Text(kind.name.lowercase().replaceFirstChar { it.uppercase() })
                                        },
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp,
                            )
                            if (state.loading) LoadingBox()
                            else LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                val pinned = state.directory.filter { state.pinnedIds.contains(it.id) }
                                val rest = state.directory.filter { !state.pinnedIds.contains(it.id) }
                                if (pinned.isNotEmpty()) {
                                    item { SectionHeader(stringResource(R.string.directory_pinned)) }
                                    items(pinned, key = { "pin-${it.id}" }) { e ->
                                        DirectoryEntityRow(
                                            entity = e,
                                            pinned = true,
                                            onTogglePin = { viewModel.togglePin(e) },
                                            onClick = {
                                                when (e.kind) {
                                                    DirectoryEntityKind.CLASS, DirectoryEntityKind.HOLD ->
                                                        viewModel.openDirectoryMembers(e)
                                                    DirectoryEntityKind.ROOM ->
                                                        viewModel.openRoomSchedule(e)
                                                    else -> Unit
                                                }
                                            },
                                        )
                                        AppListDivider()
                                    }
                                }
                                items(rest, key = { it.id }) { e ->
                                    DirectoryEntityRow(
                                        entity = e,
                                        pinned = false,
                                        onTogglePin = { viewModel.togglePin(e) },
                                        onClick = {
                                            when (e.kind) {
                                                DirectoryEntityKind.CLASS, DirectoryEntityKind.HOLD ->
                                                    viewModel.openDirectoryMembers(e)
                                                DirectoryEntityKind.ROOM ->
                                                    viewModel.openRoomSchedule(e)
                                                else -> Unit
                                            }
                                        },
                                    )
                                    AppListDivider()
                                }
                            }
                        }
                    }
                }
            }
            MoreDestination.ROOMS -> {
                if (state.roomEntity != null) {
                    if (state.loading) LoadingBox(Modifier.padding(padding))
                    else {
                        val week = state.roomSchedule
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.padding(padding),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                Text(
                                    stringResource(R.string.room_schedule) +
                                        if (week != null) " · uge ${week.week}" else "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (week == null || week.days.all { it.events.isEmpty() }) {
                                item {
                                    Text(
                                        stringResource(R.string.room_schedule_empty),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                week.days.forEach { day ->
                                    if (day.events.isEmpty()) return@forEach
                                    item(key = "rday-${day.date}") {
                                        Text(
                                            day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                                                " ${day.date.dayOfMonth}/${day.date.monthValue}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 8.dp),
                                        )
                                    }
                                    items(day.events, key = { "re-${it.id}" }) { ev ->
                                        AppListRow {
                                            AppListPrimary(ev.title, emphasized = true)
                                            AppListSecondary(ev.timeLabelText())
                                            ev.teacher?.let { AppListMeta(it) }
                                        }
                                        AppListDivider()
                                    }
                                }
                            }
                        }
                    }
                } else if (state.loading) {
                    LoadingBox(Modifier.padding(padding))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        item { SectionHeader(stringResource(R.string.rooms_occupancy_header)) }
                        items(state.roomsOccupancy, key = { it.id }) { room ->
                            AppListRow(
                                onClick = { viewModel.openRoomFromOccupancy(room) },
                            ) {
                                AppListPrimary("${room.shortName} · ${room.name}", emphasized = true)
                                AppListSecondary(
                                    if (room.inUse) {
                                        stringResource(R.string.room_in_use)
                                    } else {
                                        stringResource(R.string.room_free)
                                    },
                                )
                            }
                            AppListDivider()
                        }
                    }
                }
            }
            MoreDestination.STUDIEKORT -> {
                val card = state.card
                val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                ) { uri ->
                    if (uri != null) viewModel.uploadProfilePicture(uri)
                }
                if (card == null) {
                    LoadingBox(Modifier.padding(padding))
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // One intentional "card" — it's a student ID, not a list item.
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Text(
                                    stringResource(R.string.more_studiekort),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    if (card.photoUrl != null) {
                                        AsyncImage(
                                            model = card.photoUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(88.dp)
                                                .clip(RoundedCornerShape(12.dp)),
                                        )
                                    } else {
                                        Box(
                                            Modifier
                                                .size(88.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                (card.student.name ?: "?").take(1).uppercase(),
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            card.student.name ?: stringResource(R.string.student_fallback),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                        card.student.classLabel?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                            )
                                        }
                                        card.student.schoolName?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                            )
                                        }
                                        card.birthday?.let { bday ->
                                            Text(
                                                stringResource(R.string.studiekort_birthday, bday),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        TextButton(onClick = { photoPicker.launch("image/*") }) {
                            Text(stringResource(R.string.studiekort_change_photo))
                        }

                        card.qrUrl?.let { qr ->
                            SectionHeader(stringResource(R.string.more_studiekort_qr))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                                tonalElevation = 0.dp,
                            ) {
                                AsyncImage(
                                    model = qr,
                                    contentDescription = stringResource(R.string.cd_qr),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .padding(24.dp),
                                )
                            }
                        }
                        state.message?.let {
                            Text(it.asString(), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            MoreDestination.PLANS -> {
                if (state.loading && state.planDetail == null) {
                    LoadingBox(Modifier.padding(padding))
                } else if (state.planDetail != null) {
                    val plan = state.planDetail!!
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                                Text(
                                    plan.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (plan.team.isNotBlank()) {
                                    Text(
                                        plan.team,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp,
                            )
                            val body = plan.detailHtml.orEmpty()
                                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                                .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
                                .replace(Regex("<[^>]+>"), "")
                                .replace("&nbsp;", " ")
                                .replace("&amp;", "&")
                                .trim()
                            Text(
                                body.ifBlank { plan.title },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        items(state.plans, key = { it.id }) { p ->
                            AppListRow(onClick = { viewModel.openPlanDetail(p) }) {
                                AppListPrimary(p.title, emphasized = true)
                                if (p.team.isNotBlank()) AppListSecondary(p.team)
                            }
                            AppListDivider()
                        }
                    }
                }
            }
            MoreDestination.MODULE_STATS -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(state.moduleStats, key = { it.team }) { s ->
                    AppListRow {
                        AppListPrimary(s.team, emphasized = true)
                        AppListSecondary(
                            stringResource(R.string.module_stats_line, s.held, s.cancelled, s.changed),
                        )
                    }
                    AppListDivider()
                }
            }
            MoreDestination.TERM -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(state.terms, key = { it.id }) { t ->
                    AppListRow(
                        onClick = { viewModel.selectTerm(t.id) },
                        trailing = {
                            if (t.selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    ) {
                        AppListPrimary(t.name, emphasized = t.selected)
                        if (t.selected) AppListSecondary(stringResource(R.string.term_selected))
                    }
                    AppListDivider()
                }
            }
            MoreDestination.SETTINGS -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                item { SectionHeader(stringResource(R.string.settings_appearance)) }
                items(AppearanceMode.entries.toList(), key = { it.name }) { mode ->
                    val label = when (mode) {
                        AppearanceMode.SYSTEM -> stringResource(R.string.settings_appearance_system)
                        AppearanceMode.LIGHT -> stringResource(R.string.settings_appearance_light)
                        AppearanceMode.DARK -> stringResource(R.string.settings_appearance_dark)
                    }
                    AppListRow(
                        onClick = { viewModel.setAppearance(mode) },
                        trailing = {
                            if (appearance == mode) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    ) {
                        AppListPrimary(label, emphasized = appearance == mode)
                    }
                    AppListDivider()
                }

                item { SectionHeader(stringResource(R.string.settings_language)) }
                items(AppLanguage.entries.toList(), key = { it.name }) { lang ->
                    val label = when (lang) {
                        AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
                        AppLanguage.DANISH -> stringResource(R.string.settings_language_danish)
                        AppLanguage.ENGLISH -> stringResource(R.string.settings_language_english)
                    }
                    AppListRow(
                        onClick = { viewModel.setLanguage(lang) },
                        trailing = {
                            if (language == lang) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    ) {
                        AppListPrimary(label, emphasized = language == lang)
                    }
                    AppListDivider()
                }

                item { SectionHeader(stringResource(R.string.settings_calendar)) }
                items(CalendarStyle.entries.toList(), key = { it.name }) { style ->
                    val title = if (style == CalendarStyle.PROFESSIONAL) {
                        stringResource(R.string.settings_calendar_timeline)
                    } else {
                        stringResource(R.string.settings_calendar_list)
                    }
                    val hint = if (style == CalendarStyle.PROFESSIONAL) {
                        stringResource(R.string.settings_calendar_timeline_hint)
                    } else {
                        stringResource(R.string.settings_calendar_list_hint)
                    }
                    AppListRow(
                        onClick = { viewModel.setCalendarStyle(style) },
                        trailing = {
                            if (calendarStyle == style) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    ) {
                        AppListPrimary(title, emphasized = calendarStyle == style)
                        AppListSecondary(hint, maxLines = 2)
                    }
                    AppListDivider()
                }

                item {
                    SectionHeader(stringResource(R.string.settings_subject_colors))
                    Text(
                        stringResource(R.string.settings_subject_colors_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                items(viewModel.editableSubjects(), key = { it }) { subject ->
                    val current = subjectColors[subject] ?: viewModel.colorForSubject(subject)
                    val rename = state.subjectRenameDrafts[subject]
                        ?: subjectNames[subject]
                        ?: subject
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(current)),
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                subject,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        OutlinedTextField(
                            value = rename,
                            onValueChange = { viewModel.updateSubjectRenameDraft(subject, it) },
                            label = { Text(stringResource(R.string.settings_subject_rename)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            singleLine = true,
                        )
                        TextButton(onClick = { viewModel.saveSubjectRename(subject) }) {
                            Text(stringResource(R.string.settings_subject_rename_save))
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            viewModel.subjectColorOptions().forEach { argb ->
                                val selected = current == argb
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .then(
                                            if (selected) {
                                                Modifier.border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.onSurface,
                                                    CircleShape,
                                                )
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .clickable { viewModel.setSubjectColor(subject, argb) },
                                )
                            }
                        }
                    }
                    AppListDivider()
                }

                item { SectionHeader(stringResource(R.string.settings_notifications)) }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_notif_events)) },
                        trailingContent = {
                            Switch(checked = notifEvents, onCheckedChange = viewModel::setNotifEvents)
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_notif_messages)) },
                        trailingContent = {
                            Switch(checked = notifMessages, onCheckedChange = viewModel::setNotifMessages)
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_notif_assignments)) },
                        trailingContent = {
                            Switch(checked = notifAssignments, onCheckedChange = viewModel::setNotifAssignments)
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                    AppListDivider()
                }

                item { SectionHeader(stringResource(R.string.settings_notif_history)) }
                if (notificationHistory.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.settings_notif_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                } else {
                    items(notificationHistory.take(10)) { entry ->
                        val text = entry.substringAfter('|', entry)
                        AppListRow { AppListSecondary(text, maxLines = 3) }
                        AppListDivider()
                    }
                    item {
                        TextButton(
                            onClick = viewModel::clearNotificationHistory,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            Text(stringResource(R.string.settings_notif_history_clear))
                        }
                    }
                }

                item { SectionHeader(stringResource(R.string.settings_section_data)) }
                item {
                    AppListRow(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.privacyPolicyUrl)),
                            )
                        },
                    ) {
                        AppListPrimary(stringResource(R.string.settings_privacy), emphasized = true)
                        AppListSecondary(viewModel.privacyPolicyUrl, maxLines = 1)
                    }
                    AppListDivider()
                    AppListRow(onClick = viewModel::pullSubjectSync) {
                        AppListPrimary(stringResource(R.string.settings_sync_subjects), emphasized = true)
                        AppListSecondary(stringResource(R.string.settings_sync_subjects_hint))
                    }
                    AppListDivider()
                    AppListRow(
                        onClick = {
                            val act = context as? android.app.Activity
                            if (act != null) viewModel.checkForUpdatesWithActivity(act)
                            else viewModel.checkForUpdates()
                        },
                        leading = {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ) {
                        AppListPrimary(stringResource(R.string.settings_check_updates), emphasized = true)
                        AppListSecondary(stringResource(R.string.settings_version, viewModel.appVersion()))
                    }
                    AppListDivider()
                    AppListRow(
                        onClick = viewModel::clearCache,
                        leading = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ) {
                        AppListPrimary(stringResource(R.string.settings_clear_cache), emphasized = true)
                    }
                    state.message?.let {
                        Text(
                            it.asString(),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            MoreDestination.HELP -> LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
            ) {
                item {
                    Text(stringResource(R.string.help_title), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.help_body), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun MoreRoot(
    padding: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    studentName: String,
    classLabel: String?,
    photoUrl: String?,
    onNavigate: (MoreDestination) -> Unit,
    onLogout: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (photoUrl != null) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            studentName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(
                        studentName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    classLabel?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp,
            )
        }
        item { SectionHeader(stringResource(R.string.more_section_school)) }
        item { MoreLink(Icons.Default.Grade, stringResource(R.string.more_grades)) { onNavigate(MoreDestination.GRADES) } }
        item { MoreLink(Icons.Default.Warning, stringResource(R.string.more_absence)) { onNavigate(MoreDestination.ABSENCE) } }
        item { MoreLink(Icons.Default.People, stringResource(R.string.more_directory)) { onNavigate(MoreDestination.DIRECTORY) } }
        item { MoreLink(Icons.Default.CalendarMonth, stringResource(R.string.more_rooms)) { onNavigate(MoreDestination.ROOMS) } }
        item { MoreLink(Icons.Default.Badge, stringResource(R.string.more_studiekort)) { onNavigate(MoreDestination.STUDIEKORT) } }
        item { MoreLink(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.more_plans)) { onNavigate(MoreDestination.PLANS) } }
        item { MoreLink(Icons.Default.BarChart, stringResource(R.string.more_module_stats)) { onNavigate(MoreDestination.MODULE_STATS) } }
        item { MoreLink(Icons.Default.CalendarMonth, stringResource(R.string.more_term)) { onNavigate(MoreDestination.TERM) } }
        item { SectionHeader(stringResource(R.string.more_section_app)) }
        item { MoreLink(Icons.Default.Help, stringResource(R.string.more_help)) { onNavigate(MoreDestination.HELP) } }
        item { MoreLink(Icons.Default.Settings, stringResource(R.string.more_settings)) { onNavigate(MoreDestination.SETTINGS) } }
        item { MoreLink(Icons.AutoMirrored.Filled.ExitToApp, stringResource(R.string.action_logout), onLogout) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MoreLink(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun DirectoryEntityRow(
    entity: DirectoryEntity,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onClick: () -> Unit,
) {
    AppListRow(
        onClick = onClick,
        leading = {
            if (entity.avatarUrl != null) {
                AsyncImage(
                    model = entity.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                )
            } else {
                InitialsAvatar(entity.name)
            }
        },
        trailing = {
            IconButton(onClick = onTogglePin) {
                Icon(
                    if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (pinned) {
                        stringResource(R.string.directory_unpin)
                    } else {
                        stringResource(R.string.directory_pin)
                    },
                    tint = if (pinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
    ) {
        AppListPrimary(entity.name, emphasized = true)
        val meta = listOfNotNull(
            entity.kind.name.lowercase().replaceFirstChar { it.uppercase() },
            entity.subtitle,
        ).joinToString(" · ")
        if (meta.isNotBlank()) AppListSecondary(meta)
    }
}
