package dk.betterlectio.android.ui.screens.messages

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dk.betterlectio.android.ui.components.UnreadDot
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                    state.threads.isEmpty() -> EmptyBox(stringResource(R.string.empty_messages))
                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(state.threads, key = { it.id }) { thread ->
                            AppListRow(
                                onClick = { onOpenThread(thread) },
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
                                                    .height(16.dp)
                                                    .width(16.dp),
                                            )
                                        }
                                    }
                                },
                            ) {
                                AppListPrimary(thread.topic, emphasized = thread.unread)
                                AppListSecondary(thread.sender)
                            }
                            AppListDivider()
                        }
                    }
                }
            }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (detail.thread.flagged) {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
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
                        stringResource(R.string.message_receivers) + ": " + detail.receivers.joinToString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onMarkRead) { Text(stringResource(R.string.message_mark_read)) }
                    TextButton(onClick = onToggleFlag) {
                        Text(
                            if (detail.thread.flagged) {
                                stringResource(R.string.message_unflag)
                            } else {
                                stringResource(R.string.message_flag)
                            },
                        )
                    }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.message_delete)) }
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
                            entry.sentAt?.let {
                                Text(
                                    it.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = false
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                loadDataWithBaseURL(
                                    "https://www.lectio.dk",
                                    entry.contentHtml.orEmpty(),
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
                                entry.contentHtml.orEmpty(),
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        },
                    )
                    entry.attachments.forEach { att ->
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(att.url)))
                            },
                        ) { Text("📎 ${att.name}") }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = onReplyChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.message_reply_hint)) },
                    minLines = 2,
                    maxLines = 6,
                )
                Button(onClick = onSendReply, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.message_send))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.message_compose)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.composeSubject,
                onValueChange = { viewModel.updateCompose(subject = it) },
                label = { Text(stringResource(R.string.message_subject)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.recipientQuery,
                onValueChange = viewModel::onRecipientQuery,
                label = { Text(stringResource(R.string.message_recipients)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.selectedRecipients.isNotEmpty()) {
                Text(
                    state.selectedRecipients.joinToString { it.name },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.recipientResults.forEach { r ->
                val selected = state.selectedRecipients.any { it.id == r.id }
                Text(
                    (if (selected) "✓ " else "") + r.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleRecipient(r) }
                        .padding(8.dp),
                )
            }
            OutlinedTextField(
                value = state.composeBody,
                onValueChange = { viewModel.updateCompose(body = it) },
                label = { Text(stringResource(R.string.message_body)) },
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
            state.composeMessage?.let { Text(it.asString(), color = MaterialTheme.colorScheme.primary) }
            Button(
                onClick = {
                    viewModel.sendCompose()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.message_send))
            }
        }
    }
}
