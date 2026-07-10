package dk.betterlectio.android.feature.absence

import java.time.LocalDate

data class AbsenceTeamRow(
    val team: String,
    val regularCurrentPercent: Double,
    val regularFinalPercent: Double,
    val assignmentCurrentPercent: Double,
    val assignmentFinalPercent: Double,
)

data class AbsenceRegistration(
    val id: String,
    val date: LocalDate?,
    val team: String,
    val cause: String,
    val status: String,
)

data class AbsenceOverview(
    val teams: List<AbsenceTeamRow>,
    val registrations: List<AbsenceRegistration> = emptyList(),
)

object AbsenceCauses {
    val all = listOf(
        "Syg",
        "Privat",
        "Skolerelateret",
        "Forsinkelse",
        "Andet",
    )
}
