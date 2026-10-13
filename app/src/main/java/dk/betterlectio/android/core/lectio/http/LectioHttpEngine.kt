package dk.betterlectio.android.core.lectio.http

import dk.betterlectio.android.BuildConfig
import dk.betterlectio.android.core.lectio.cookie.CookieHeaderBuilder
import dk.betterlectio.android.core.lectio.cookie.LectioCookieJar
import dk.betterlectio.android.core.lectio.model.FetchPriority
import dk.betterlectio.android.core.lectio.model.LectioCredentials
import dk.betterlectio.android.core.lectio.model.LectioError
import dk.betterlectio.android.core.lectio.model.LectioRequest
import dk.betterlectio.android.core.lectio.model.LectioResponse
import dk.betterlectio.android.core.lectio.scrape.LectioHtml
import dk.betterlectio.android.core.lectio.session.CredentialStore
import dk.betterlectio.android.core.lectio.session.SessionEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Core Lectio HTTP loop: serial + cooldown, manual redirects, cookie merge, retries.
 *
 * iOS parity: LectioHTTPClient.performRequest / performSingleAttempt
 */
data class LectioEngineResult(
    val response: LectioResponse,
    val credentials: LectioCredentials,
)

@Singleton
class LectioHttpEngine @Inject constructor(
    @param:Named("lectio") private val client: OkHttpClient,
    private val credentialStore: CredentialStore,
    private val sessionEvents: SessionEvents,
    private val limiter: PriorityRequestLimiter,
) {
    private val maxRedirects = 5
    private val maxAttempts = 3

    suspend fun execute(
        request: LectioRequest,
        initialCredentials: LectioCredentials,
    ): LectioEngineResult = withContext(Dispatchers.IO) {
        // Retained across InvalidCredentials retries so Set-Cookie rotations during a failed
        // login probe (studentId == null) are not discarded (iOS keychain re-read only helps
        // when studentId is already known).
        var currentCredentials = initialCredentials
        var lastError: LectioError = LectioError.Unknown("No response")
        var recoveryTrigger: String? = null
        var recoveryStartedAt = 0L

        var attemptIndex = 0
        while (attemptIndex < maxAttempts) {
            val attempt = attemptIndex
            attemptIndex++
            try {
                return@withContext limiter.withPermit(request.priority) {
                    // Re-read store so concurrent rotations are visible (iOS keychain re-read).
                    val fresh = request.studentId
                        ?.let { credentialStore.loadCredentials(it) }
                        ?: currentCredentials
                    when (val outcome = performSingleAttempt(request, fresh)) {
                        is SingleAttemptOutcome.Success -> {
                            currentCredentials = outcome.credentials
                            recoveryTrigger?.let { trigger ->
                                sessionEvents.emitSessionRecovery(
                                    trigger,
                                    "success",
                                    (System.nanoTime() - recoveryStartedAt) / 1_000_000,
                                )
                            }
                            LectioEngineResult(outcome.response, outcome.credentials)
                        }
                        is SingleAttemptOutcome.Failure -> {
                            currentCredentials = outcome.credentials
                            throw AttemptFailure(outcome.error, outcome.detectionSource)
                        }
                    }
                }
            } catch (failure: AttemptFailure) {
                val canRecover = request.studentId != null &&
                    (failure.error is LectioError.InvalidCredentials || failure.error is LectioError.SessionExpired)
                if (canRecover && recoveryTrigger == null) {
                    val trigger = failure.detectionSource ?: "http_invalid_credentials"
                    recoveryTrigger = trigger
                    recoveryStartedAt = System.nanoTime()
                    currentCredentials = currentCredentials.clearingSessionId()
                    credentialStore.updateCredentials(currentCredentials, request.studentId!!)
                    Timber.i("Auth recovery: retrying without ASP.NET_SessionId (%s)", trigger)
                    // Recovery is one additional bounded attempt, independent of the normal
                    // transient retry budget. This also covers an anomaly on the final attempt.
                    attemptIndex--
                    continue
                }
                if (canRecover && recoveryTrigger != null) {
                    val recovery = SessionEvents.SessionRecovery(
                        trigger = recoveryTrigger,
                        outcome = "failure",
                        durationMs = (System.nanoTime() - recoveryStartedAt) / 1_000_000,
                    )
                    sessionEvents.emitSessionExpired(
                        "${recoveryTrigger}_recovery_failed",
                        recovery,
                    )
                    throw LectioError.SessionExpired
                }
                when (val error = failure.error) {
                    is LectioError.InvalidCredentials -> {
                        lastError = error
                        if (attempt < maxAttempts - 1) {
                            val delayMs = if (attempt == 0) 500L else 1500L
                            delay(delayMs)
                            continue
                        }
                    }
                    is LectioError.RobotDetection -> {
                        lastError = error
                        if (attempt < maxAttempts - 1) {
                            delay(3000L + Random.nextLong(0, 1500))
                            continue
                        }
                    }
                    else -> throw error
                }
            } catch (e: LectioError.SessionExpired) {
                // Definitive death — do not retry; session already signalled if UniLogin path.
                throw e
            } catch (e: LectioError.InvalidCredentials) {
                lastError = e
                if (attempt < maxAttempts - 1) {
                    val delayMs = if (attempt == 0) 500L else 1500L
                    Timber.d("Auth blip attempt %d/%d — retry in %dms", attempt + 1, maxAttempts, delayMs)
                    delay(delayMs)
                }
            } catch (e: LectioError.RobotDetection) {
                lastError = e
                if (attempt < maxAttempts - 1) {
                    val delayMs = 3000L + Random.nextLong(0, 1500)
                    Timber.d("Robot detection attempt %d/%d — backoff %dms", attempt + 1, maxAttempts, delayMs)
                    delay(delayMs)
                }
            } catch (e: LectioError.Network) {
                lastError = e
                if (attempt < maxAttempts - 1 && isTransient(e.cause)) {
                    Timber.d("Transient network attempt %d/%d — retry", attempt + 1, maxAttempts)
                    delay(1000)
                } else {
                    throw e
                }
            } catch (e: LectioError) {
                throw e
            } catch (e: IOException) {
                lastError = mapIoException(e)
                if (attempt < maxAttempts - 1 && isTransient(e)) {
                    delay(1000)
                } else {
                    throw lastError
                }
            }
        }

        if (lastError is LectioError.InvalidCredentials) {
            sessionEvents.emitSessionExpired("http_invalid_credentials_after_retries")
            throw LectioError.SessionExpired
        }
        throw lastError
    }

    private sealed class SingleAttemptOutcome {
        data class Success(
            val response: LectioResponse,
            val credentials: LectioCredentials,
        ) : SingleAttemptOutcome()

        data class Failure(
            val error: LectioError,
            val credentials: LectioCredentials,
            val detectionSource: String? = null,
        ) : SingleAttemptOutcome()
    }

    private class AttemptFailure(
        val error: LectioError,
        val detectionSource: String?,
    ) : Exception()

    private fun performSingleAttempt(
        request: LectioRequest,
        credentials: LectioCredentials,
    ): SingleAttemptOutcome {
        var currentCredentials = credentials
        var currentUrl = request.url
        var redirectCount = 0
        var method = request.method
        var body = request.body

        fun fail(error: LectioError, detectionSource: String? = null): SingleAttemptOutcome =
            SingleAttemptOutcome.Failure(error, currentCredentials, detectionSource)

        while (redirectCount <= maxRedirects) {
            val cookieHeader = CookieHeaderBuilder.build(currentCredentials)
            if (BuildConfig.DEBUG) {
                Timber.d(
                    "Lectio %s %s | %s",
                    method,
                    currentUrl.encodedPath,
                    CookieHeaderBuilder.redactedPreview(currentCredentials),
                )
            }

            val okRequest = Request.Builder()
                .url(currentUrl)
                .method(
                    method,
                    body?.toRequestBody(
                        request.headers["Content-Type"]?.toMediaType()
                            ?: FORM_URLENCODED,
                    ),
                )
                .header("User-Agent", LectioUserAgent.VALUE)
                .header("Referer", LectioUserAgent.REFERER)
                .apply {
                    // A redirect must never forward Lectio credentials to another domain.
                    if (isLectioHost(currentUrl.host) || currentUrl.host == request.url.host) {
                        header("Cookie", cookieHeader)
                    }
                    request.headers.forEach { (k, v) ->
                        if (!k.equals("Cookie", ignoreCase = true)) {
                            header(k, v)
                        }
                    }
                }
                .build()

            client.newCall(okRequest).execute().use { response ->
                currentCredentials = mergeCookiesFromResponse(response, currentCredentials, request.studentId)

                when (response.code) {
                    in 200..299 -> {
                        val finalUrl = response.request.url
                        if (UniLoginDetector.isUniLoginBroker(finalUrl)) {
                            Timber.w("Final URL UniLogin broker — recovery required")
                            return fail(LectioError.InvalidCredentials, "http_final_unilogin_broker")
                        }
                        val bytes = response.body?.bytes() ?: ByteArray(0)
                        val html = LectioHtml.decode(bytes)
                        if (LectioHtml.isRobotDetectionPage(html)) {
                            return fail(LectioError.RobotDetection)
                        }
                        // Authenticated pages should not land on login.aspx
                        if (LectioHtml.isLoginPageUrl(finalUrl.toString()) &&
                            !finalUrl.toString().contains("unilogin", ignoreCase = true)
                        ) {
                            return fail(LectioError.InvalidCredentials, "http_final_login_page")
                        }
                        return SingleAttemptOutcome.Success(
                            LectioResponse(
                                body = html,
                                bytes = bytes,
                                finalUrl = finalUrl,
                                statusCode = response.code,
                                contentType = response.header("Content-Type"),
                                contentDisposition = response.header("Content-Disposition"),
                            ),
                            currentCredentials,
                        )
                    }

                    301, 302, 303, 307, 308 -> {
                        val location = response.header("Location")
                            ?: return fail(LectioError.Unknown("Redirect without Location"))
                        val redirectUrl = resolveRedirect(currentUrl, location)
                            ?: return fail(LectioError.Unknown("Invalid redirect: $location"))

                        if (UniLoginDetector.isUniLoginBroker(redirectUrl)) {
                            Timber.w("Redirect to UniLogin broker — recovery required")
                            return fail(LectioError.InvalidCredentials, "http_redirect_unilogin_broker")
                        }

                        if (redirectCount >= maxRedirects) {
                            Timber.w("Exceeded %d redirects — recovery required", maxRedirects)
                            return fail(
                                LectioError.InvalidCredentials,
                                "http_redirect_budget_exceeded",
                            )
                        }

                        // RFC: 303 / 302 traditionally become GET
                        if (response.code == 303 || response.code == 302) {
                            method = "GET"
                            body = null
                        }
                        currentUrl = redirectUrl
                        redirectCount++
                    }

                    401, 403 -> return fail(
                        LectioError.InvalidCredentials,
                        "http_status_${response.code}",
                    )

                    else -> return fail(LectioError.Http(response.code))
                }
            }
        }

        Timber.w("Redirect loop exceeded — treating as session death")
        return fail(LectioError.InvalidCredentials, "http_redirect_budget_exceeded")
    }

    private fun mergeCookiesFromResponse(
        response: Response,
        current: LectioCredentials,
        studentId: String?,
    ): LectioCredentials {
        val setCookies = response.headers("Set-Cookie")
        if (setCookies.isEmpty()) return current
        val host = response.request.url.host
        val updated = LectioCookieJar.mergeSetCookies(current, setCookies, host) ?: return current
        if (studentId != null) {
            try {
                credentialStore.updateCredentials(updated, studentId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to persist rotated credentials")
            }
        }
        if (BuildConfig.DEBUG) {
            Timber.d("Credentials rotated: %s", CookieHeaderBuilder.redactedPreview(updated))
        }
        return updated
    }

    private fun resolveRedirect(base: HttpUrl, location: String): HttpUrl? {
        location.toHttpUrlOrNull()?.let { return it }
        return base.resolve(location)
    }

    private fun isLectioHost(host: String): Boolean =
        host == "lectio.dk" || host.endsWith(".lectio.dk")

    private fun isTransient(cause: Throwable?): Boolean = when (cause) {
        is SocketTimeoutException -> true
        is UnknownHostException -> true
        is IOException -> {
            val msg = cause.message.orEmpty()
            msg.contains("timeout", ignoreCase = true) ||
                msg.contains("connection", ignoreCase = true)
        }
        else -> false
    }

    private fun mapIoException(e: IOException): LectioError = when (e) {
        is UnknownHostException -> LectioError.Offline
        else -> LectioError.Network(e)
    }

    companion object {
        private val FORM_URLENCODED = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()
    }
}
