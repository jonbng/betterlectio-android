package dk.betterlectio.android.core.lectio.auth

import android.webkit.CookieManager
import dk.betterlectio.android.core.lectio.model.LectioCredentials
import dk.betterlectio.android.core.lectio.model.LectioCredentials.Companion.COOKIE_AUTOLOGIN
import dk.betterlectio.android.core.lectio.model.LectioCredentials.Companion.COOKIE_SESSION_ID
import dk.betterlectio.android.core.lectio.model.LectioCredentials.Companion.PRIMARY_COOKIE_NAMES
import dk.betterlectio.android.core.lectio.model.LectioError
import dk.betterlectio.android.core.lectio.scrape.LectioUrls
import dk.betterlectio.android.core.result.AppResult
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls Lectio cookies after MitID/UniLogin WebView login.
 * iOS parity: CookieManager.extractLectioCredentials
 */
interface WebViewCookieExtractor {
    fun extractFromCookieManager(): AppResult<LectioCredentials>
    fun clearLectioCookies()
}

@Singleton
class AndroidWebViewCookieExtractor @Inject constructor() : WebViewCookieExtractor {

    override fun extractFromCookieManager(): AppResult<LectioCredentials> {
        val cm = CookieManager.getInstance()
        cm.flush()
        val raw = cm.getCookie(LectioUrls.ORIGIN)
            ?: return AppResult.Failure(LectioError.MissingCookies.toAppError())

        val map = parseCookieHeader(raw)
        val autologin = map[COOKIE_AUTOLOGIN]
        val sessionId = map[COOKIE_SESSION_ID]
        if (autologin.isNullOrEmpty() || sessionId.isNullOrEmpty()) {
            return AppResult.Failure(LectioError.MissingCookies.toAppError())
        }

        val additional = map
            .filterKeys { it !in PRIMARY_COOKIE_NAMES }
            .filterValues { it.isNotEmpty() }
            .toMutableMap()

        val creds = LectioCredentials(
            autologinkey = autologin,
            sessionId = sessionId,
            autologinkeyExpiresAt = Instant.now().plusSeconds(60L * 60 * 24 * 60),
            sessionIdExpiresAt = Instant.now().plusSeconds(60L * 60 * 24 * 60),
            additionalCookies = additional,
        ).seededIsLoggedIn()

        return AppResult.Success(creds)
    }

    override fun clearLectioCookies() {
        val cm = CookieManager.getInstance()
        // removeAllCookies is async; still best-effort on logout.
        cm.removeAllCookies(null)
        cm.flush()
    }

    internal fun parseCookieHeader(header: String): Map<String, String> {
        return header.split(';')
            .map { it.trim() }
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null
                else part.substring(0, eq).trim() to part.substring(eq + 1).trim()
            }
            .toMap()
    }
}
