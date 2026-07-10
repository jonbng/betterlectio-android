package dk.betterlectio.android.ui.screens.more

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posthog.PostHog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dk.betterlectio.android.R
import dk.betterlectio.android.core.cache.SimpleCache
import dk.betterlectio.android.core.i18n.UiText
import dk.betterlectio.android.core.i18n.toUiText
import dk.betterlectio.android.core.lectio.auth.AuthSessionInstaller
import dk.betterlectio.android.core.lectio.session.SessionController
import dk.betterlectio.android.core.model.Student
import dk.betterlectio.android.core.result.AppResult
import dk.betterlectio.android.feature.absence.AbsenceCauses
import dk.betterlectio.android.feature.absence.AbsenceOverview
import dk.betterlectio.android.feature.absence.AbsenceRepository
import dk.betterlectio.android.feature.directory.DirectoryEntity
import dk.betterlectio.android.feature.directory.DirectoryEntityKind
import dk.betterlectio.android.feature.directory.DirectoryPinRepository
import dk.betterlectio.android.feature.directory.DirectoryRepository
import dk.betterlectio.android.feature.directory.RoomScheduleRepository
import dk.betterlectio.android.feature.grades.GradeRepository
import dk.betterlectio.android.feature.grades.GradeRow
import dk.betterlectio.android.feature.grades.GradeSubjectDetail
import dk.betterlectio.android.feature.plans.PlanRepository
import dk.betterlectio.android.feature.plans.StudyPlan
import dk.betterlectio.android.feature.schedule.ScheduleWeek
import dk.betterlectio.android.feature.settings.AppLanguage
import dk.betterlectio.android.feature.settings.AppearanceMode
import dk.betterlectio.android.feature.settings.CalendarStyle
import dk.betterlectio.android.feature.settings.SettingsStore
import dk.betterlectio.android.feature.studiekort.ProfilePictureUploader
import dk.betterlectio.android.feature.studiekort.StudentCard
import dk.betterlectio.android.feature.studiekort.StudiekortRepository
import dk.betterlectio.android.feature.supabase.SupabaseSubjectSync
import dk.betterlectio.android.feature.teams.ModuleStat
import dk.betterlectio.android.feature.teams.ModuleStatRepository
import dk.betterlectio.android.feature.terms.SchoolTerm
import dk.betterlectio.android.feature.terms.TermRepository
import dk.betterlectio.android.feature.updates.AppUpdateProbe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MoreDestination {
    ROOT, GRADES, ABSENCE, DIRECTORY, ROOMS, STUDIEKORT, PLANS, MODULE_STATS, TERM, SETTINGS, HELP
}

data class MoreUiState(
    val destination: MoreDestination = MoreDestination.ROOT,
    val student: Student? = null,
    val profilePhotoUrl: String? = null,
    val loading: Boolean = false,
    val grades: List<GradeRow> = emptyList(),
    val gradeDetail: GradeSubjectDetail? = null,
    val absence: AbsenceOverview? = null,
    val directory: List<DirectoryEntity> = emptyList(),
    val directoryQuery: String = "",
    val directoryKind: DirectoryEntityKind? = null,
    val directoryMembers: List<DirectoryEntity> = emptyList(),
    val directoryParent: DirectoryEntity? = null,
    val pinnedIds: Set<String> = emptySet(),
    val roomSchedule: ScheduleWeek? = null,
    val roomEntity: DirectoryEntity? = null,
    /** Live room occupancy list (in-use flags). */
    val roomsOccupancy: List<dk.betterlectio.android.feature.directory.RoomParser.RoomWithOccupancy> = emptyList(),
    val card: StudentCard? = null,
    val plans: List<StudyPlan> = emptyList(),
    val planDetail: StudyPlan? = null,
    val moduleStats: List<ModuleStat> = emptyList(),
    val terms: List<SchoolTerm> = emptyList(),
    val subjectRenameDrafts: Map<String, String> = emptyMap(),
    val updateMessage: String? = null,
    val message: UiText? = null,
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val session: SessionController,
    private val auth: AuthSessionInstaller,
    private val gradesRepo: GradeRepository,
    private val absenceRepo: AbsenceRepository,
    private val directoryRepo: DirectoryRepository,
    private val pinRepo: DirectoryPinRepository,
    private val roomScheduleRepo: RoomScheduleRepository,
    private val studiekortRepo: StudiekortRepository,
    private val plansRepo: PlanRepository,
    private val moduleStatRepo: ModuleStatRepository,
    private val termRepo: TermRepository,
    private val cache: SimpleCache,
    private val appUpdateProbe: AppUpdateProbe,
    private val profilePictureUploader: ProfilePictureUploader,
    private val subjectSync: SupabaseSubjectSync,
    val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        MoreUiState(
            student = session.currentStudent,
            pinnedIds = pinRepo.pinnedIds(),
        ),
    )
    val state: StateFlow<MoreUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val res = studiekortRepo.loadCardScraped()) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        card = res.data,
                        profilePhotoUrl = res.data.photoUrl,
                        student = res.data.student,
                    )
                }
                is AppResult.Failure -> Unit
            }
        }
    }

    val appearance = settings.appearance.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.appearance.value)
    val language = settings.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.language.value)
    val calendarStyle = settings.calendarStyle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.calendarStyle.value)
    val notifEvents = settings.notifEvents
    val notifMessages = settings.notifMessages
    val notifAssignments = settings.notifAssignments
    val subjectColors = settings.subjectColors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.subjectColors.value)
    val subjectNames = settings.subjectNames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.subjectNames.value)
    val notificationHistory = settings.notificationHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.notificationHistory.value)

    fun navigate(dest: MoreDestination) {
        _state.update {
            it.copy(
                destination = dest,
                message = null,
                gradeDetail = null,
                directoryMembers = emptyList(),
                directoryParent = null,
                roomSchedule = null,
                roomEntity = null,
                planDetail = null,
            )
        }
        when (dest) {
            MoreDestination.GRADES -> {
                PostHog.capture(event = "grades_viewed")
                loadGrades()
            }
            MoreDestination.ABSENCE -> {
                PostHog.capture(event = "absence_viewed")
                loadAbsence()
            }
            MoreDestination.DIRECTORY -> searchDirectory()
            MoreDestination.ROOMS -> loadRoomsOccupancy()
            MoreDestination.STUDIEKORT -> loadCard()
            MoreDestination.PLANS -> loadPlans()
            MoreDestination.MODULE_STATS -> loadModuleStats()
            MoreDestination.TERM -> loadTerms()
            MoreDestination.SETTINGS -> {
                val drafts = settings.editableSubjects().associateWith { key ->
                    settings.displayNameForSubject(key, key)
                }
                _state.update { it.copy(subjectRenameDrafts = drafts) }
            }
            MoreDestination.HELP -> Unit
            else -> Unit
        }
    }

    fun back() {
        val s = _state.value
        when {
            s.gradeDetail != null -> _state.update { it.copy(gradeDetail = null) }
            s.planDetail != null -> _state.update { it.copy(planDetail = null) }
            s.roomSchedule != null || s.roomEntity != null -> _state.update {
                it.copy(roomSchedule = null, roomEntity = null)
            }
            s.directoryParent != null -> _state.update {
                it.copy(directoryParent = null, directoryMembers = emptyList())
            }
            else -> _state.update { it.copy(destination = MoreDestination.ROOT) }
        }
    }

    fun logout() = auth.logout()

    fun clearCache() {
        cache.clearAll()
        _state.update { it.copy(message = UiText.Res(R.string.msg_cache_cleared)) }
    }

    private fun loadGrades() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        when (val res = gradesRepo.load(true)) {
            is AppResult.Success -> _state.update { it.copy(loading = false, grades = res.data) }
            is AppResult.Failure -> _state.update { it.copy(loading = false, message = res.error.toUiText()) }
        }
    }

    fun openGradeDetail(row: GradeRow) = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        when (val res = gradesRepo.loadSubjectDetail(row)) {
            is AppResult.Success -> _state.update { it.copy(loading = false, gradeDetail = res.data) }
            is AppResult.Failure -> _state.update { it.copy(loading = false, message = res.error.toUiText()) }
        }
    }

    private fun loadAbsence() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        when (val res = absenceRepo.loadOverview(true)) {
            is AppResult.Success -> _state.update { it.copy(loading = false, absence = res.data) }
            is AppResult.Failure -> _state.update { it.copy(loading = false, message = res.error.toUiText()) }
        }
    }

    fun updateAbsenceCause(id: String, cause: String) = viewModelScope.launch {
        when (val res = absenceRepo.updateCause(id, cause)) {
            is AppResult.Success -> {
                PostHog.capture(event = "absence_cause_updated")
                settings.appendNotificationHistory(
                    appContext.getString(R.string.msg_absence_cause_history, cause),
                )
                _state.update { it.copy(message = UiText.Res(R.string.msg_absence_cause_updated, cause)) }
                loadAbsence()
            }
            is AppResult.Failure -> _state.update { it.copy(message = res.error.toUiText()) }
        }
    }

    fun onDirectoryQuery(q: String) {
        _state.update { it.copy(directoryQuery = q) }
        searchDirectory()
    }

    fun onDirectoryKind(kind: DirectoryEntityKind?) {
        _state.update { it.copy(directoryKind = kind) }
        searchDirectory()
    }

    private fun searchDirectory() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        when (val res = directoryRepo.search(_state.value.directoryQuery, _state.value.directoryKind)) {
            is AppResult.Success -> {
                val classLabel = session.currentStudent?.classLabel
                val ranked = dk.betterlectio.android.feature.directory.DirectorySearch.rank(
                    items = res.data,
                    query = _state.value.directoryQuery,
                    pinnedIds = pinRepo.pinnedIds(),
                    classmateClassLabel = classLabel,
                )
                _state.update {
                    it.copy(
                        loading = false,
                        directory = ranked,
                        pinnedIds = pinRepo.pinnedIds(),
                    )
                }
            }
            is AppResult.Failure -> _state.update { it.copy(loading = false, message = res.error.toUiText()) }
        }
    }

    fun togglePin(entity: DirectoryEntity) {
        pinRepo.toggle(entity.id)
        val classLabel = session.currentStudent?.classLabel
        _state.update {
            it.copy(
                pinnedIds = pinRepo.pinnedIds(),
                directory = dk.betterlectio.android.feature.directory.DirectorySearch.rank(
                    items = it.directory,
                    query = it.directoryQuery,
                    pinnedIds = pinRepo.pinnedIds(),
                    classmateClassLabel = classLabel,
                ),
            )
        }
    }

    fun isPinned(id: String): Boolean = pinRepo.isPinned(id)

    fun openDirectoryMembers(entity: DirectoryEntity) = viewModelScope.launch {
        _state.update { it.copy(loading = true, directoryParent = entity) }
        when (val res = directoryRepo.loadMembers(entity)) {
            is AppResult.Success -> _state.update {
                it.copy(loading = false, directoryMembers = res.data)
            }
            is AppResult.Failure -> _state.update {
                it.copy(loading = false, message = res.error.toUiText())
            }
        }
    }

    fun openRoomSchedule(entity: DirectoryEntity) = viewModelScope.launch {
        _state.update { it.copy(loading = true, roomEntity = entity, roomSchedule = null) }
        when (val res = roomScheduleRepo.loadRoomWeek(entity)) {
            is AppResult.Success -> _state.update {
                it.copy(loading = false, roomSchedule = res.data)
            }
            is AppResult.Failure -> _state.update {
                it.copy(loading = false, message = res.error.toUiText())
            }
        }
    }

    fun openRoomFromOccupancy(room: dk.betterlectio.android.feature.directory.RoomParser.RoomWithOccupancy) {
        openRoomSchedule(
            DirectoryEntity(
                id = room.id,
                name = "${room.shortName} · ${room.name}",
                kind = DirectoryEntityKind.ROOM,
                subtitle = appContext.getString(
                    if (room.inUse) R.string.room_in_use else R.string.room_free,
                ),
            ),
        )
    }

    private fun loadRoomsOccupancy() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        when (val res = roomScheduleRepo.listRoomsWithOccupancy()) {
            is AppResult.Success -> _state.update {
                it.copy(loading = false, roomsOccupancy = res.data)
            }
            is AppResult.Failure -> _state.update {
                it.copy(loading = false, message = res.error.toUiText())
            }
        }
    }

    private fun loadCard() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        when (val res = studiekortRepo.loadCardScraped()) {
            is AppResult.Success -> _state.update {
                it.copy(
                    loading = false,
                    card = res.data,
                    profilePhotoUrl = res.data.photoUrl,
                    student = res.data.student,
                )
            }
            is AppResult.Failure -> _state.update { it.copy(loading = false, message = res.error.toUiText()) }
        }
    }

    private fun loadPlans() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        when (val res = plansRepo.load()) {
            is AppResult.Success -> _state.update { it.copy(loading = false, plans = res.data) }
            is AppResult.Failure -> _state.update { it.copy(loading = false, message = res.error.toUiText()) }
        }
    }

    fun openPlanDetail(plan: StudyPlan) = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        when (val res = plansRepo.loadDetail(plan)) {
            is AppResult.Success -> _state.update { it.copy(loading = false, planDetail = res.data) }
            is AppResult.Failure -> _state.update {
                it.copy(loading = false, message = res.error.toUiText())
            }
        }
    }

    private fun loadModuleStats() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        when (val res = moduleStatRepo.load()) {
            is AppResult.Success -> _state.update { it.copy(loading = false, moduleStats = res.data) }
            is AppResult.Failure -> _state.update { it.copy(loading = false, message = res.error.toUiText()) }
        }
    }

    private fun loadTerms() = viewModelScope.launch {
        when (val res = termRepo.loadTerms()) {
            is AppResult.Success -> _state.update { it.copy(terms = res.data) }
            is AppResult.Failure -> _state.update { it.copy(message = res.error.toUiText()) }
        }
    }

    fun selectTerm(id: String) = viewModelScope.launch {
        termRepo.selectTerm(id)
        loadTerms()
    }

    fun setAppearance(mode: AppearanceMode) = settings.setAppearance(mode)
    fun setLanguage(language: AppLanguage) = settings.setLanguage(language)
    fun setCalendarStyle(style: CalendarStyle) = settings.setCalendarStyle(style)
    fun setNotifEvents(v: Boolean) = settings.setNotifEvents(v)
    fun setNotifMessages(v: Boolean) = settings.setNotifMessages(v)
    fun setNotifAssignments(v: Boolean) = settings.setNotifAssignments(v)

    fun subjectColorOptions(): List<Long> = SettingsStore.DEFAULT_PALETTE
    fun editableSubjects(): List<String> = settings.editableSubjects()
    fun setSubjectColor(subject: String, colorArgb: Long) = settings.setSubjectColor(subject, colorArgb)
    fun colorForSubject(subject: String): Long = settings.colorForSubject(subject)
    fun displayNameForSubject(subject: String): String = settings.displayNameForSubject(subject, subject)

    fun updateSubjectRenameDraft(subject: String, name: String) {
        _state.update {
            it.copy(subjectRenameDrafts = it.subjectRenameDrafts + (subject to name))
        }
    }

    fun saveSubjectRename(subject: String) {
        val name = _state.value.subjectRenameDrafts[subject] ?: return
        settings.setSubjectName(subject, name)
        pushSubjectSync(subject)
        _state.update { it.copy(message = UiText.Res(R.string.msg_subject_name_saved)) }
    }

    fun clearNotificationHistory() {
        settings.clearNotificationHistory()
        _state.update { it.copy(message = UiText.Res(R.string.msg_notif_history_cleared)) }
    }

    fun checkForUpdates() {
        val result = appUpdateProbe.probe()
        _state.update {
            it.copy(
                updateMessage = result.message,
                message = UiText.Raw(result.message),
            )
        }
    }

    /** Full Play IAU flow when an Activity is available. */
    fun checkForUpdatesWithActivity(activity: android.app.Activity) {
        appUpdateProbe.checkAndStart(activity) { result ->
            _state.update {
                it.copy(
                    updateMessage = result.message,
                    message = UiText.Raw(result.message),
                )
            }
        }
    }

    fun appVersion(): String = appUpdateProbe.appVersionName()

    fun uploadProfilePicture(uri: android.net.Uri) = viewModelScope.launch {
        when (val res = profilePictureUploader.upload(uri)) {
            is AppResult.Success -> {
                _state.update { it.copy(message = UiText.Res(R.string.msg_profile_photo_updated)) }
                loadCard()
            }
            is AppResult.Failure -> _state.update { it.copy(message = res.error.toUiText()) }
        }
    }

    fun pullSubjectSync() = viewModelScope.launch {
        val student = session.currentStudent ?: return@launch
        if (!subjectSync.isConfigured()) {
            _state.update { it.copy(message = UiText.Res(R.string.msg_supabase_not_configured)) }
            return@launch
        }
        val mappings = subjectSync.fetchMappings(student.studentId, student.gymId.toString())
        if (mappings.isNullOrEmpty()) {
            _state.update { it.copy(message = UiText.Res(R.string.msg_no_remote_subject_mapping)) }
            return@launch
        }
        mappings.forEach { m ->
            m.displayName?.let { settings.setSubjectName(m.subjectKey, it) }
            m.colorArgb?.let { settings.setSubjectColor(m.subjectKey, it) }
        }
        _state.update { it.copy(message = UiText.Res(R.string.msg_subjects_synced, mappings.size)) }
    }

    fun pushSubjectSync(subject: String) = viewModelScope.launch {
        val student = session.currentStudent ?: return@launch
        if (!subjectSync.isConfigured()) return@launch
        subjectSync.upsertMapping(
            studentId = student.studentId,
            schoolId = student.gymId.toString(),
            mapping = dk.betterlectio.android.feature.supabase.SupabaseSubjectSync.SubjectMapping(
                subjectKey = subject,
                displayName = settings.displayNameForSubject(subject, subject),
                colorArgb = settings.colorForSubject(subject),
            ),
        )
    }

    val privacyPolicyUrl: String get() = SettingsStore.PRIVACY_POLICY_URL

    val absenceCauses get() = AbsenceCauses.all
}
