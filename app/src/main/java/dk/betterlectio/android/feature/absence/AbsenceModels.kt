package dk.betterlectio.android.feature.absence

import java.time.LocalDate
import java.time.LocalDateTime

data class AbsenceFraction(
    val current: Double = 0.0,
    val total: Double = 0.0,
)

data class AbsenceTeamRow(
    val team: String,
    /** Flutter: `HE` + holdelementid */
    val teamId: String? = null,
    val regularCurrentPercent: Double,
    val regularFinalPercent: Double,
    val assignmentCurrentPercent: Double,
    val assignmentFinalPercent: Double,
    val regularCurrentModules: AbsenceFraction = AbsenceFraction(),
    val regularFinalModules: AbsenceFraction = AbsenceFraction(),
    val assignmentCurrentTime: AbsenceFraction = AbsenceFraction(),
    val assignmentFinalTime: AbsenceFraction = AbsenceFraction(),
)

data class AbsenceRegistration(
    val id: String,
    val date: LocalDate?,
    val team: String,
    val cause: String,
    val status: String,
    val week: String = "",
    val activityTitle: String = "",
    val percent: Double? = null,
    val registeredAt: LocalDateTime? = null,
    val note: String = "",
    val missingCause: Boolean = false,
)

data class AbsenceOverview(
    val teams: List<AbsenceTeamRow>,
    val registrations: List<AbsenceRegistration> = emptyList(),
    val attendanceAbsencePercent: Double? = null,
    val writtenAbsencePercent: Double? = null,
)

/**
 * Lectio cause dropdown strings — must match Flutter [AbsenceCauses] names.
 */
object AbsenceCauses {
    val all = listOf(
        "Sygdom",
        "Private forhold",
        "Skolerelaterede aktiviteter",
        "Kom for sent",
        "Andet",
    )
}
