package dk.betterlectio.android.ui.screens.homework

import android.os.SystemClock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dk.betterlectio.android.core.analytics.AppAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.betterlectio.android.core.result.AppError
import dk.betterlectio.android.core.result.AppResult
import dk.betterlectio.android.core.cache.CacheFreshness
import dk.betterlectio.android.feature.homework.HomeworkDayGroup
import dk.betterlectio.android.feature.homework.HomeworkItem
import dk.betterlectio.android.feature.homework.HomeworkRepository
import dk.betterlectio.android.feature.homework.groupedByDate
import dk.betterlectio.android.feature.review.ReviewPromptCoordinator
import dk.betterlectio.android.feature.review.ReviewTrigger
import dk.betterlectio.android.feature.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

data class HomeworkUiState(
    val loading: Boolean = true,
    val items: List<HomeworkItem> = emptyList(),
    val groups: List<HomeworkDayGroup> = emptyList(),
    val selected: HomeworkItem? = null,
    val error: AppError? = null,
)

@HiltViewModel
class HomeworkViewModel @Inject constructor(
    private val repository: HomeworkRepository,
    private val settings: SettingsStore,
    private val reviewPromptCoordinator: ReviewPromptCoordinator,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeworkUiState())
    val state: StateFlow<HomeworkUiState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    val lessonMappings = settings.lessonMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.lessonMappings.value)

    fun displayTeam(team: String): String =
        settings.displayNameForSubject(team, fallback = team)

    init {
        refreshIfStale()
    }

    fun refresh(force: Boolean = false) {
        if (!force) {
            refreshIfStale()
            return
        }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val analyticsStartedAt = SystemClock.elapsedRealtime()
            _state.update { it.copy(loading = true, error = null) }
            applyResult(repository.load(force), reportFailure = true, analyticsStartedAt = analyticsStartedAt)
        }
    }

    fun onVisible() = refreshIfStale()

    private fun refreshIfStale() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val freshness = repository.cacheFreshness()
            _state.update { it.copy(loading = true, error = null) }
            if (freshness != CacheFreshness.MISSING) {
                applyResult(repository.load(forceRefresh = false), reportFailure = false)
            }
            if (freshness != CacheFreshness.FRESH) {
                _state.update { it.copy(loading = true) }
                val analyticsStartedAt = SystemClock.elapsedRealtime()
                applyResult(repository.load(forceRefresh = true), reportFailure = true, analyticsStartedAt = analyticsStartedAt)
            }
        }
    }

    private fun applyResult(
        res: AppResult<List<HomeworkItem>>,
        reportFailure: Boolean,
        analyticsStartedAt: Long? = null,
    ) {
        when (res) {
                is AppResult.Success -> {
                    analyticsStartedAt?.let { AppAnalytics.captureLoad("homework", "success", it) }
                    _state.update {
                    it.copy(
                        loading = false,
                        items = res.data,
                        groups = res.data.groupedByDate(),
                    )
                    }
                }
                is AppResult.Failure -> {
                    analyticsStartedAt?.let {
                        AppAnalytics.captureLoad(
                            "homework",
                            "failure",
                            it,
                            mapOf("error_type" to (res.error::class.simpleName ?: "unknown")),
                        )
                    }
                    if (reportFailure) reviewPromptCoordinator.reportRecentError()
                    _state.update { it.copy(loading = false, error = res.error) }
                }
        }
    }

    fun toggleDone(id: String) {
        val entry = _state.value.items.firstOrNull { it.id == id }
        val wasDone = entry?.done ?: repository.isDone(id)
        repository.toggleDone(id, entry)
        if (!wasDone) {
            reviewPromptCoordinator.maybePrompt(ReviewTrigger.HomeworkDone)
        }
        refresh()
    }

    fun select(item: HomeworkItem?) {
        if (item == null) {
            _state.update { it.copy(selected = null) }
            return
        }
        _state.update { it.copy(selected = item) }
        viewModelScope.launch {
            when (val res = repository.loadDetail(item)) {
                is AppResult.Success -> _state.update { it.copy(selected = res.data) }
                is AppResult.Failure -> Unit
            }
        }
    }
}
