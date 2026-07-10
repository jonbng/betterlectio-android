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
    val showPasswordForm: Boolean = false,
    val username: String = "",
    val password: String = "",
    val error: AppError? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val authSessionInstaller: AuthSessionInstaller,
    private val passwordLogin: dk.betterlectio.android.feature.auth.PasswordLoginRepository,
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
        _state.update { it.copy(showWebView = true, error = null) }
    }

    fun dismissWebView() {
        _state.update { it.copy(showWebView = false, loggingIn = false) }
    }

    fun onWebViewLoginSuccess() {
        val school = _state.value.selected ?: return
        viewModelScope.launch {
            _state.update { it.copy(loggingIn = true, error = null) }
            when (val res = authSessionInstaller.completeLoginFromWebView(school)) {
                is AppResult.Success -> _state.update {
                    it.copy(loggingIn = false, showWebView = false)
                }
                is AppResult.Failure -> {
                    PostHog.capture(
                        event = "login_failed",
                        properties = mapOf("login_method" to "mitid", "error" to res.error.toString()),
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

    fun togglePasswordForm() {
        _state.update { it.copy(showPasswordForm = !it.showPasswordForm, error = null) }
    }

    fun onUsername(v: String) {
        _state.update { it.copy(username = v) }
    }

    fun onPassword(v: String) {
        _state.update { it.copy(password = v) }
    }

    fun loginWithPassword() {
        val school = _state.value.selected ?: return
        val user = _state.value.username
        val pass = _state.value.password
        viewModelScope.launch {
            _state.update { it.copy(loggingIn = true, error = null) }
            when (val res = passwordLogin.login(school, user, pass)) {
                is AppResult.Success -> {
                    PostHog.capture(
                        event = "login_with_password_completed",
                        properties = mapOf("login_method" to "password"),
                    )
                    _state.update { it.copy(loggingIn = false) }
                }
                is AppResult.Failure -> {
                    PostHog.capture(
                        event = "login_failed",
                        properties = mapOf("login_method" to "password", "error" to res.error.toString()),
                    )
                    _state.update {
                        it.copy(loggingIn = false, error = res.error)
                    }
                }
            }
        }
    }

    private fun filter(schools: List<School>, q: String): List<School> {
        if (q.isBlank()) return schools
        return schools.filter { it.name.contains(q, ignoreCase = true) }
    }
}
