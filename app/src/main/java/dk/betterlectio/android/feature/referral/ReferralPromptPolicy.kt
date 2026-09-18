package dk.betterlectio.android.feature.referral

internal object ReferralPromptPolicy {
    const val MINIMUM_ACTIVE_DAYS = 2
    const val MAXIMUM_IMPRESSIONS = 3
    const val COOLDOWN_MILLIS = 14L * 24 * 60 * 60 * 1_000

    fun isEligible(
        activeDayCount: Int,
        impressionCount: Int,
        lastShownAtMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        if (activeDayCount < MINIMUM_ACTIVE_DAYS) return false
        if (impressionCount >= MAXIMUM_IMPRESSIONS) return false
        return lastShownAtMillis == null || nowMillis - lastShownAtMillis >= COOLDOWN_MILLIS
    }
}
