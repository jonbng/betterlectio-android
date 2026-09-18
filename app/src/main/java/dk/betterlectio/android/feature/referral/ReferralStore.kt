package dk.betterlectio.android.feature.referral

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-student referral finalization, conversion, and frequency-capped prompt state.
 */
@Singleton
class ReferralStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("bl_referral", Context.MODE_PRIVATE)

    fun wasFinalizeAttempted(studentId: String): Boolean =
        prefs.getBoolean(finalizeKey(studentId), false)

    fun markFinalizeAttempted(studentId: String) {
        prefs.edit { putBoolean(finalizeKey(studentId), true) }
    }

    fun recordSuccessfulUse(studentId: String, nowMillis: Long = System.currentTimeMillis()) {
        val key = activeDaysKey(studentId)
        val days = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        days += (nowMillis / DAY_MILLIS).toString()
        prefs.edit { putStringSet(key, days) }
    }

    fun canShowNudge(studentId: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        ReferralPromptPolicy.isEligible(
            activeDayCount = prefs.getStringSet(activeDaysKey(studentId), emptySet()).orEmpty().size,
            impressionCount = prefs.getInt(nudgeImpressionsKey(studentId), 0),
            lastShownAtMillis = prefs.getLong(nudgeLastShownKey(studentId), 0L).takeIf { it > 0L },
            nowMillis = nowMillis,
        )

    fun recordNudgeImpression(studentId: String, nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit {
            putInt(
                nudgeImpressionsKey(studentId),
                prefs.getInt(nudgeImpressionsKey(studentId), 0) + 1,
            )
            putLong(nudgeLastShownKey(studentId), nowMillis)
        }
    }

    fun lastKnownConversions(studentId: String): Int =
        prefs.getInt(conversionsKey(studentId), -1)

    fun setLastKnownConversions(studentId: String, conversions: Int) {
        prefs.edit { putInt(conversionsKey(studentId), conversions) }
    }

    private fun finalizeKey(studentId: String) = "finalize_attempted:$studentId"
    private fun activeDaysKey(studentId: String) = "active_days_v2:$studentId"
    private fun nudgeImpressionsKey(studentId: String) = "nudge_impressions_v2:$studentId"
    private fun nudgeLastShownKey(studentId: String) = "nudge_last_shown_v2:$studentId"
    private fun conversionsKey(studentId: String) = "conversions:$studentId"

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1_000
    }
}
