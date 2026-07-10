package dk.betterlectio.android.feature.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dk.betterlectio.android.core.i18n.AppLocale
import dk.betterlectio.android.core.model.Student
import dk.betterlectio.android.feature.supabase.SupabaseSubjectMapping
import dk.betterlectio.android.feature.supabase.SupabaseSubjectService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class AppearanceMode { SYSTEM, LIGHT, DARK }
enum class CalendarStyle { PROFESSIONAL, STANDARD }
enum class AppLanguage { SYSTEM, DANISH, ENGLISH }

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
    private val supabaseSubjects: SupabaseSubjectService,
) {
    private val prefs = context.getSharedPreferences("bl_settings", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** mappingId by canonical subject key (from last remote pull). */
    private val remoteMappingIds = mutableMapOf<String, String>()
    private var syncStudentId: String? = null
    private var syncSchoolId: String? = null

    private val _appearance = MutableStateFlow(loadAppearance())
    val appearance: StateFlow<AppearanceMode> = _appearance.asStateFlow()

    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _calendarStyle = MutableStateFlow(loadCalendarStyle())
    val calendarStyle: StateFlow<CalendarStyle> = _calendarStyle.asStateFlow()

    private val _notifEvents = MutableStateFlow(prefs.getBoolean("notif_events", true))
    val notifEvents: StateFlow<Boolean> = _notifEvents.asStateFlow()

    private val _notifMessages = MutableStateFlow(prefs.getBoolean("notif_messages", true))
    val notifMessages: StateFlow<Boolean> = _notifMessages.asStateFlow()

    private val _notifAssignments = MutableStateFlow(prefs.getBoolean("notif_assignments", true))
    val notifAssignments: StateFlow<Boolean> = _notifAssignments.asStateFlow()

    private val _subjectColors = MutableStateFlow(loadSubjectColors())
    val subjectColors: StateFlow<Map<String, Long>> = _subjectColors.asStateFlow()

    private val _subjectNames = MutableStateFlow(loadSubjectNames())
    val subjectNames: StateFlow<Map<String, String>> = _subjectNames.asStateFlow()

    private val _notificationHistory = MutableStateFlow(loadNotificationHistory())
    val notificationHistory: StateFlow<List<String>> = _notificationHistory.asStateFlow()

    fun setAppearance(mode: AppearanceMode) {
        prefs.edit { putString("appearance", mode.name) }
        _appearance.value = mode
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit { putString("language", language.name) }
        _language.value = language
        AppLocale.apply(language)
    }

    /** Apply stored language without rewriting prefs (call once at process start). */
    fun applyStoredLanguage() {
        AppLocale.apply(_language.value)
    }

    fun setCalendarStyle(style: CalendarStyle) {
        prefs.edit { putString("calendar_style", style.name) }
        _calendarStyle.value = style
    }

    fun setNotifEvents(v: Boolean) {
        prefs.edit { putBoolean("notif_events", v) }
        _notifEvents.value = v
    }

    fun setNotifMessages(v: Boolean) {
        prefs.edit { putBoolean("notif_messages", v) }
        _notifMessages.value = v
    }

    fun setNotifAssignments(v: Boolean) {
        prefs.edit { putBoolean("notif_assignments", v) }
        _notifAssignments.value = v
    }

    fun setSubjectColor(subject: String, colorArgb: Long) {
        val map = _subjectColors.value.toMutableMap()
        map[subject] = colorArgb
        prefs.edit { putString("subject_colors", map.entries.joinToString(";") { "${it.key}=${it.value}" }) }
        _subjectColors.value = map
        pushSubjectOverride(subject)
    }

    fun setSubjectName(subjectKey: String, displayName: String) {
        val map = _subjectNames.value.toMutableMap()
        val name = displayName.trim()
        if (name.isEmpty()) map.remove(subjectKey) else map[subjectKey] = name
        prefs.edit {
            putString(
                "subject_names",
                map.entries.joinToString(";") { "${it.key.replace(';', ' ')}=${it.value.replace(';', ' ')}" },
            )
        }
        _subjectNames.value = map
        pushSubjectOverride(subjectKey)
    }

    /**
     * Pull remote lesson mappings and apply display name / hue locally
     * (iOS: SettingsStore.syncWithSupabase).
     */
    fun syncSubjectsFromSupabase(student: Student) {
        if (student.isDemo) return
        syncStudentId = student.studentId
        syncSchoolId = student.gymId.toString()
        scope.launch {
            val mappings = supabaseSubjects.fetchMappings(student.studentId, student.gymId.toString())
            if (mappings.isEmpty()) return@launch
            applyRemoteMappings(mappings)
            Timber.i("SettingsStore: synced %d lesson mappings from Supabase", mappings.size)
        }
    }

    private fun applyRemoteMappings(mappings: List<SupabaseSubjectMapping>) {
        val colors = _subjectColors.value.toMutableMap()
        val names = _subjectNames.value.toMutableMap()
        remoteMappingIds.clear()
        for (m in mappings) {
            if (m.deletedAt != null) continue
            remoteMappingIds[m.canonicalKey] = m.mappingId
            // Also index by default/display names for local subject keys
            remoteMappingIds[m.defaultName] = m.mappingId
            remoteMappingIds[m.displayName] = m.mappingId
            names[m.canonicalKey] = m.displayName
            colors[m.canonicalKey] = SupabaseSubjectService.hueToArgb(m.displayColorHue)
            if (m.displayName != m.canonicalKey) {
                names[m.displayName] = m.displayName
                colors[m.displayName] = SupabaseSubjectService.hueToArgb(m.displayColorHue)
            }
        }
        prefs.edit {
            putString("subject_colors", colors.entries.joinToString(";") { "${it.key}=${it.value}" })
            putString(
                "subject_names",
                names.entries.joinToString(";") {
                    "${it.key.replace(';', ' ')}=${it.value.replace(';', ' ')}"
                },
            )
        }
        _subjectColors.value = colors
        _subjectNames.value = names
    }

    private fun pushSubjectOverride(subjectKey: String) {
        val studentId = syncStudentId ?: return
        val schoolId = syncSchoolId ?: return
        val mappingId = remoteMappingIds[subjectKey]
            ?: remoteMappingIds[subjectKey.trim().lowercase()]
            ?: return
        val displayName = _subjectNames.value[subjectKey]
        val color = _subjectColors.value[subjectKey]
        val hue = color?.let { SupabaseSubjectService.argbToHue(it) }
        scope.launch {
            supabaseSubjects.upsertMappingOverride(
                studentId = studentId,
                schoolId = schoolId,
                mappingId = mappingId,
                displayName = displayName,
                colorHue = hue,
                icon = null,
            )
        }
    }

    fun displayNameForSubject(subjectKey: String, fallback: String = subjectKey): String {
        val key = subjectKey.trim()
        return _subjectNames.value[key]?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun colorForSubject(subjectKey: String): Long =
        SubjectColorResolver.resolve(subjectKey, _subjectColors.value, DEFAULT_PALETTE)

    /** Subjects shown in settings when the user has not customized yet. */
    fun editableSubjects(extra: Collection<String> = emptyList()): List<String> {
        val fromStore = _subjectColors.value.keys + _subjectNames.value.keys
        val defaults = listOf("Matematik", "Dansk", "Engelsk", "Fysik", "Historie", "Ma A", "Da A", "Fy B")
        return (fromStore + defaults + extra)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    fun appendNotificationHistory(entry: String) {
        val next = (listOf("${System.currentTimeMillis()}|$entry") + _notificationHistory.value).take(50)
        prefs.edit { putString("notif_history", next.joinToString("\n")) }
        _notificationHistory.value = next
    }

    fun clearNotificationHistory() {
        prefs.edit { remove("notif_history") }
        _notificationHistory.value = emptyList()
    }

    companion object {
        const val PRIVACY_POLICY_URL = "https://betterlectio.dk/privatlivspolitik"

        /** ARGB palette for subject chips / schedule accents. */
        val DEFAULT_PALETTE = listOf(
            0xFF3362E1L, // brand blue
            0xFF0B8043L, // green
            0xFFD50000L, // red
            0xFFF4511EL, // orange
            0xFF8E24AAL, // purple
            0xFF039BE5L, // light blue
            0xFF33B679L, // teal
            0xFFE67C73L, // coral
        )
    }

    private fun loadAppearance(): AppearanceMode =
        runCatching { AppearanceMode.valueOf(prefs.getString("appearance", null) ?: "SYSTEM") }
            .getOrDefault(AppearanceMode.SYSTEM)

    private fun loadLanguage(): AppLanguage =
        runCatching { AppLanguage.valueOf(prefs.getString("language", null) ?: "SYSTEM") }
            .getOrDefault(AppLanguage.SYSTEM)

    private fun loadCalendarStyle(): CalendarStyle =
        runCatching { CalendarStyle.valueOf(prefs.getString("calendar_style", null) ?: "PROFESSIONAL") }
            .getOrDefault(CalendarStyle.PROFESSIONAL)

    private fun loadSubjectColors(): Map<String, Long> {
        val raw = prefs.getString("subject_colors", null) ?: return emptyMap()
        return raw.split(';').mapNotNull {
            val (k, v) = it.split('=').takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            k to (v.toLongOrNull() ?: return@mapNotNull null)
        }.toMap()
    }

    private fun loadSubjectNames(): Map<String, String> {
        val raw = prefs.getString("subject_names", null) ?: return emptyMap()
        return raw.split(';').mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size != 2) null else parts[0] to parts[1]
        }.toMap()
    }

    private fun loadNotificationHistory(): List<String> {
        val raw = prefs.getString("notif_history", null) ?: return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }
}
