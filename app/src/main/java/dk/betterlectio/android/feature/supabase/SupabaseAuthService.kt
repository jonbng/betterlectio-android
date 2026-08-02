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
    suspend fun authenticateWithLectio(
        credentials: LectioCredentials,
        expectedStudentId: String,
        gymId: Int,
    ): SupabaseSessionState {
        val client = manager.client ?: run {
            Timber.w("SupabaseAuth: client not configured — skipping")
            return SupabaseSessionState.Unavailable(SupabaseUnavailableReason.NOT_CONFIGURED)
        }

        return mutex.withLock {
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

                if (decoded.studentId != expectedStudentId) {
                    Timber.e(
                        "SupabaseAuth: identity mismatch expected=%s actual=%s requestId=%s",
                        expectedStudentId,
                        decoded.studentId,
                        decoded.requestId,
                    )
                    return@withLock SupabaseSessionState.Unavailable(
                        SupabaseUnavailableReason.IDENTITY_MISMATCH,
                    )
                }

                // The Edge Function may rotate the same Lectio cookies used by the app.
                // Persist those rotations before any later Supabase step can fail.
                if (!syncRotatedCookies(decoded, credentials, expectedStudentId)) {
                    return@withLock SupabaseSessionState.Unavailable(
                        SupabaseUnavailableReason.COOKIE_PERSISTENCE_FAILED,
                    )
                }

                Timber.i(
                    "SupabaseAuth: received magic link token requestId=%s",
                    decoded.requestId,
                )

                client.auth.verifyEmailOtp(
                    type = OtpType.Email.MAGIC_LINK,
                    tokenHash = decoded.tokenHash,
                )
                Timber.i("SupabaseAuth: authentication successful")
                if (client.auth.currentSessionOrNull() == null) {
                    SupabaseSessionState.Unavailable(
                        SupabaseUnavailableReason.AUTHENTICATION_FAILED,
                    )
                } else {
                    SupabaseSessionState.Ready
                }
            } catch (e: Exception) {
                Timber.w(e, "SupabaseAuth: authentication failed")
                SupabaseSessionState.Unavailable(
                    SupabaseUnavailableReason.AUTHENTICATION_FAILED,
                )
            }
        }
    }

    /**
     * Cold-start: if SDK has no session, re-mint via edge function using stored Lectio cookies.
     * Always completes the session gate with the real bootstrap outcome.
     */
    suspend fun ensureSessionIfNeeded(student: Student): SupabaseSessionState {
        val result = when {
            student.isDemo -> {
                SupabaseSessionState.Unavailable(SupabaseUnavailableReason.DEMO_SESSION)
            }
            manager.client == null -> {
                SupabaseSessionState.Unavailable(SupabaseUnavailableReason.NOT_CONFIGURED)
            }
            else -> {
                val client = checkNotNull(manager.client)
                // Wait for SDK to load any persisted session from storage
                runCatching { client.auth.awaitInitialization() }
                if (client.auth.currentSessionOrNull() != null) {
                    Timber.d("SupabaseAuth: existing session present")
                    SupabaseSessionState.Ready
                } else {
                    val credentials = credentialStore.loadCredentials(student.studentId)
                    if (credentials == null) {
                        SupabaseSessionState.Unavailable(
                            SupabaseUnavailableReason.MISSING_CREDENTIALS,
                        )
                    } else {
                        Timber.i("SupabaseAuth: no cached session — re-authenticating via Edge Function")
                        authenticateWithLectio(credentials, student.studentId, student.gymId)
                    }
                }
            }
        }
        manager.completeSessionBootstrap(result)
        return result
    }

    /**
     * Login path: mint session then open the gate for other remote work.
     */
    suspend fun authenticateAndMarkReady(
        credentials: LectioCredentials,
        studentId: String,
        gymId: Int,
    ): SupabaseSessionState {
        val result = authenticateWithLectio(credentials, studentId, gymId)
        manager.completeSessionBootstrap(result)
        return result
    }

    suspend fun signOutLocal() {
        val client = manager.client
        try {
            client?.auth?.signOut()
        } catch (e: Exception) {
            Timber.w(e, "SupabaseAuth: local signOut failed")
        } finally {
            manager.resetSessionReady()
        }
    }

    private fun syncRotatedCookies(
        response: EdgeFunctionResponse,
        fallbackCredentials: LectioCredentials,
        expectedStudentId: String,
    ): Boolean {
        val rotated = response.cookies ?: run {
            Timber.w("SupabaseAuth: EF response carried no rotated cookies")
            return false
        }
        val studentId = response.studentId ?: run {
            Timber.w("SupabaseAuth: no studentId in EF response")
            return false
        }
        if (studentId != expectedStudentId) {
            return false
        }
        if (rotated.autologinkey.isBlank() || rotated.sessionId.isBlank()) {
            Timber.w("SupabaseAuth: EF returned empty primary cookie — refusing to overwrite")
            return false
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
            return true
        } catch (e: Exception) {
            Timber.w(e, "SupabaseAuth: failed to write rotated cookies")
            return false
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
        @SerialName("request_id") val requestId: String? = null,
        val cookies: RotatedCookies? = null,
    )

    @Serializable
    private data class RotatedCookies(
        val autologinkey: String,
        val sessionId: String,
        val additional: Map<String, String>? = null,
    )
}
