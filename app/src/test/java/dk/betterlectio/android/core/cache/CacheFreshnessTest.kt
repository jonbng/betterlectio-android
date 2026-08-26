package dk.betterlectio.android.core.cache

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class CacheFreshnessTest {
    private val ttl = Duration.ofMinutes(5)
    private val now = 1_000_000L

    @Test
    fun entry_younger_than_ttl_is_fresh() {
        val entry = CachedValue("value", now - ttl.toMillis() + 1)

        assertEquals(CacheFreshness.FRESH, entry.freshness(ttl, now))
    }

    @Test
    fun entry_at_ttl_boundary_is_stale() {
        val entry = CachedValue("value", now - ttl.toMillis())

        assertEquals(CacheFreshness.STALE, entry.freshness(ttl, now))
    }

    @Test
    fun older_entry_is_stale() {
        val entry = CachedValue("value", now - ttl.toMillis() - 1)

        assertEquals(CacheFreshness.STALE, entry.freshness(ttl, now))
    }

    @Test
    fun future_timestamp_is_treated_as_stale() {
        val entry = CachedValue("value", now + 60_000)

        assertEquals(CacheFreshness.STALE, entry.freshness(ttl, now))
    }

    @Test
    fun invalid_timestamp_is_stale() {
        val entry = CachedValue("value", 0L)

        assertEquals(CacheFreshness.STALE, entry.freshness(ttl, now))
    }
}
