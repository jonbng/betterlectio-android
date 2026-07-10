package dk.betterlectio.android.core.lectio.http

import dk.betterlectio.android.core.lectio.model.FetchPriority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes Lectio HTTP attempts AND enforces a minimum gap between consecutive requests.
 *
 * iOS parity: PriorityRequestLimiter in LectioHTTPClient.swift —
 * concurrent redirect races can look like token replay to Lectio's autologin detector.
 *
 * Cancellation-safe: cancelled waiters are removed from the queue; if a waiter already
 * received the permit and is then cancelled, the permit is released for the next waiter.
 */
class PriorityRequestLimiter(
    private val minIntervalMs: Long = 100L,
) {
    private val mutex = Mutex()
    private var busy = false
    private val importantWaiters = ArrayDeque<CompletableDeferred<Unit>>()
    private val opportunisticWaiters = ArrayDeque<CompletableDeferred<Unit>>()
    private var lastEndedAtMs: Long = 0L

    suspend fun <T> withPermit(priority: FetchPriority, block: suspend () -> T): T {
        acquire(priority)
        try {
            val elapsed = System.currentTimeMillis() - lastEndedAtMs
            if (lastEndedAtMs > 0L && elapsed < minIntervalMs) {
                delay(minIntervalMs - elapsed)
            }
            return block()
        } finally {
            release()
        }
    }

    private suspend fun acquire(priority: FetchPriority) {
        val waiter = mutex.withLock {
            val canTakeImmediately = when (priority) {
                FetchPriority.Important -> !busy
                FetchPriority.Opportunistic -> !busy && importantWaiters.isEmpty()
            }
            if (canTakeImmediately) {
                busy = true
                null
            } else {
                val deferred = CompletableDeferred<Unit>()
                when (priority) {
                    FetchPriority.Important -> importantWaiters.addLast(deferred)
                    FetchPriority.Opportunistic -> opportunisticWaiters.addLast(deferred)
                }
                deferred
            }
        }
        if (waiter == null) return
        try {
            waiter.await()
        } catch (e: CancellationException) {
            val ownsSlot = mutex.withLock {
                val stillQueued =
                    importantWaiters.remove(waiter) || opportunisticWaiters.remove(waiter)
                // Slot already transferred to us (complete succeeded) but we cancelled before block.
                !stillQueued && waiter.isCompleted && !waiter.isCancelled
            }
            if (ownsSlot) {
                release()
            }
            throw e
        }
    }

    private suspend fun release() {
        mutex.withLock {
            lastEndedAtMs = System.currentTimeMillis()
            while (true) {
                val next = importantWaiters.removeFirstOrNull()
                    ?: opportunisticWaiters.removeFirstOrNull()
                if (next == null) {
                    busy = false
                    break
                }
                // complete() returns false if the waiter was already cancelled/completed.
                if (next.complete(Unit)) break
            }
        }
    }
}
