package dk.betterlectio.android.feature.messages

import dk.betterlectio.android.core.result.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opportunistic message folder + thread prefetch after auth.
 * iOS parity: MessageListPrefetcher — no permission prompts.
 */
@Singleton
class MessageListPrefetcher @Inject constructor(
    private val repository: MessageRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun schedulePrefetch() {
        scope.launch {
            try {
                repository.loadFolder(MessageFolder.UNREAD, forceRefresh = true)
                val newest = repository.loadFolder(MessageFolder.NEWEST, forceRefresh = true)
                if (newest is AppResult.Success) {
                    newest.data.take(3).forEach { thread ->
                        repository.loadThread(thread)
                    }
                }
                Timber.d("Message prefetch completed")
            } catch (t: Throwable) {
                Timber.w(t, "Message prefetch failed")
            }
        }
    }
}
