package dk.betterlectio.android.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.betterlectio.android.R
import dk.betterlectio.android.core.model.School
import dk.betterlectio.android.ui.components.AppListDivider
import dk.betterlectio.android.ui.components.AppListPrimary
import dk.betterlectio.android.ui.components.AppListRow
import dk.betterlectio.android.ui.components.AppListSecondary
import dk.betterlectio.android.ui.components.EmptyBox
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Brand moment
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 32.dp, bottom = 16.dp),
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.login_search_school)) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                ),
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                thickness = 0.5.dp,
            )

            if (state.loadingSchools) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.filtered.isEmpty()) {
                EmptyBox(
                    text = stringResource(R.string.empty_login_schools),
                    description = stringResource(R.string.empty_login_schools_hint),
                    icon = Icons.Outlined.School,
                    actionLabel = if (state.query.isNotBlank()) {
                        stringResource(R.string.cd_clear_search)
                    } else {
                        null
                    },
                    onAction = if (state.query.isNotBlank()) {
                        { viewModel.onQuery("") }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(state.filtered, key = { it.id }) { school ->
                        val selected = state.selected?.id == school.id
                        Surface(
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            AppListRow(
                                onClick = { viewModel.select(school) },
                                leading = {
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                                },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            school.name.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                },
                                trailing = {
                                    if (selected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                            ) {
                                AppListPrimary(school.name, emphasized = selected)
                                if (school.isDemo) {
                                    AppListSecondary(stringResource(R.string.login_demo_badge))
                                }
                            }
                        }
                        AppListDivider()
                    }
                }
            }

            // Sticky actions
            Surface(
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )

                    state.error?.let {
                        Text(
                            text = it.toString(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    state.selected?.let { school ->
                        Text(
                            school.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Button(
                        onClick = viewModel::startMitId,
                        enabled = state.selected != null && !state.loggingIn,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.loggingIn) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(stringResource(R.string.login_mitid))
                    }

                    TextButton(
                        onClick = viewModel::enterDemo,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.login_demo))
                    }
                }
            }
        }
    }

    if (state.showWebView && state.selected != null) {
        MitIdLoginDialog(
            school = state.selected!!,
            loggingIn = state.loggingIn,
            appSwitchError = state.mitIdAppSwitchError,
            onDismiss = viewModel::dismissWebView,
            onLoginComplete = { callbackUrl -> viewModel.onWebViewLoginSuccess(callbackUrl) },
            onAppSwitchFailed = viewModel::onMitIdAppSwitchFailed,
            onClearAppSwitchError = viewModel::clearMitIdAppSwitchError,
        )
    }
}

/**
 * Full-screen MitID session — not a flingable bottom sheet.
 * Dismiss only via explicit cancel / system back (same idea as Flutter UniLoginScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MitIdLoginDialog(
    school: School,
    loggingIn: Boolean,
    appSwitchError: String?,
    onDismiss: () -> Unit,
    onLoginComplete: (callbackUrl: String) -> Unit,
    onAppSwitchFailed: (String) -> Unit,
    onClearAppSwitchError: () -> Unit,
) {
    // Block accidental back during cookie install after success callback.
    BackHandler(enabled = !loggingIn) { onDismiss() }
    BackHandler(enabled = loggingIn) { /* consume */ }

    Dialog(
        onDismissRequest = {
            if (!loggingIn) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !loggingIn,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                stringResource(R.string.login_mitid),
                                maxLines = 1,
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                enabled = !loggingIn,
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.login_mitid_cancel),
                                )
                            }
                        },
                        actions = {
                            if (loggingIn) {
                                CircularProgressIndicator(
                                    Modifier
                                        .padding(end = 16.dp)
                                        .size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    if (appSwitchError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    stringResource(R.string.login_mitid_app_missing),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                TextButton(onClick = onClearAppSwitchError) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                    }

                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        MitIdWebView(
                            school = school,
                            onLoginComplete = onLoginComplete,
                            onExternalAppLaunchFailed = onAppSwitchFailed,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (loggingIn) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}
