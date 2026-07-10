package dk.betterlectio.android.feature.live

import dk.betterlectio.android.feature.schedule.ScheduleEvent
import java.time.LocalDateTime

/**
 * Pure helpers for scheduling live-lesson boundary refresh (start/end of lessons).
 * Testable without Android framework.
 */
object LiveLessonBoundary {
    data class Boundary(
        val at: LocalDateTime,
        val eventId: String,
        val kind: Kind,
        val title: String,
    ) {
        enum class Kind { START, END }
    }

    /**
     * Next boundary at or after [now] among timed events (start or end).
     */
    fun nextBoundary(
        events: List<ScheduleEvent>,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boundary? {
        val candidates = mutableListOf<Boundary>()
        for (e in events) {
            val s = e.start ?: continue
            val en = e.end ?: continue
            if (!s.isBefore(now)) {
                candidates += Boundary(s, e.id, Boundary.Kind.START, e.title)
            }
            if (!en.isBefore(now)) {
                candidates += Boundary(en, e.id, Boundary.Kind.END, e.title)
            }
        }
        return candidates.minByOrNull { it.at }
    }

    /** All boundaries in chronological order at/after [now]. */
    fun upcomingBoundaries(
        events: List<ScheduleEvent>,
        now: LocalDateTime = LocalDateTime.now(),
        limit: Int = 8,
    ): List<Boundary> {
        val candidates = mutableListOf<Boundary>()
        for (e in events) {
            val s = e.start ?: continue
            val en = e.end ?: continue
            if (!s.isBefore(now)) {
                candidates += Boundary(s, e.id, Boundary.Kind.START, e.title)
            }
            if (!en.isBefore(now)) {
                candidates += Boundary(en, e.id, Boundary.Kind.END, e.title)
            }
        }
        return candidates
            .sortedWith(compareBy({ it.at }, { it.eventId }, { it.kind.name }))
            .distinctBy { Triple(it.at, it.eventId, it.kind) }
            .take(limit)
    }

    fun currentLesson(
        events: List<ScheduleEvent>,
        now: LocalDateTime = LocalDateTime.now(),
    ): ScheduleEvent? = events.firstOrNull { e ->
        val s = e.start
        val en = e.end
        s != null && en != null && !now.isBefore(s) && now.isBefore(en)
    }
}
