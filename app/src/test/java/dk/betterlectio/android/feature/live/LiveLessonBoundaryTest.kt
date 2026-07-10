package dk.betterlectio.android.feature.live

import dk.betterlectio.android.feature.schedule.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LiveLessonBoundaryTest {
    private val day = LocalDate.of(2026, 3, 10)

    private fun event(id: String, startH: Int, startM: Int, endH: Int, endM: Int) = ScheduleEvent(
        id = id,
        title = id,
        start = LocalDateTime.of(day, LocalTime.of(startH, startM)),
        end = LocalDateTime.of(day, LocalTime.of(endH, endM)),
        date = day,
    )

    @Test
    fun nextBoundary_prefers_soonest_start_or_end() {
        val events = listOf(
            event("a", 8, 0, 9, 0),
            event("b", 10, 0, 11, 0),
        )
        val now = LocalDateTime.of(day, LocalTime.of(7, 30))
        val next = LiveLessonBoundary.nextBoundary(events, now)
        assertNotNull(next)
        assertEquals("a", next!!.eventId)
        assertEquals(LiveLessonBoundary.Boundary.Kind.START, next.kind)
    }

    @Test
    fun nextBoundary_during_lesson_returns_end() {
        val events = listOf(event("a", 8, 0, 9, 0))
        val now = LocalDateTime.of(day, LocalTime.of(8, 30))
        val next = LiveLessonBoundary.nextBoundary(events, now)
        assertNotNull(next)
        assertEquals(LiveLessonBoundary.Boundary.Kind.END, next!!.kind)
    }

    @Test
    fun currentLesson_detects_ongoing() {
        val events = listOf(event("a", 8, 0, 9, 0), event("b", 10, 0, 11, 0))
        val now = LocalDateTime.of(day, LocalTime.of(8, 20))
        assertEquals("a", LiveLessonBoundary.currentLesson(events, now)?.id)
        assertNull(LiveLessonBoundary.currentLesson(events, LocalDateTime.of(day, LocalTime.of(9, 30))))
    }

    @Test
    fun upcomingBoundaries_ordered() {
        val events = listOf(event("a", 8, 0, 9, 0), event("b", 10, 0, 11, 0))
        val now = LocalDateTime.of(day, LocalTime.of(7, 0))
        val list = LiveLessonBoundary.upcomingBoundaries(events, now, limit = 10)
        assertEquals(4, list.size)
        assertTrue(list.zipWithNext().all { (a, b) -> !a.at.isAfter(b.at) })
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
