package dk.betterlectio.android.feature.referral

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferralPromptPolicyTest {
    @Test
    fun requiresRepeatValueBeforeFirstPrompt() {
        assertFalse(ReferralPromptPolicy.isEligible(1, 0, null, 1_000L))
        assertTrue(ReferralPromptPolicy.isEligible(2, 0, null, 1_000L))
    }

    @Test
    fun enforcesCooldownAndLifetimeCap() {
        val shownAt = 1_000L
        assertFalse(ReferralPromptPolicy.isEligible(2, 1, shownAt, shownAt + 60_000L))
        assertTrue(
            ReferralPromptPolicy.isEligible(
                2,
                1,
                shownAt,
                shownAt + ReferralPromptPolicy.COOLDOWN_MILLIS,
            ),
        )
        assertFalse(ReferralPromptPolicy.isEligible(10, 3, null, Long.MAX_VALUE))
    }
}
