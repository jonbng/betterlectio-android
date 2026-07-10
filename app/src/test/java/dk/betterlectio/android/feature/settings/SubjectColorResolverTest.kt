package dk.betterlectio.android.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SubjectColorResolverTest {
    @Test
    fun custom_color_wins_over_palette() {
        val custom = mapOf("Matematik" to 0xFF112233L)
        val color = SubjectColorResolver.resolve("Matematik", custom)
        assertEquals(0xFF112233L, color)
    }

    @Test
    fun different_subjects_can_resolve_distinct_defaults() {
        val a = SubjectColorResolver.resolve("Matematik", emptyMap())
        val b = SubjectColorResolver.resolve("Dansk", emptyMap())
        // Not required to differ, but palette mapping must be stable
        assertEquals(a, SubjectColorResolver.resolve("Matematik", emptyMap()))
        assertEquals(b, SubjectColorResolver.resolve("Dansk", emptyMap()))
        assertNotEquals(0L, a)
    }

    @Test
    fun empty_key_uses_first_palette_entry() {
        assertEquals(
            SettingsStore.DEFAULT_PALETTE.first(),
            SubjectColorResolver.resolve("  ", emptyMap()),
        )
    }
}
