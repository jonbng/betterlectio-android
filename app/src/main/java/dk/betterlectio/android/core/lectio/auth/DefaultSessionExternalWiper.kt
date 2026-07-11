package dk.betterlectio.android.core.lectio.auth

import dk.betterlectio.android.core.lectio.session.SessionExternalWiper
import dk.betterlectio.android.feature.supabase.SupabaseAuthService
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSessionExternalWiper @Inject constructor(
    private val webViewCookieExtractor: WebViewCookieExtractor,
    private val supabaseAuth: SupabaseAuthService,
) : SessionExternalWiper {

    override suspend fun wipeExternalAuthState() {
        runCatching { webViewCookieExtractor.clearAllWebViewData() }
            .onFailure {
                Timber.w(it, "WebView wipe failed — falling back to cookie clear")
                webViewCookieExtractor.clearLectioCookies()
            }
        runCatching { supabaseAuth.signOutLocal() }
            .onFailure { Timber.w(it, "Supabase local sign-out failed") }
        Timber.i("External auth state wiped (WebView + Supabase)")
    }
}
