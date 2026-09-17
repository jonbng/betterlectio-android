package dk.betterlectio.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorTest {
    @Test
    fun light_schedule_washes_keep_dark_text_readable_for_every_hue() {
        for (hue in 0 until 360 step 8) {
            val background = SurfaceLight.scheduleWash(subjectColor(hue))
            assertEquals(1f, background.alpha, 0f)
            assertTrue("hue $hue", contrastRatio(Neutral10, background) >= 4.5f)
        }
    }

    @Test
    fun dark_schedule_washes_keep_light_text_readable_for_every_hue() {
        for (hue in 0 until 360 step 8) {
            val background = SurfaceDark.scheduleWash(subjectColor(hue))
            assertEquals(1f, background.alpha, 0f)
            assertTrue("hue $hue", contrastRatio(Neutral90, background) >= 4.5f)
        }
    }

    private fun subjectColor(hue: Int): Color = Color.hsv(hue.toFloat(), 0.62f, 0.88f)

    private fun contrastRatio(first: Color, second: Color): Float {
        val light = maxOf(first.luminance(), second.luminance())
        val dark = minOf(first.luminance(), second.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }
}
