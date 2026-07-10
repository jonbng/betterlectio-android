package dk.betterlectio.android.feature.supabase

import dk.betterlectio.android.core.lectio.model.LectioCredentials
import dk.betterlectio.android.core.lectio.session.CredentialStore
import dk.betterlectio.android.core.model.Student
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lectio cookies → Supabase session via Edge Function `token-for-auth`
 * (iOS: `SupabaseAuthService`).
 */
@Singleton
class SupabaseAuthService @Inject constructor(
    private val manager: SupabaseManager,
    private val credentialStore: CredentialStore,
) {
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Best-effort: invoke edge function, verify magic-link OTP, sync rotated cookies.
     * Never throws into Lectio auth paths.
     */
    suspend fun authenticateWithLectio(credentials: LectioCredentials, gymId: Int) {
        val client = manager.client ?: run {
            Timber.w("SupabaseAuth: client not configured — skipping")
            return
        }

        mutex.withLock {
            Timber.i("SupabaseAuth: starting authentication via Edge Function")
            try {
                val response = client.functions.invoke("token-for-auth") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            EdgeFunctionRequest.serializer(),
                            EdgeFunctionRequest(
                                autologinkey = credentials.autologinkey,
                                sessionId = credentials.sessionId,
                                gymId = gymId.toString(),
                            ),
                        ),
                    )
                }
                val body = response.bodyAsText()
                val decoded = json.decodeFromString(EdgeFunctionResponse.serializer(), body)

                Timber.i("SupabaseAuth: received magic link token for %s", decoded.email)

                client.auth.verifyEmailOtp(
                    type = OtpType.Email.MAGIC_LINK,
                    tokenHash = decoded.tokenHash,
                )
                Timber.i("SupabaseAuth: authentication successful")

                syncRotatedCookies(decoded, credentials)
            } catch (e: Exception) {
                Timber.w(e, "SupabaseAuth: authentication failed")
            }
        }
    }

    /**
     * Cold-start: if SDK has no session, re-mint via edge function using stored Lectio cookies.
     * Always ends by marking the session gate ready (even on failure / skip).
     */
    suspend fun ensureSessionIfNeeded(student: Student) {
        try {
            if (student.isDemo) return
            val client = manager.client ?: return
            // Wait for SDK to load any persisted session from storage
            runCatching { client.auth.awaitInitialization() }
            if (client.auth.currentSessionOrNull() != null) {
                Timber.d("SupabaseAuth: existing session present")
                return
            }

            val credentials = credentialStore.loadCredentials(student.studentId) ?: return
            Timber.i("SupabaseAuth: no cached session — re-authenticating via Edge Function")
            authenticateWithLectio(credentials, student.gymId)
        } finally {
            manager.markSessionReady()
        }
    }

    /**
     * Login path: mint session then open the gate for other remote work.
     */
    suspend fun authenticateAndMarkReady(credentials: LectioCredentials, gymId: Int) {
        try {
            authenticateWithLectio(credentials, gymId)
        } finally {
            manager.markSessionReady()
        }
    }

    suspend fun signOutLocal() {
        val client = manager.client ?: return
        try {
            client.auth.signOut()
        } catch (e: Exception) {
            Timber.w(e, "SupabaseAuth: local signOut failed")
        } finally {
            manager.resetSessionReady()
        }
    }

    private fun syncRotatedCookies(
        response: EdgeFunctionResponse,
        fallbackCredentials: LectioCredentials,
    ) {
        val rotated = response.cookies ?: run {
            Timber.i("SupabaseAuth: EF response carried no rotated cookies — skipping credential sync")
            return
        }
        val studentId = response.studentId ?: run {
            Timber.i("SupabaseAuth: no studentId in EF response — skipping credential sync")
            return
        }
        if (rotated.autologinkey.isBlank() || rotated.sessionId.isBlank()) {
            Timber.w("SupabaseAuth: EF returned empty primary cookie — refusing to overwrite")
            return
        }

        val additional = (rotated.additional ?: emptyMap()).toMutableMap()
        if (!additional.containsKey(LectioCredentials.COOKIE_IS_LOGGED_IN)) {
            additional[LectioCredentials.COOKIE_IS_LOGGED_IN] = "Y"
        }

        val updated = LectioCredentials(
            autologinkey = rotated.autologinkey,
            sessionId = rotated.sessionId,
            autologinkeyExpiresAt = fallbackCredentials.autologinkeyExpiresAt,
            sessionIdExpiresAt = fallbackCredentials.sessionIdExpiresAt,
            additionalCookies = additional,
        )
        try {
            credentialStore.updateCredentials(updated, studentId)
            Timber.i("SupabaseAuth: credentials synced with EF-rotated cookies for student %s", studentId)
        } catch (e: Exception) {
            Timber.w(e, "SupabaseAuth: failed to write rotated cookies")
        }
    }

    @Serializable
    private data class EdgeFunctionRequest(
        val autologinkey: String,
        val sessionId: String,
        val gymId: String,
    )

    @Serializable
    private data class EdgeFunctionResponse(
        @SerialName("token_hash") val tokenHash: String,
        val email: String,
        val studentId: String? = null,
        val cookies: RotatedCookies? = null,
    )

    @Serializable
    private data class RotatedCookies(
        val autologinkey: String,
        val sessionId: String,
        val additional: Map<String, String>? = null,
    )
}
