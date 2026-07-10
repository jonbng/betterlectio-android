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
        var currentCredentials = initialCredentials
        var lastError: LectioError = LectioError.Unknown("No response")

        for (attempt in 0 until maxAttempts) {
            try {
                return@withContext limiter.withPermit(request.priority) {
                    // Re-read store so concurrent rotations are visible (iOS keychain re-read).
                    val fresh = request.studentId
                        ?.let { credentialStore.loadCredentials(it) }
                        ?: currentCredentials
                    val (response, creds) = performSingleAttempt(request, fresh)
                    currentCredentials = creds
                    LectioEngineResult(response, creds)
                }
            } catch (e: LectioError.SessionExpired) {
                // Definitive death — do not retry; session already cleared if UniLogin path.
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
            sessionEvents.emitSessionExpired()
            throw LectioError.SessionExpired
        }
        throw lastError
    }

    private fun performSingleAttempt(
        request: LectioRequest,
        credentials: LectioCredentials,
    ): Pair<LectioResponse, LectioCredentials> {
        var currentCredentials = credentials
        var currentUrl = request.url
        var redirectCount = 0
        var method = request.method
        var body = request.body

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
                .header("Cookie", cookieHeader)
                .header("User-Agent", LectioUserAgent.VALUE)
                .header("Referer", LectioUserAgent.REFERER)
                .header("Accept-Encoding", "gzip, deflate, br")
                .apply {
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
                            Timber.w("Final URL UniLogin broker — session expired")
                            sessionEvents.emitSessionExpired()
                            throw LectioError.SessionExpired
                        }
                        val bytes = response.body?.bytes() ?: ByteArray(0)
                        val html = LectioHtml.decode(bytes)
                        if (LectioHtml.isRobotDetectionPage(html)) {
                            throw LectioError.RobotDetection
                        }
                        // Authenticated pages should not land on login.aspx
                        if (LectioHtml.isLoginPageUrl(finalUrl.toString()) &&
                            !finalUrl.toString().contains("unilogin", ignoreCase = true)
                        ) {
                            throw LectioError.InvalidCredentials
                        }
                        return LectioResponse(
                            body = html,
                            bytes = bytes,
                            finalUrl = finalUrl,
                            statusCode = response.code,
                        ) to currentCredentials
                    }

                    301, 302, 303, 307, 308 -> {
                        val location = response.header("Location")
                            ?: throw LectioError.Unknown("Redirect without Location")
                        val redirectUrl = resolveRedirect(currentUrl, location)
                            ?: throw LectioError.Unknown("Invalid redirect: $location")

                        if (UniLoginDetector.isUniLoginBroker(redirectUrl)) {
                            Timber.w("Redirect to UniLogin broker — session expired")
                            sessionEvents.emitSessionExpired()
                            throw LectioError.SessionExpired
                        }

                        if (redirectCount >= maxRedirects) {
                            // Redirect budget is not necessarily dead autologin.
                            throw LectioError.Unknown("Too many redirects ($maxRedirects)")
                        }

                        // RFC: 303 / 302 traditionally become GET
                        if (response.code == 303 || response.code == 302) {
                            method = "GET"
                            body = null
                        }
                        currentUrl = redirectUrl
                        redirectCount++
                    }

                    401, 403 -> throw LectioError.InvalidCredentials

                    else -> throw LectioError.Http(response.code)
                }
            }
        }

        throw LectioError.Unknown("Redirect loop exceeded")
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
