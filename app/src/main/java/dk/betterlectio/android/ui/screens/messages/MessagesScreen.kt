package dk.betterlectio.android.ui.screens.messages

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import java.time.LocalDateTime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dk.betterlectio.android.R
import dk.betterlectio.android.core.i18n.asString
import dk.betterlectio.android.feature.messages.MessageSearch
import dk.betterlectio.android.feature.messages.MessageThread
import dk.betterlectio.android.feature.messages.MessageThreadDetail
import dk.betterlectio.android.ui.components.AppListDivider
import dk.betterlectio.android.ui.components.AppListMeta
import dk.betterlectio.android.ui.components.AppListPrimary
import dk.betterlectio.android.ui.components.AppListRow
import dk.betterlectio.android.ui.components.AppListSecondary
import dk.betterlectio.android.ui.components.EmptyBox
import dk.betterlectio.android.ui.components.ErrorBox
import dk.betterlectio.android.ui.components.InitialsAvatar
import dk.betterlectio.android.ui.components.ListSkeleton
import dk.betterlectio.android.ui.components.SectionHeader
import dk.betterlectio.android.ui.components.UnreadDot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.launch

private object MsgRoutes {
    const val LIST = "messages_list"
    const val THREAD = "messages_thread/{threadId}"
    const val COMPOSE = "messages_compose"
    fun thread(id: String) = "messages_thread/$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel = hiltViewModel(),
    scrollToTopToken: Int = 0,
) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Keep nav in sync when detail is closed externally (e.g. delete)
    LaunchedEffect(state.detail) {
        if (state.detail == null) {
            val route = navController.currentBackStackEntry?.destination?.route
            if (route?.startsWith("messages_thread") == true) {
                navController.popBackStack(MsgRoutes.LIST, inclusive = false)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = MsgRoutes.LIST,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(MsgRoutes.LIST) {
            MessageListPane(
                viewModel = viewModel,
                scrollToTopToken = scrollToTopToken,
                onOpenThread = { thread ->
                    viewModel.openThread(thread)
                    navController.navigate(MsgRoutes.thread(thread.id))
                },
                onCompose = {
                    viewModel.openCompose()
                    navController.navigate(MsgRoutes.COMPOSE)
                },
            )
        }
        composable(
            route = MsgRoutes.THREAD,
            arguments = listOf(navArgument("threadId") { type = NavType.StringType }),
        ) {
            BackHandler { viewModel.closeDetail(); navController.popBackStack() }
            val detail = state.detail
            if (detail == null) {
                // Still loading or missing — show spinner scaffold
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.tab_messages)) },
                            navigationIcon = {
                                IconButton(onClick = {
                                    viewModel.closeDetail()
                                    navController.popBackStack()
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.cd_back),
                                    )
                                }
                            },
                        )
                    },
                ) { p ->
                    ListSkeleton(modifier = Modifier.padding(p))
                }
            } else {
                MessageThreadPane(
                    detail = detail,
                    replyText = state.replyText,
                    onReplyChange = viewModel::onReplyChange,
                    onSendReply = viewModel::sendReply,
                    onMarkRead = viewModel::markRead,
                    onToggleFlag = viewModel::toggleFlag,
                    onDelete = {
                        viewModel.deleteCurrent()
                        navController.popBackStack()
                    },
                    onBack = {
                        viewModel.closeDetail()
                        navController.popBackStack()
                    },
                )
            }
        }
        composable(MsgRoutes.COMPOSE) {
            BackHandler { viewModel.closeCompose(); navController.popBackStack() }
            MessageComposePane(
                state = state,
                viewModel = viewModel,
                onBack = {
                    viewModel.closeCompose()
                    navController.popBackStack()
                },
                onSent = {
                    navController.popBackStack()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageListPane(
    viewModel: MessagesViewModel,
    scrollToTopToken: Int,
    onOpenThread: (MessageThread) -> Unit,
    onCompose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val timeFmt = DateTimeFormatter.ofPattern("d. MMM", Locale.getDefault())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val markedReadLabel = stringResource(R.string.message_marked_read_snackbar)
    val deletedLabel = stringResource(R.string.message_deleted_snackbar)
    var searchQuery by remember { mutableStateOf("") }
    val filteredThreads = remember(state.threads, searchQuery) {
        MessageSearch.filter(state.threads, searchQuery)
    }

    LaunchedEffect(scrollToTopToken) {
        if (scrollToTopToken > 0) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_messages)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCompose,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.message_compose))
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.threads.isNotEmpty(),
            onRefresh = { viewModel.refresh(true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    placeholder = { Text(stringResource(R.string.message_search_hint)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.cd_search),
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cd_clear_search),
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                    ),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.folders) { folder ->
                        FilterChip(
                            selected = state.selectedFolder.id == folder.id,
                            onClick = { viewModel.selectFolder(folder) },
                            label = { Text(folder.displayName) },
                        )
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp,
                )
                when {
                    state.loading && state.threads.isEmpty() -> ListSkeleton()
                    state.error != null && state.threads.isEmpty() ->
                        ErrorBox(state.error, onRetry = { viewModel.refresh(true) })
                    state.threads.isEmpty() -> EmptyBox(
                        text = stringResource(R.string.empty_messages),
                        description = stringResource(R.string.empty_messages_folder_hint),
                        icon = Icons.Outlined.MailOutline,
                        actionLabel = stringResource(R.string.message_compose),
                        onAction = onCompose,
                    )
                    filteredThreads.isEmpty() -> EmptyBox(
                        text = stringResource(R.string.empty_messages_search),
                        description = stringResource(R.string.empty_messages_search_hint),
                        icon = Icons.Default.Search,
                        actionLabel = stringResource(R.string.cd_clear_search),
                        onAction = { searchQuery = "" },
                    )
                    else -> {
                        val sections = remember(filteredThreads) {
                            groupThreadsByRecency(filteredThreads)
                        }
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            sections.forEach { section ->
                                item(key = "hdr-${section.key}") {
                                    SectionHeader(stringResource(section.titleRes))
                                }
                                items(section.threads, key = { it.id }) { thread ->
                                    SwipeableMessageRow(
                                        thread = thread,
                                        timeFmt = timeFmt,
                                        onOpen = { onOpenThread(thread) },
                                        onMarkRead = {
                                            if (thread.unread) {
                                                viewModel.markThreadRead(thread)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(markedReadLabel)
                                                }
                                            }
                                        },
                                        onDelete = {
                                            viewModel.deleteThread(thread)
                                            scope.launch {
                                                snackbarHostState.showSnackbar(deletedLabel)
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
        }
    }
}

private data class MessageDateSection(
    val key: String,
    val titleRes: Int,
    val threads: List<MessageThread>,
)

private fun groupThreadsByRecency(threads: List<MessageThread>): List<MessageDateSection> {
    val today = LocalDate.now()
    val buckets = linkedMapOf(
        "today" to mutableListOf<MessageThread>(),
        "yesterday" to mutableListOf(),
        "week" to mutableListOf(),
        "earlier" to mutableListOf(),
    )
    threads.forEach { thread ->
        val date = thread.dateChanged?.toLocalDate()
        val key = when {
            date == null -> "earlier"
            date == today -> "today"
            date == today.minusDays(1) -> "yesterday"
            ChronoUnit.DAYS.between(date, today) in 2..6 -> "week"
            else -> "earlier"
        }
        buckets.getValue(key).add(thread)
    }
    return listOf(
        "today" to R.string.message_section_today,
        "yesterday" to R.string.message_section_yesterday,
        "week" to R.string.message_section_this_week,
        "earlier" to R.string.message_section_earlier,
    ).mapNotNull { (key, res) ->
        val list = buckets.getValue(key)
        if (list.isEmpty()) null
        else MessageDateSection(key = key, titleRes = res, threads = list)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableMessageRow(
    thread: MessageThread,
    timeFmt: DateTimeFormatter,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (thread.unread) onMarkRead()
                    false // keep row; unread state updates in place
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isDelete = direction == SwipeToDismissBoxValue.EndToStart
            val color = if (isDelete) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
            val icon = if (isDelete) Icons.Default.Delete else Icons.Default.Drafts
            val alignment = if (isDelete) Alignment.CenterEnd else Alignment.CenterStart
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment,
            ) {
                Icon(
                    icon,
                    contentDescription = if (isDelete) {
                        stringResource(R.string.message_swipe_delete)
                    } else {
                        stringResource(R.string.message_swipe_read)
                    },
                    tint = if (isDelete) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
        },
        enableDismissFromStartToEnd = thread.unread,
        enableDismissFromEndToStart = true,
    ) {
        AppListRow(
            onClick = onOpen,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            leading = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UnreadDot(visible = thread.unread)
                    Spacer(Modifier.width(8.dp))
                    InitialsAvatar(thread.sender.ifBlank { thread.topic })
                }
            },
            trailing = {
                Column(horizontalAlignment = Alignment.End) {
                    thread.dateChanged?.let {
                        AppListMeta(it.toLocalDate().format(timeFmt))
                    }
                    if (thread.flagged) {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(16.dp),
                        )
                    }
                }
            },
        ) {
            AppListPrimary(thread.topic, emphasized = thread.unread)
            AppListSecondary(thread.sender)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageThreadPane(
    detail: MessageThreadDetail,
    replyText: String,
    onReplyChange: (String) -> Unit,
    onSendReply: () -> Unit,
    onMarkRead: () -> Unit,
    onToggleFlag: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        detail.thread.topic,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    if (detail.thread.unread) {
                        IconButton(onClick = onMarkRead) {
                            Icon(
                                Icons.Default.Drafts,
                                contentDescription = stringResource(R.string.message_mark_read),
                            )
                        }
                    }
                    IconButton(onClick = onToggleFlag) {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = if (detail.thread.flagged) {
                                stringResource(R.string.message_unflag)
                            } else {
                                stringResource(R.string.message_flag)
                            },
                            tint = if (detail.thread.flagged) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.message_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { padding ->
        val dateTimeFmt = remember {
            DateTimeFormatter.ofPattern("d. MMM yyyy · HH:mm", Locale.getDefault())
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
            ) {
                if (detail.receivers.isNotEmpty()) {
                    Text(
                        stringResource(R.string.message_receivers),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        detail.receivers.joinToString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                detail.entries.forEach { entry ->
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        thickness = 0.5.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        InitialsAvatar(entry.senderName.orEmpty().ifBlank { "?" })
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.senderName.orEmpty().ifBlank { "—" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            entry.sentAt?.let { sent ->
                                Text(
                                    formatMessageTimestamp(sent, dateTimeFmt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(8.dp),
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = false
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    loadDataWithBaseURL(
                                        "https://www.lectio.dk",
                                        wrapMessageHtml(entry.contentHtml.orEmpty()),
                                        "text/html",
                                        "UTF-8",
                                        null,
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            update = { wv ->
                                wv.loadDataWithBaseURL(
                                    "https://www.lectio.dk",
                                    wrapMessageHtml(entry.contentHtml.orEmpty()),
                                    "text/html",
                                    "UTF-8",
                                    null,
                                )
                            },
                        )
                    }
                    if (entry.attachments.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        entry.attachments.forEach { att ->
                            AssistChip(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(att.url)))
                                },
                                label = {
                                    Text(stringResource(R.string.message_attachment, att.name))
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp,
            )
            // Sticky reply composer (keyboard-aware)
            SurfaceReplyBar(
                replyText = replyText,
                onReplyChange = onReplyChange,
                onSendReply = onSendReply,
            )
        }
    }
}

@Composable
private fun SurfaceReplyBar(
    replyText: String,
    onReplyChange: (String) -> Unit,
    onSendReply: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = replyText,
            onValueChange = onReplyChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.message_reply_hint)) },
            minLines = 1,
            maxLines = 5,
            shape = RoundedCornerShape(16.dp),
        )
        Button(
            onClick = onSendReply,
            enabled = replyText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.message_send))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MessageComposePane(
    state: MessagesUiState,
    viewModel: MessagesViewModel,
    onBack: () -> Unit,
    onSent: () -> Unit,
) {
    LaunchedEffect(state.showCompose, state.composeMessage) {
        if (!state.showCompose && state.composeMessage != null) onSent()
    }
    val canSend = state.composeSubject.isNotBlank() &&
        state.composeBody.isNotBlank() &&
        state.selectedRecipients.isNotEmpty()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.message_compose)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.composeSubject,
                onValueChange = { viewModel.updateCompose(subject = it) },
                label = { Text(stringResource(R.string.message_subject)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.recipientQuery,
                onValueChange = viewModel::onRecipientQuery,
                label = { Text(stringResource(R.string.message_recipients)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (state.selectedRecipients.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.selectedRecipients.forEach { r ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.toggleRecipient(r) },
                            label = { Text(r.name) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cd_clear_search),
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
            state.recipientResults
                .filter { result -> state.selectedRecipients.none { it.id == result.id } }
                .take(8)
                .forEach { r ->
                    AppListRow(
                        onClick = { viewModel.toggleRecipient(r) },
                        leading = { InitialsAvatar(r.name) },
                    ) {
                        AppListPrimary(r.name, emphasized = true)
                        if (r.kind.isNotBlank()) {
                            AppListSecondary(r.kind)
                        }
                    }
                }
            OutlinedTextField(
                value = state.composeBody,
                onValueChange = { viewModel.updateCompose(body = it) },
                label = { Text(stringResource(R.string.message_body)) },
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
            state.composeMessage?.let {
                Text(it.asString(), color = MaterialTheme.colorScheme.primary)
            }
            Button(
                onClick = viewModel::sendCompose,
                enabled = canSend,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.message_send))
            }
            if (!canSend) {
                Text(
                    stringResource(R.string.message_compose_send_disabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatMessageTimestamp(value: LocalDateTime, fmt: DateTimeFormatter): String =
    value.format(fmt)

/** Light Lectio HTML wrapper so bodies match app text size/color better. */
private fun wrapMessageHtml(body: String): String {
    if (body.isBlank()) return ""
    return """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1"/>
        <style>
          body { font-family: sans-serif; font-size: 15px; line-height: 1.45;
                 color: #1a1c1e; margin: 0; padding: 4px; word-wrap: break-word; }
          a { color: #3362E1; }
          img { max-width: 100%; height: auto; }
          table { max-width: 100%; }
        </style></head><body>$body</body></html>
    """.trimIndent()
}
