package dk.betterlectio.android.core.lectio.auth

import com.posthog.PostHog
import dk.betterlectio.android.core.lectio.http.LectioHttpEngine
import dk.betterlectio.android.core.lectio.model.FetchPriority
import dk.betterlectio.android.core.lectio.model.LectioCredentials
import dk.betterlectio.android.core.lectio.model.LectioError
import dk.betterlectio.android.core.lectio.model.LectioRequest
import dk.betterlectio.android.core.lectio.scrape.LectioUrls
import dk.betterlectio.android.core.lectio.scrape.StudentIdentityParser
import dk.betterlectio.android.core.lectio.session.CredentialStore
import dk.betterlectio.android.core.lectio.session.SessionController
import dk.betterlectio.android.core.model.School
import dk.betterlectio.android.core.model.Student
import dk.betterlectio.android.core.result.AppResult
import dk.betterlectio.android.feature.directory.DirectorySyncService
import dk.betterlectio.android.feature.messages.MessageListPrefetcher
import dk.betterlectio.android.feature.settings.SettingsStore
import dk.betterlectio.android.feature.supabase.SupabaseAuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Completes MitID/WebView auth: validate cookies against Lectio, persist, install session.
 * iOS parity: AuthenticationService.completeAuthentication
 */
@Singleton
class AuthSessionInstaller @Inject constructor(
    private val engine: LectioHttpEngine,
    private val credentialStore: CredentialStore,
    private val sessionController: SessionController,
    private val webViewCookieExtractor: WebViewCookieExtractor,
    private val supabaseAuth: SupabaseAuthService,
    private val settingsStore: SettingsStore,
    private val directorySync: DirectorySyncService,
    private val messagePrefetcher: MessageListPrefetcher,
) {
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Use after WebView reached forside/unilogin callback — reads cookies from CookieManager.
     */
    suspend fun completeLoginFromWebView(school: School): AppResult<Student> {
        return when (val extracted = webViewCookieExtractor.extractFromCookieManager()) {
            is AppResult.Failure -> extracted
            is AppResult.Success -> completeLogin(extracted.data, school)
        }
    }

    /**
     * Validate [credentials], parse student identity, persist, install session.
     */
    suspend fun completeLogin(
        credentials: LectioCredentials,
        school: School,
    ): AppResult<Student> {
        val seeded = credentials.seededIsLoggedIn()
        val probeUrl = LectioUrls.forsideUrl(school.id)

        return try {
            val first = engine.execute(
                LectioRequest(
                    url = probeUrl,
                    method = "GET",
                    priority = FetchPriority.Important,
                    studentId = null,
                ),
                seeded,
            )

            var identity = StudentIdentityParser.parse(first.response.body)
            var latestCreds = first.credentials

            if (identity.personId.isNullOrBlank()) {
                val skema = engine.execute(
                    LectioRequest(
                        url = LectioUrls.buildUrl(school.id, "SkemaNy.aspx"),
                        method = "GET",
                        priority = FetchPriority.Important,
                        studentId = null,
                    ),
                    latestCreds,
                )
                latestCreds = skema.credentials
                identity = StudentIdentityParser.parse(skema.response.body)
            }

            val personId = identity.personId
                ?: return AppResult.Failure(
                    LectioError.Parse("Could not parse student id from Lectio").toAppError(),
                )

            val student = Student(
                studentId = personId,
                gymId = school.id,
                name = identity.name,
                pictureId = identity.pictureId,
                classLabel = null,
                schoolName = school.name,
                isDemo = false,
            )

            // Persist under real student id, then one more request so rotations bind to that id.
            credentialStore.saveCredentials(latestCreds, personId)
            val confirm = engine.execute(
                LectioRequest(
                    url = probeUrl,
                    method = "GET",
                    priority = FetchPriority.Important,
                    studentId = personId,
                ),
                latestCreds,
            )
            val finalCreds = confirm.credentials
            credentialStore.saveCredentials(finalCreds, personId)
            sessionController.installSession(student, finalCreds)

            PostHog.identify(
                distinctId = personId,
                userProperties = mapOf(
                    "gym_id" to school.id,
                    "school_name" to school.name,
                ),
            )
            PostHog.capture(
                event = "login_completed",
                properties = mapOf("login_method" to "mitid"),
            )

            // iOS: best-effort Supabase Auth via Edge Function (does not block login UI).
            // Gate opens after mint so schedule/homework RPCs don't race RLS.
            bgScope.launch {
                supabaseAuth.authenticateAndMarkReady(finalCreds, school.id)
                settingsStore.syncSubjectsFromSupabase(student)
            }
            // Opportunistic directory catalog + message prefetch (no permission prompts).
            schedulePostLoginSync()

            Timber.i("Login complete studentId=%s gymId=%s", personId, school.id)
            AppResult.Success(student)
        } catch (e: LectioError) {
            Timber.w(e, "Login validation failed")
            AppResult.Failure(e.toAppError())
        } catch (e: Exception) {
            Timber.e(e, "Login validation crashed")
            AppResult.Failure(LectioError.Unknown(e.message, e).toAppError())
        }
    }

    fun enterDemo(): Student {
        sessionController.installDemoSession()
        PostHog.capture(event = "demo_entered")
        // Demo never uses Supabase — open gate so any best-effort calls no-op quickly
        bgScope.launch { supabaseAuth.ensureSessionIfNeeded(Student.Demo) }
        schedulePostLoginSync()
        return Student.Demo
    }

    /**
     * Directory full-catalog offline snapshot + message folder/thread prefetch.
     * Fire-and-forget; never prompts for background/battery permissions.
     */
    private fun schedulePostLoginSync() {
        bgScope.launch {
            runCatching { directorySync.syncFullCatalog() }
                .onFailure { Timber.w(it, "Directory catalog sync failed") }
        }
        messagePrefetcher.schedulePrefetch()
    }

    fun logout() {
        PostHog.capture(event = "logged_out")
        PostHog.reset()
        webViewCookieExtractor.clearLectioCookies()
        sessionController.clearSession()
        bgScope.launch { supabaseAuth.signOutLocal() }
    }

    /**
     * Cold-start: re-mint Supabase session if needed, then open the session gate
     * (iOS: ensureSupabaseSession). Returns a Job callers can join before remote work.
     */
    fun ensureSupabaseSession(student: Student): kotlinx.coroutines.Job {
        return bgScope.launch {
            if (student.isDemo) {
                // ensureSessionIfNeeded marks ready in finally
                supabaseAuth.ensureSessionIfNeeded(student)
                schedulePostLoginSync()
                return@launch
            }
            supabaseAuth.ensureSessionIfNeeded(student)
            settingsStore.syncSubjectsFromSupabase(student)
            schedulePostLoginSync()
        }
    }
}
