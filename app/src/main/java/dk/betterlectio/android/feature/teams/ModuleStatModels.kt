package dk.betterlectio.android.feature.teams

data class ModuleStat(
    val holdElementId: String,
    val team: String,
    val teachingHeld: Double?,
    val teachingPlanned: Double?,
    val otherHeld: Double?,
    val otherPlanned: Double?,
    val total: Double?,
    val norm: Double?,
    val deviation: String,
)
