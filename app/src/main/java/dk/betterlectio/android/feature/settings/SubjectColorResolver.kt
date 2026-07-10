package dk.betterlectio.android.feature.settings

/**
 * Pure subject → ARGB resolution used by [SettingsStore] and schedule accents.
 */
object SubjectColorResolver {
    fun resolve(
        subjectKey: String,
        custom: Map<String, Long>,
        palette: List<Long> = SettingsStore.DEFAULT_PALETTE,
    ): Long {
        val key = subjectKey.trim()
        if (key.isEmpty()) return palette.first()
        return custom[key] ?: palette[kotlin.math.abs(key.hashCode()) % palette.size]
    }
}
