package dk.betterlectio.android.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.betterlectio.android.BuildConfig
import dk.betterlectio.android.core.lectio.auth.AuthSessionInstaller
import dk.betterlectio.android.core.lectio.model.LectioCredentials
import dk.betterlectio.android.core.model.School
import dk.betterlectio.android.core.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Serializable
internal data class AdminSessionSummary(
    val studentId: String,
    val schoolId: Int,
    val studentName: String,
    val className: String? = null,
    val schoolName: String,
    val status: String,
)

@Serializable
private data class AdminSessionList(val sessions: List<AdminSessionSummary>)

@Serializable
private data class AdminCookie(val name: String, val value: String)

@Serializable
private data class AdminSessionCredential(
    val studentId: String,
    val schoolId: Int,
    val studentName: String,
    val className: String? = null,
    val schoolName: String,
    val cookies: List<AdminCookie>,
)

@Serializable
private data class AdminSessionRequest(
    val studentId: String,
    val schoolId: Int,
    val platform: String = "admin-android",
)

internal data class AdminLoginState(
    val sessions: List<AdminSessionSummary> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val importingId: String? = null,
    val error: String? = null,
) {
    val filtered: List<AdminSessionSummary>
        get() {
            val needle = query.trim()
            if (needle.isEmpty()) return sessions
            return sessions.filter {
                it.studentName.contains(needle, ignoreCase = true) ||
                    it.className.orEmpty().contains(needle, ignoreCase = true) ||
                    it.schoolName.contains(needle, ignoreCase = true)
            }
        }
}

@HiltViewModel
internal class AdminLoginViewModel @Inject constructor(
    private val installer: AuthSessionInstaller,
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val _state = MutableStateFlow(AdminLoginState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { fetchSessions() }
                .onSuccess { sessions -> _state.update { it.copy(sessions = sessions, loading = false) } }
                .onFailure { _state.update { state -> state.copy(loading = false, error = safeMessage(it)) } }
        }
    }

    fun login(session: AdminSessionSummary) {
        if (_state.value.importingId != null) return
        viewModelScope.launch {
            _state.update { it.copy(importingId = session.studentId, error = null) }
            try {
                val credential = fetchCredential(session)
                require(
                    credential.studentId == session.studentId &&
                        credential.schoolId == session.schoolId,
                ) { "The admin server returned a different student session." }
                require(credential.cookies.all { it.name.isNotBlank() }) {
                    "The retained session contains an invalid cookie."
                }
                val cookieMap = credential.cookies.associate { it.name to it.value }
                require(cookieMap.size == credential.cookies.size) {
                    "The retained session contains duplicate cookies."
                }
                val autologin = cookieMap[LectioCredentials.COOKIE_AUTOLOGIN].orEmpty()
                require(autologin.isNotBlank()) { "The retained session has no autologin cookie." }
                val credentials = LectioCredentials(
                    autologinkey = autologin,
                    sessionId = cookieMap[LectioCredentials.COOKIE_SESSION_ID].orEmpty(),
                    additionalCookies = cookieMap.filterKeys { it !in LectioCredentials.PRIMARY_COOKIE_NAMES },
                ).seededIsLoggedIn()
                when (installer.completeLogin(
                    credentials = credentials,
                    school = School(id = session.schoolId, name = session.schoolName),
                    expectedStudentId = session.studentId,
                    authPlatform = "admin-android",
                )) {
                    is AppResult.Success -> Unit
                    is AppResult.Failure -> error("Lectio rejected or mismatched the retained session.")
                }
            } catch (error: Exception) {
                _state.update { it.copy(error = safeMessage(error)) }
            } finally {
                _state.update { it.copy(importingId = null) }
            }
        }
    }

    private suspend fun fetchSessions(): List<AdminSessionSummary> = withContext(Dispatchers.IO) {
        val response = client.newCall(requestBuilder().get().build()).execute()
        response.use {
            check(it.isSuccessful) { "Admin server returned ${it.code}." }
            json.decodeFromString<AdminSessionList>(it.body.string()).sessions
        }
    }

    private suspend fun fetchCredential(session: AdminSessionSummary): AdminSessionCredential =
        withContext(Dispatchers.IO) {
            val payload = json.encodeToString(
                AdminSessionRequest(
                    studentId = session.studentId,
                    schoolId = session.schoolId,
                ),
            )
            val request = requestBuilder()
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use {
                check(it.isSuccessful) { "Admin server returned ${it.code}." }
                json.decodeFromString<AdminSessionCredential>(it.body.string())
            }
        }

    private fun requestBuilder(): Request.Builder = Request.Builder()
        .url(BuildConfig.ADMIN_MOBILE_API_ORIGIN + "/api/mobile/lectio-sessions")
        .header("Authorization", "Bearer ${BuildConfig.ADMIN_MOBILE_TOKEN}")
        .header("Cache-Control", "no-store")
        .cacheControl(CacheControl.FORCE_NETWORK)

    private fun safeMessage(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: "Could not load retained sessions."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariantLoginScreen() {
    val viewModel: AdminLoginViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin sessions") },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Choose a retained student session to install it on this device.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search students") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null && state.sessions.isEmpty() -> {
                    AdminEmptyState(checkNotNull(state.error), viewModel::refresh)
                }
                state.filtered.isEmpty() -> AdminEmptyState("No retained sessions match your search.", viewModel::refresh)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    state.error?.let { message ->
                        item {
                            Text(
                                message,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    items(state.filtered, key = { "${it.studentId}:${it.schoolId}" }) { session ->
                        Surface(
                            onClick = { viewModel.login(session) },
                            enabled = state.importingId == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(Icons.Outlined.Person, contentDescription = null)
                                Column(Modifier.weight(1f)) {
                                    Text(session.studentName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        listOfNotNull(session.className, session.schoolName).joinToString(" · "),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (state.importingId == session.studentId) {
                                    CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        session.status,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = when (session.status) {
                                            "healthy" -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.error
                                        },
                                    )
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminEmptyState(message: String, retry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.TextButton(onClick = retry) { Text("Try again") }
        }
    }
}
