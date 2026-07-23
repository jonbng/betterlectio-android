package dk.betterlectio.android.feature.supabase

import dk.betterlectio.android.feature.directory.StudentProfile
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read rich student profile fields from Supabase `students`.
 * RLS school-scopes reads; missing rows / failures return null.
 */
@Singleton
class SupabaseStudentProfileService @Inject constructor(
    private val manager: SupabaseManager,
) {
    suspend fun getStudent(studentId: String): StudentProfile? {
        val id = studentId.trim()
        if (id.isEmpty()) return null
        val client = manager.client ?: return null
        return try {
            manager.awaitSessionReady()
            client.from("students")
                .select(Columns.list(*PROFILE_COLUMNS)) {
                    filter {
                        eq("id", id)
                    }
                }
                .decodeList<StudentProfileRow>()
                .firstOrNull()
                ?.toProfile()
        } catch (e: Exception) {
            Timber.w(e, "student profile fetch failed for id=%s", id)
            null
        }
    }

    private companion object {
        val PROFILE_COLUMNS = arrayOf(
            "id",
            "name",
            "description",
            "instagram",
            "birthdate",
            "show_birthday",
            "custom_pfp_url",
            "lectio_pfp_url",
            "class_name",
            "last_seen_at",
            "extension_installed_at",
            "extension_uninstalled_at",
            "app_installed_at",
        )
    }
}

@Serializable
private data class StudentProfileRow(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    val instagram: String? = null,
    val birthdate: String? = null,
    @SerialName("show_birthday") val showBirthday: Boolean = false,
    @SerialName("custom_pfp_url") val customPfpUrl: String? = null,
    @SerialName("lectio_pfp_url") val lectioPfpUrl: String? = null,
    @SerialName("class_name") val className: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("extension_installed_at") val extensionInstalledAt: String? = null,
    @SerialName("extension_uninstalled_at") val extensionUninstalledAt: String? = null,
    @SerialName("app_installed_at") val appInstalledAt: String? = null,
) {
    fun toProfile() = StudentProfile(
        id = id,
        name = name,
        description = description,
        instagram = instagram,
        birthdate = birthdate,
        showBirthday = showBirthday,
        customPfpUrl = customPfpUrl,
        lectioPfpUrl = lectioPfpUrl,
        className = className,
        lastSeenAt = lastSeenAt,
        extensionInstalledAt = extensionInstalledAt,
        extensionUninstalledAt = extensionUninstalledAt,
        appInstalledAt = appInstalledAt,
    )
}
