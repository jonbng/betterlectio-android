package dk.betterlectio.android.feature.feedback

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure constants / contract checks — sensor events need instrumentation.
 * Keeps the detector defaults intentional and documented.
 */
class ShakeDetectorTest {

    @Test
    fun defaultsAreSensible() {
        assertTrue(ShakeDetector.DEFAULT_THRESHOLD > 500f)
        assertTrue(ShakeDetector.DEFAULT_THRESHOLD < 5_000f)
        assertTrue(ShakeDetector.DEFAULT_COOLDOWN_MS in 1_000L..10_000L)
    }
}
