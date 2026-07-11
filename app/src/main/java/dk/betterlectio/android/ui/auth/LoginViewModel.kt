package dk.betterlectio.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posthog.PostHog
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.betterlectio.android.core.lectio.auth.AuthSessionInstaller
import dk.betterlectio.android.core.model.School
import dk.betterlectio.android.core.result.AppError
import dk.betterlectio.android.core.result.AppResult
import dk.betterlectio.android.feature.auth.SchoolRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val schools: List<School> = emptyList(),
    val filtered: List<School> = emptyList(),
    val query: String = "",
    val selected: School? = null,
    val loadingSchools: Boolean = true,
    val loggingIn: Boolean = false,
    val showWebView: Boolean = false,
    val error: AppError? = null,
    /** Non-null when appswitch Intent could not open MitID (e.g. app not installed). */
    val mitIdAppSwitchError: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val authSessionInstaller: AuthSessionInstaller,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        refreshSchools()
    }

    fun refreshSchools() {
        viewModelScope.launch {
            _state.update { it.copy(loadingSchools = true, error = null) }
            when (val res = schoolRepository.loadSchools()) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        schools = res.data,
                        filtered = filter(res.data, it.query),
                        loadingSchools = false,
                    )
                }
                is AppResult.Failure -> _state.update {
                    it.copy(loadingSchools = false, error = res.error)
                }
            }
        }
    }

    fun onQuery(q: String) {
        _state.update {
            it.copy(query = q, filtered = filter(it.schools, q))
        }
    }

    fun select(school: School) {
        _state.update { it.copy(selected = school) }
    }

    fun startMitId() {
        if (_state.value.selected == null) return
        _state.update {
            it.copy(
                showWebView = true,
                error = null,
                mitIdAppSwitchError = null,
            )
        }
    }

    fun dismissWebView() {
        _state.update {
            it.copy(
                showWebView = false,
                loggingIn = false,
                mitIdAppSwitchError = null,
            )
        }
    }

    fun onMitIdAppSwitchFailed(url: String) {
        PostHog.capture(
            event = "mitid_app_switch_failed",
            properties = mapOf("url_host" to (runCatching { java.net.URI(url).host }.getOrNull() ?: "unknown")),
        )
        _state.update { it.copy(mitIdAppSwitchError = url) }
    }

    fun clearMitIdAppSwitchError() {
        _state.update { it.copy(mitIdAppSwitchError = null) }
    }

    fun onWebViewLoginSuccess(callbackUrl: String) {
        val school = _state.value.selected ?: return
        // Guard against double-fire from onPageFinished + onResume.
        if (_state.value.loggingIn) return
        viewModelScope.launch {
            _state.update { it.copy(loggingIn = true, error = null, mitIdAppSwitchError = null) }
            when (
                val res = authSessionInstaller.completeLoginFromWebView(
                    school = school,
                    callbackUrl = callbackUrl,
                )
            ) {
                is AppResult.Success -> _state.update {
                    it.copy(loggingIn = false, showWebView = false)
                }
                is AppResult.Failure -> {
                    PostHog.capture(
                        event = "login_failed",
                        properties = mapOf(
                            "login_method" to "mitid",
                            "error" to res.error.toString(),
                            "callback_host" to (
                                runCatching { java.net.URI(callbackUrl).host }.getOrNull()
                                    ?: "unknown"
                                ),
                        ),
                    )
                    _state.update {
                        it.copy(loggingIn = false, showWebView = false, error = res.error)
                    }
                }
            }
        }
    }

    fun enterDemo() {
        authSessionInstaller.enterDemo()
        // PostHog.capture("demo_entered") is called inside enterDemo()
    }

    private fun filter(schools: List<School>, q: String): List<School> {
        if (q.isBlank()) return schools
        return schools.filter { it.name.contains(q, ignoreCase = true) }
    }
}
