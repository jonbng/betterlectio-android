package dk.betterlectio.android.ui.screens.homework

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dk.betterlectio.android.R
import dk.betterlectio.android.feature.homework.HomeworkItem
import dk.betterlectio.android.ui.components.AppListDivider
import dk.betterlectio.android.ui.components.AppListMeta
import dk.betterlectio.android.ui.components.AppListRow
import dk.betterlectio.android.ui.components.AppListSecondary
import dk.betterlectio.android.ui.components.DetailSection
import dk.betterlectio.android.ui.components.EmptyBox
import dk.betterlectio.android.ui.components.ErrorBox
import dk.betterlectio.android.ui.components.ListSkeleton
import dk.betterlectio.android.ui.components.SectionHeader

private object HwRoutes {
    const val LIST = "hw_list"
    const val DETAIL = "hw_detail/{id}"
    fun detail(id: String) = "hw_detail/$id"
}

@Composable
fun HomeworkScreen(
    viewModel: HomeworkViewModel = hiltViewModel(),
    scrollToTopToken: Int = 0,
) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.selected) {
        if (state.selected == null) {
            val route = navController.currentBackStackEntry?.destination?.route
            if (route?.startsWith("hw_detail") == true) {
                navController.popBackStack(HwRoutes.LIST, inclusive = false)
            }
        }
    }

    NavHost(navController = navController, startDestination = HwRoutes.LIST, modifier = Modifier.fillMaxSize()) {
        composable(HwRoutes.LIST) {
            HomeworkListPane(
                viewModel = viewModel,
                scrollToTopToken = scrollToTopToken,
                onOpen = { item ->
                    viewModel.select(item)
                    navController.navigate(HwRoutes.detail(item.id))
                },
            )
        }
        composable(
            HwRoutes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) {
            BackHandler {
                viewModel.select(null)
                navController.popBackStack()
            }
            val item = state.selected
            if (item == null) {
                ListSkeleton()
            } else {
                HomeworkDetailPane(
                    item = item,
                    onBack = {
                        viewModel.select(null)
                        navController.popBackStack()
                    },
                    onToggleDone = {
                        viewModel.toggleDone(item.id)
                        viewModel.select(null)
                        navController.popBackStack()
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
                state.items.isEmpty() -> EmptyBox(stringResource(R.string.empty_homework))
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    state.groups.forEach { group ->
                        item(key = "header-${group.label}") {
                            SectionHeader(group.label)
                        }
                        items(group.items, key = { it.id }) { item ->
                            AppListRow(
                                onClick = { onOpen(item) },
                                leading = {
                                    Checkbox(
                                        checked = item.done,
                                        onCheckedChange = { viewModel.toggleDone(item.id) },
                                    )
                                },
                            ) {
                                Text(
                                    item.activityTitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (item.done) FontWeight.Normal else FontWeight.Medium,
                                    color = if (item.done) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                                    maxLines = 2,
                                )
                                if (item.note.isNotBlank()) {
                                    AppListSecondary(item.note, maxLines = 2)
                                }
                                if (item.team.isNotBlank()) {
                                    AppListMeta(item.team)
                                }
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
private fun HomeworkDetailPane(
    item: HomeworkItem,
    onBack: () -> Unit,
    onToggleDone: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.activityTitle, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
            item.date?.let {
                Text(it.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }
            if (item.team.isNotBlank()) {
                Text(item.team, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            }
            DetailSection(stringResource(R.string.label_homework)) {
                Text(
                    item.note.ifBlank { stringResource(R.string.homework_no_note) },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item.detailHtml?.let { html ->
                DetailSection(stringResource(R.string.homework_lesson_content)) {
                    Text(
                        dk.betterlectio.android.feature.homework.HomeworkDetailLoader.plainTextFromHtml(html),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.done, onCheckedChange = { onToggleDone() })
                Text(
                    if (item.done) {
                        stringResource(R.string.homework_done)
                    } else {
                        stringResource(R.string.homework_mark_done)
                    },
                )
            }
        }
    }
}

@Composable
private fun ColumnScroll(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        content = { content() },
    )
}
