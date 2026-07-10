package dk.betterlectio.android.feature.schedule

import java.time.LocalDate
import java.time.LocalDateTime

enum class EventStatus { NORMAL, CHANGED, CANCELLED }

data class ScheduleEvent(
    val id: String,
    val title: String,
    val team: String = "",
    val teacher: String? = null,
    val room: String? = null,
    val status: EventStatus = EventStatus.NORMAL,
    val start: LocalDateTime? = null,
    val end: LocalDateTime? = null,
    val date: LocalDate,
    val notes: String? = null,
    val homework: String? = null,
    val isAllDay: Boolean = false,
    val href: String? = null,
)

data class ScheduleDay(
    val date: LocalDate,
    val events: List<ScheduleEvent>,
    val informations: List<String> = emptyList(),
)

data class ScheduleWeek(
    val year: Int,
    val week: Int,
    val days: List<ScheduleDay>,
)
