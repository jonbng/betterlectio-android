package dk.betterlectio.android.ui.screens.homework

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dk.betterlectio.android.R
import dk.betterlectio.android.feature.homework.HomeworkDetailLoader
import dk.betterlectio.android.feature.homework.HomeworkItem
import dk.betterlectio.android.feature.schedule.LessonDetailParser
import dk.betterlectio.android.ui.components.AttachmentRow
import dk.betterlectio.android.ui.components.DetailSection
import dk.betterlectio.android.ui.components.EmptyBox
import dk.betterlectio.android.ui.components.ErrorBox
import dk.betterlectio.android.ui.components.LectioHtmlBody
import dk.betterlectio.android.ui.components.LessonContentBlocks
import dk.betterlectio.android.ui.components.ListSkeleton
import dk.betterlectio.android.ui.components.isDueUrgent
import dk.betterlectio.android.ui.components.relativeDaySectionLabel
import dk.betterlectio.android.ui.components.relativeDueLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkScreen(
    viewModel: HomeworkViewModel = hiltViewModel(),
    scrollToTopToken: Int = 0,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onVisible() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    @Suppress("UNUSED_VARIABLE")
    val lessonMappings by viewModel.lessonMappings.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    HomeworkListPane(
        viewModel = viewModel,
        scrollToTopToken = scrollToTopToken,
        onOpen = viewModel::select,
    )
    state.selected?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.select(null) },
            sheetState = sheetState,
        ) {
            Box(Modifier.fillMaxHeight(0.92f)) {
                HomeworkDetailPane(
                    item = item,
                    displayTeam = viewModel::displayTeam,
                    onBack = { viewModel.select(null) },
                    onToggleDone = {
                        viewModel.toggleDone(item.id)
                        viewModel.select(null)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeworkListPane(
    viewModel: HomeworkViewModel,
    scrollToTopToken: Int,
    onOpen: (HomeworkItem) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(scrollToTopToken) {
        if (scrollToTopToken > 0) listState.animateScrollToItem(0)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_homework)) },
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
            when {
                state.loading && state.items.isEmpty() -> ListSkeleton()
                state.error != null && state.items.isEmpty() ->
                    ErrorBox(state.error, onRetry = { viewModel.refresh(true) })
                state.items.isEmpty() -> EmptyBox(
                    text = stringResource(R.string.empty_homework_all_clear),
                    description = stringResource(R.string.empty_homework_all_clear_hint),
                    icon = Icons.Default.CheckCircle,
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                ) {
                    state.groups.forEach { group ->
                        item(key = "header-${group.date ?: "none"}-${group.label}") {
                            HomeworkDayHeader(
                                title = relativeDaySectionLabel(group.date),
                                count = group.items.size,
                                allDone = group.items.all { it.done },
                            )
                        }
                        items(group.items, key = { it.id }) { item ->
                            SwipeableHomeworkRow(
                                item = item,
                                displayTeam = viewModel::displayTeam,
                                onOpen = { onOpen(item) },
                                onToggleDone = {
                                    viewModel.toggleDone(item.id)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableHomeworkRow(
    item: HomeworkItem,
    displayTeam: (String) -> String = { it },
    onOpen: () -> Unit,
    onToggleDone: () -> Unit,
) {
    val summary = item.note.ifBlank { item.tasks.joinToString("\n") { it.text } }
    val subjectName = displayTeam(item.team.ifBlank { item.activityTitle })
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.4f },
    )

    LaunchedEffect(dismissState, item.id) {
        var actionConsumed = false
        snapshotFlow { dismissState.currentValue }.collect { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart,
                SwipeToDismissBoxValue.StartToEnd,
                -> if (!actionConsumed) {
                    actionConsumed = true
                    onToggleDone()
                    dismissState.reset()
                }
                SwipeToDismissBoxValue.Settled -> actionConsumed = false
            }
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp)),
        backgroundContent = {
            val done = item.done
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (done) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = if (
                    dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                ) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (done) Icons.Default.Restore else Icons.Default.Check,
                        contentDescription = null,
                        tint = if (done) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    )
                    Text(
                        if (done) {
                            stringResource(R.string.homework_swipe_undo)
                        } else {
                            stringResource(R.string.homework_swipe_done)
                        },
                        color = if (done) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
    ) {
        val dueLabel = relativeDueLabel(item.date)
        val urgent = !item.done && isDueUrgent(item.date)
        Surface(
            onClick = onOpen,
            color = if (item.done) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomeworkCompletionToggle(
                    checked = item.done,
                    onCheckedChange = { onToggleDone() },
                )
                Column(Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            subjectName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (item.done) FontWeight.Normal else FontWeight.SemiBold,
                            color = if (item.done) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textDecoration = if (item.done) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        dueLabel?.let { label ->
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (urgent) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (urgent) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    if (summary.isNotBlank()) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeworkDayHeader(title: String, count: Int, allDone: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (allDone) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            stringResource(R.string.homework_day_count, count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeworkDuePill(text: String, urgent: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (urgent) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (urgent) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun HomeworkCompletionToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionLabel = stringResource(
        if (checked) R.string.homework_swipe_undo else R.string.homework_mark_done,
    )
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = actionLabel },
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            color = if (checked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = if (checked) {
                null
            } else {
                BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (checked) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeworkDetailPane(
    item: HomeworkItem,
    displayTeam: (String) -> String = { it },
    onBack: () -> Unit,
    onToggleDone: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val textTasks = item.textTasks
    val linkedTasks = item.linkedTasks
    val subjectName = displayTeam(item.team.ifBlank { item.activityTitle })
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                if (item.done) {
                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleDone()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.homework_swipe_undo))
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleDone()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.homework_mark_done))
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.homework_detail_title)) },
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
        ColumnScroll(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            subjectName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        relativeDueLabel(item.date)?.let { due ->
                            HomeworkDuePill(
                                text = due,
                                urgent = isDueUrgent(item.date) && !item.done,
                            )
                        }
                    }
                    val metadata = listOfNotNull(item.teacher, item.room)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (metadata.isNotBlank()) {
                        Text(
                            metadata,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (item.note.isNotBlank() || textTasks.isNotEmpty()) {
                DetailSection(stringResource(R.string.label_homework)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.note.isNotBlank()) {
                            Text(
                                item.note,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        textTasks.forEach { task ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    "•",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    task.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            item.detailHtml?.let { html ->
                DetailSection(stringResource(R.string.homework_lesson_content)) {
                    HomeworkDetailContent(
                        html = html,
                        itemId = item.id,
                        title = item.activityTitle,
                        snackbarHostState = snackbarHostState,
                    )
                }
            }
            if (linkedTasks.isNotEmpty()) {
                DetailSection(stringResource(R.string.homework_links)) {
                    linkedTasks.forEach { task ->
                        AttachmentRow(
                            name = task.text.ifBlank { task.url.orEmpty() },
                            url = task.url.orEmpty(),
                            isFileHint = false,
                            snackbarHostState = snackbarHostState,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ColumnScroll(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        content = { content() },
    )
}

/**
 * Prefer structured lesson parsing (activity pages with images/blocks); fall back to
 * fragment HTML rendering so inline images still load with the Lectio session.
 */
@Composable
private fun HomeworkDetailContent(
    html: String,
    itemId: String,
    title: String,
    snackbarHostState: SnackbarHostState,
) {
    val looksLikeActivityPage = remember(html) {
        html.contains("inlineHomework", ignoreCase = true) ||
            html.contains("homeworkContentContainer", ignoreCase = true) ||
            html.contains("ls-paper", ignoreCase = true) ||
            html.contains("tocAndToolbar", ignoreCase = true)
    }
    val lessonDetail = remember(html, itemId, title, looksLikeActivityPage) {
        if (!looksLikeActivityPage) null
        else LessonDetailParser.parse(html, itemId, title).takeIf { it.contentBlocks.isNotEmpty() }
    }
    when {
        lessonDetail != null -> {
            lessonDetail.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(note, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            LessonContentBlocks(lessonDetail.contentBlocks)
            if (lessonDetail.resources.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                lessonDetail.resources.forEach { r ->
                    AttachmentRow(
                        name = r.title,
                        url = r.url,
                        isFileHint = r.isFile,
                        snackbarHostState = snackbarHostState,
                    )
                }
            }
        }
        else -> {
            val segmentsEmpty = remember(html) {
                dk.betterlectio.android.feature.content.LectioHtmlSegments.parse(html).isEmpty()
            }
            if (segmentsEmpty) {
                val plain = remember(html) { HomeworkDetailLoader.plainTextFromHtml(html) }
                if (plain.isNotBlank()) {
                    Text(plain, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LectioHtmlBody(html = html)
            }
        }
    }
}
