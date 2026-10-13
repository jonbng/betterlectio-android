package dk.betterlectio.android.core.lectio.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide Lectio session signals.
 * iOS parity: Notification.Name.lectioSessionExpired
 */
@Singleton
class SessionEvents @Inject constructor() {
    data class SessionRecovery(val trigger: String, val outcome: String, val durationMs: Long)
    data class SessionExpired(
        val detectionSource: String,
        val recovery: SessionRecovery? = null,
    )

    private val _sessionExpired = MutableSharedFlow<SessionExpired>(
        extraBufferCapacity = 1,
        replay = 0,
    )
    val sessionExpired: SharedFlow<SessionExpired> = _sessionExpired.asSharedFlow()
    private val _sessionRecovery = MutableSharedFlow<SessionRecovery>(extraBufferCapacity = 1, replay = 0)
    val sessionRecovery: SharedFlow<SessionRecovery> = _sessionRecovery.asSharedFlow()

    fun emitSessionExpired(detectionSource: String, recovery: SessionRecovery? = null) {
        _sessionExpired.tryEmit(SessionExpired(detectionSource, recovery))
    }

    fun emitSessionRecovery(trigger: String, outcome: String, durationMs: Long) {
        _sessionRecovery.tryEmit(SessionRecovery(trigger, outcome, durationMs))
    }
}
