package dk.betterlectio.android.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dk.betterlectio.android.core.lectio.session.AuthState
import dk.betterlectio.android.core.lectio.session.SessionController
import dk.betterlectio.android.feature.messages.MessageRepository
import dk.betterlectio.android.ui.auth.LoginScreen
import dk.betterlectio.android.ui.components.LoadingBox
import dk.betterlectio.android.ui.screens.assignments.AssignmentsScreen
import dk.betterlectio.android.ui.screens.homework.HomeworkScreen
import dk.betterlectio.android.ui.screens.messages.MessagesScreen
import dk.betterlectio.android.ui.screens.more.MoreScreen
import dk.betterlectio.android.ui.screens.schedule.ScheduleScreen

@Composable
fun BetterLectioRoot(
    sessionController: SessionController,
) {
    val authState by sessionController.authState.collectAsStateWithLifecycle()
    AnimatedContent(
        targetState = authState,
        transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
        },
        contentKey = {
            when (it) {
                AuthState.Loading -> "loading"
                AuthState.Unauthenticated -> "login"
                is AuthState.Authenticated -> "app"
            }
        },
        label = "auth",
    ) { state ->
        when (state) {
            AuthState.Loading -> LoadingBox()
            AuthState.Unauthenticated -> LoginScreen()
            is AuthState.Authenticated -> AuthenticatedShell()
        }
    }
}

@Composable
private fun AuthenticatedShell() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val context = LocalContext.current
    val messageRepository = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            UnreadEntryPoint::class.java,
        ).messageRepository()
    }
    val unreadCount by messageRepository.unreadCount.collectAsStateWithLifecycle()

    // Same-tab reselect → bump scroll token for active route
    val scrollTokens = remember { mutableStateMapOf<String, Int>() }
    var scheduleScroll by remember { mutableIntStateOf(0) }
    var messagesScroll by remember { mutableIntStateOf(0) }
    var homeworkScroll by remember { mutableIntStateOf(0) }
    var assignmentsScroll by remember { mutableIntStateOf(0) }
    var moreScroll by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                AppDestination.bottomBarItems.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selected) {
                                when (destination) {
                                    AppDestination.Schedule -> scheduleScroll++
                                    AppDestination.Messages -> messagesScroll++
                                    AppDestination.Homework -> homeworkScroll++
                                    AppDestination.Assignments -> assignmentsScroll++
                                    AppDestination.More -> moreScroll++
                                }
                                scrollTokens[destination.route] =
                                    (scrollTokens[destination.route] ?: 0) + 1
                            } else {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            val icon = if (selected) destination.selectedIcon else destination.unselectedIcon
                            if (destination == AppDestination.Messages && unreadCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(if (unreadCount > 9) "9+" else "$unreadCount")
                                        }
                                    },
                                ) {
                                    Icon(icon, contentDescription = stringResource(destination.labelRes))
                                }
                            } else {
                                Icon(icon, contentDescription = stringResource(destination.labelRes))
                            }
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Schedule.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(180)) },
            exitTransition = { fadeOut(animationSpec = tween(140)) },
            popEnterTransition = { fadeIn(animationSpec = tween(180)) },
            popExitTransition = { fadeOut(animationSpec = tween(140)) },
        ) {
            composable(AppDestination.Schedule.route) {
                ScheduleScreen(scrollToTopToken = scheduleScroll)
            }
            composable(AppDestination.Messages.route) {
                MessagesScreen(scrollToTopToken = messagesScroll)
            }
            composable(AppDestination.Homework.route) {
                HomeworkScreen(scrollToTopToken = homeworkScroll)
            }
            composable(AppDestination.Assignments.route) {
                AssignmentsScreen(scrollToTopToken = assignmentsScroll)
            }
            composable(AppDestination.More.route) {
                MoreScreen(scrollToTopToken = moreScroll)
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UnreadEntryPoint {
    fun messageRepository(): MessageRepository
}
