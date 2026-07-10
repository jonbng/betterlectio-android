package dk.betterlectio.android.ui.screens.homework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.betterlectio.android.core.result.AppError
import dk.betterlectio.android.core.result.AppResult
import dk.betterlectio.android.feature.homework.HomeworkDayGroup
import dk.betterlectio.android.feature.homework.HomeworkItem
import dk.betterlectio.android.feature.homework.HomeworkRepository
import dk.betterlectio.android.feature.homework.groupedByDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
) : ViewModel() {
    private val _state = MutableStateFlow(HomeworkUiState())
    val state: StateFlow<HomeworkUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val res = repository.load(force)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        items = res.data,
                        groups = res.data.groupedByDate(),
                    )
                }
                is AppResult.Failure -> _state.update { it.copy(loading = false, error = res.error) }
            }
        }
    }

    fun toggleDone(id: String) {
        val entry = _state.value.items.firstOrNull { it.id == id }
        repository.toggleDone(id, entry)
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
