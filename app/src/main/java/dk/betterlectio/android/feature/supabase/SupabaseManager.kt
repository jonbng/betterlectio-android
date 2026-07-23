package dk.betterlectio.android.feature.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the optional official Supabase Kotlin client (iOS: `SupabaseManager`).
 * When not configured, [client] is null and all services skip remote work.
 *
 * [awaitSessionReady] lets callers wait until cold-start / login auth bootstrap finishes
 * so RLS-protected RPCs don't race the edge-function mint.
 */
@Singleton
class SupabaseManager @Inject constructor(
    val configuration: SupabaseConfig,
) {
    val client: SupabaseClient? = if (configuration.isConfigured) {
        createSupabaseClient(
            supabaseUrl = configuration.url.trimEnd('/'),
            supabaseKey = configuration.publishableKey,
        ) {
            install(Auth)
            install(Postgrest)
            install(Functions)
            install(Storage)
        }
    } else {
        Timber.w("Supabase not configured (SUPABASE_URL / SUPABASE_ANON_KEY); remote sync disabled")
        null
    }

    val isConfigured: Boolean get() = client != null

    /**
     * Completes when Supabase auth bootstrap is done (or immediately if unconfigured).
     * Reset on logout so the next login re-gates.
     */
    @Volatile
    private var sessionReady: CompletableDeferred<Unit> =
        if (isConfigured) CompletableDeferred() else CompletableDeferred<Unit>().also { it.complete(Unit) }

    fun markSessionReady() {
        val current = sessionReady
        if (!current.isCompleted) {
            current.complete(Unit)
            Timber.d("Supabase session gate: ready")
        }
    }

    /** After logout — next login must re-mint before remote work. */
    fun resetSessionReady() {
        if (!isConfigured) {
            sessionReady = CompletableDeferred<Unit>().also { it.complete(Unit) }
            return
        }
        sessionReady = CompletableDeferred()
        Timber.d("Supabase session gate: reset")
    }

    /**
     * Wait until [markSessionReady] (timeout → continue best-effort so offline UX never hangs).
     */
    suspend fun awaitSessionReady(timeoutMs: Long = SESSION_READY_TIMEOUT_MS) {
        if (!isConfigured) return
        val deferred = sessionReady
        if (deferred.isCompleted) return
        val completed = withTimeoutOrNull(timeoutMs) { deferred.await() }
        if (completed == null) {
            Timber.w("Supabase session gate: timed out after %dms — continuing best-effort", timeoutMs)
        }
    }

    companion object {
        const val SESSION_READY_TIMEOUT_MS = 15_000L
    }
}
