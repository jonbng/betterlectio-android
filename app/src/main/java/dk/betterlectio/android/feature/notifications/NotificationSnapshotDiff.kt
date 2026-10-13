package dk.betterlectio.android.feature.notifications

/**
 * Pure snapshot diff so WorkManager only fires on *new* changes, not re-fires.
 */
object NotificationSnapshotDiff {
    /**
     * A missing snapshot is a baseline, not evidence that every current item is new.
     */
    fun freshIds(
        previous: Set<String>,
        current: Set<String>,
        initialized: Boolean,
    ): Set<String> = if (initialized) current - previous else emptySet()

    fun newIds(previous: Set<String>, current: Set<String>): Set<String> =
        current - previous

    fun eventKey(id: String, status: String): String = "event:$id:$status"
    fun messageKey(id: String): String = "msg:$id"
    fun assignmentKey(id: String, status: String): String = "asg:$id:$status"

    /** Stable across worker runs so one logical item updates its own notification. */
    fun notificationId(key: String): Int = key.hashCode() and Int.MAX_VALUE
}
