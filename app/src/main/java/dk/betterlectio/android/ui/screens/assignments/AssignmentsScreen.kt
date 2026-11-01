package dk.betterlectio.android.ui.screens.assignments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dk.betterlectio.android.R
import dk.betterlectio.android.feature.assignments.AssignmentDetail
import dk.betterlectio.android.feature.assignments.AssignmentFilter
import dk.betterlectio.android.feature.assignments.AssignmentItem
import dk.betterlectio.android.feature.assignments.matches
import dk.betterlectio.android.ui.components.AttachmentRow
import dk.betterlectio.android.ui.components.DetailMetaLine
import dk.betterlectio.android.ui.components.DetailSection
import dk.betterlectio.android.ui.components.EmptyBox
import dk.betterlectio.android.ui.components.ErrorBox
import dk.betterlectio.android.ui.components.ListSkeleton
import dk.betterlectio.android.ui.components.StatusChip
import dk.betterlectio.android.ui.components.isDueUrgent
import dk.betterlectio.android.ui.components.relativeDueLabel
import java.time.LocalDate
import java.time.temporal.WeekFields

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentsScreen(
    viewModel: AssignmentsViewModel = hiltViewModel(),
    scrollToTopToken: Int = 0,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onVisible() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    AssignmentsListPane(
        viewModel = viewModel,
        scrollToTopToken = scrollToTopToken,
        onOpen = viewModel::openDetail,
    )
    state.detail?.let { detail ->
        ModalBottomSheet(
            onDismissRequest = viewModel::closeDetail,
            sheetState = sheetState,
        ) {
            Box(Modifier.fillMaxHeight(0.92f)) {
                AssignmentDetailPane(
                    detail = detail,
                    onBack = viewModel::closeDetail,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignmentsListPane(
    viewModel: AssignmentsViewModel,
    scrollToTopToken: Int,
    onOpen: (AssignmentItem) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val currentWeek = remember {
        LocalDate.now().get(WeekFields.ISO.weekOfWeekBasedYear())
    }
    val groupedAssignments = remember(state.filtered) {
        state.filtered.groupBy { it.week }.toList()
    }
    var didInitialWeekScroll by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(scrollToTopToken) {
        if (scrollToTopToken > 0) listState.animateScrollToItem(0)
    }
    LaunchedEffect(groupedAssignments, state.filter) {
        if (!didInitialWeekScroll && state.filter == AssignmentFilter.ALL && groupedAssignments.isNotEmpty()) {
            val groupIndex = groupedAssignments.indexOfFirst { it.first == currentWeek }
            if (groupIndex >= 0) {
                val itemIndex = groupedAssignments.take(groupIndex).sumOf { (_, items) -> items.size + 1 }
                listState.scrollToItem(itemIndex)
            }
            didInitialWeekScroll = true
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_assignments)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.items.isNotEmpty(),
            onRefresh = { viewModel.refresh(true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.fillMaxSize()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(AssignmentFilter.entries) { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = {
                                val label = when (filter) {
                                        AssignmentFilter.ALL -> stringResource(R.string.filter_all)
                                        AssignmentFilter.AWAITING_ME -> stringResource(R.string.filter_awaiting)
                                        AssignmentFilter.DELIVERED -> stringResource(R.string.filter_delivered)
                                        AssignmentFilter.MISSING -> stringResource(R.string.filter_missing)
                                    }
                                Text("$label ${state.items.count { it.matches(filter) }}")
                            },
                        )
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp,
                )
                when {
                    state.loading && state.items.isEmpty() -> ListSkeleton()
                    state.error != null && state.items.isEmpty() ->
                        ErrorBox(state.error, onRetry = { viewModel.refresh(true) })
                    state.filtered.isEmpty() -> EmptyBox(
                        text = stringResource(R.string.empty_assignments),
                        description = stringResource(
                            if (state.filter == AssignmentFilter.ALL) {
                                R.string.empty_assignments_all_hint
                            } else {
                                R.string.empty_assignments_hint
                            },
                        ),
                        icon = Icons.AutoMirrored.Outlined.Assignment,
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = { viewModel.refresh(true) },
                    )
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        groupedAssignments.forEach { (week, assignments) ->
                            item(key = "week-$week") {
                                AssignmentWeekHeader(
                                    week = week,
                                    assignments = assignments,
                                    current = week == currentWeek,
                                )
                            }
                            items(assignments, key = { it.id }) { item ->
                                AssignmentCard(item = item, onClick = { onOpen(item) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssignmentWeekHeader(
    week: Int,
    assignments: List<AssignmentItem>,
    current: Boolean,
) {
    val totalHours = assignments.sumOf { it.studentTime }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${stringResource(R.string.label_week)} $week",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (totalHours > 0) {
            Text(
                stringResource(
                    R.string.assignment_hours_short,
                    totalHours.toString().removeSuffix(".0").replace('.', ','),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AssignmentCard(item: AssignmentItem, onClick: () -> Unit) {
    val delivered = item.status.contains("aflever", ignoreCase = true) ||
        item.status.contains("godkendt", ignoreCase = true) ||
        item.status.contains("afsluttet", ignoreCase = true)
    val urgent = !delivered && isDueUrgent(item.deadline)
    val statusColor = when {
        urgent || item.status.contains("mangl", ignoreCase = true) -> MaterialTheme.colorScheme.error
        delivered -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.team.isNotBlank()) {
                        Text(
                            item.team,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (item.status.isNotBlank()) StatusChip(text = item.status, color = statusColor)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    relativeDueLabel(item.deadline)
                        ?: stringResource(R.string.assignment_no_deadline),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    maxLines = 1,
                )
                if (item.studentTime > 0) {
                    Text(
                        stringResource(
                            R.string.assignment_hours_short,
                            item.studentTime.toString().removeSuffix(".0").replace('.', ','),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignmentDetailPane(
    detail: AssignmentDetail,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val statusColor = when {
        detail.item.status.contains("mangler", ignoreCase = true) ||
            detail.item.status.contains("afventer", ignoreCase = true) ||
            detail.item.status.contains("venter", ignoreCase = true) ->
            MaterialTheme.colorScheme.error
        detail.item.status.contains("afleveret", ignoreCase = true) ||
            detail.item.status.contains("godkendt", ignoreCase = true) ||
            detail.item.status.contains("afsluttet", ignoreCase = true) ->
            MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dueLabel = relativeDueLabel(detail.item.deadline)
    val urgent = isDueUrgent(detail.item.deadline) &&
        !detail.item.status.contains("aflever", ignoreCase = true)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assignment_detail_title)) },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = if (urgent) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            dueLabel ?: stringResource(R.string.assignment_no_deadline),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (urgent) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                        )
                        if (detail.item.status.isNotBlank()) {
                            StatusChip(text = detail.item.status, color = statusColor)
                        }
                    }
                    Text(
                        detail.item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (urgent) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (detail.item.team.isNotBlank()) {
                        Text(
                            detail.item.team,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (urgent) {
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.72f)
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssignmentMetric(
                    label = stringResource(R.string.label_week),
                    value = detail.item.week.toString(),
                    modifier = Modifier.weight(1f),
                )
                AssignmentMetric(
                    label = stringResource(R.string.label_hours),
                    value = detail.item.studentTime.toString().removeSuffix(".0").replace('.', ','),
                    modifier = Modifier.weight(1f),
                )
                AssignmentMetric(
                    label = stringResource(R.string.label_awaits),
                    value = detail.item.awaits.ifBlank { "–" },
                    modifier = Modifier.weight(1.35f),
                )
            }
            if (detail.responsible.isNotBlank() || detail.grading.isNotBlank()) {
                DetailSection(stringResource(R.string.label_status)) {
                    if (detail.responsible.isNotBlank()) {
                        DetailMetaLine(stringResource(R.string.label_teacher), detail.responsible)
                    }
                    if (detail.grading.isNotBlank()) {
                        DetailMetaLine(stringResource(R.string.label_grading), detail.grading)
                    }
                }
            }
            if (detail.description.isNotBlank()) {
                DetailSection(stringResource(R.string.assignment_description)) {
                    Text(detail.description, style = MaterialTheme.typography.bodyLarge)
                }
            }
            val grade = detail.studentGrade?.takeIf { it.isNotBlank() }
                ?: detail.item.grade?.takeIf { it.isNotBlank() }
            val gradeNote = detail.studentGradeNote?.takeIf { it.isNotBlank() }
                ?: detail.item.gradeNote?.takeIf { it.isNotBlank() }
            if (grade != null || gradeNote != null) {
                DetailSection(stringResource(R.string.assignment_grade)) {
                    grade?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    gradeNote?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (detail.files.isNotEmpty()) {
                DetailSection(stringResource(R.string.assignment_files)) {
                    detail.files.distinctBy { it.second }.forEach { (name, url) ->
                        AttachmentRow(
                            name = name,
                            url = url,
                            isFileHint = true,
                            snackbarHostState = snackbarHostState,
                        )
                    }
                }
            }
            if (detail.submissions.isNotEmpty()) {
                DetailSection(stringResource(R.string.assignment_submissions)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detail.submissions.forEach { submission ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        submission.user.ifBlank { submission.timestamp },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (submission.timestamp.isNotBlank() && submission.user.isNotBlank()) {
                                        Text(
                                            submission.timestamp,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    submission.comment?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    }
                                    val documentUrl = submission.documentUrl
                                    if (!documentUrl.isNullOrBlank()) {
                                        AttachmentRow(
                                            name = submission.documentName.orEmpty()
                                                .ifBlank { stringResource(R.string.assignment_files) },
                                            url = documentUrl,
                                            isFileHint = true,
                                            snackbarHostState = snackbarHostState,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssignmentMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
