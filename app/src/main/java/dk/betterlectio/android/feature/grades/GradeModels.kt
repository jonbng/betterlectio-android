package dk.betterlectio.android.feature.grades

data class GradeRow(
    val team: String,
    val subject: String,
    val subjectType: String?,
    val firstStandpunkt: String?,
    val secondStandpunkt: String?,
    val finalYear: String?,
    val internalTest: String?,
    val yearGrade: String?,
    val examGrade: String?,
    val notes: List<String> = emptyList(),
    val teamId: String? = null,
    val firstStandpunktWeight: Double? = null,
    val secondStandpunktWeight: Double? = null,
    val finalYearWeight: Double? = null,
    val internalTestWeight: Double? = null,
    val yearGradeWeight: Double? = null,
    val examGradeWeight: Double? = null,
) {
    val gradeSummary: String
        get() = listOfNotNull(
            firstStandpunkt,
            secondStandpunkt,
            finalYear,
            yearGrade,
            examGrade,
        ).joinToString(" · ")
}

data class GradeSubjectDetail(
    val row: GradeRow,
    val notes: List<String>,
)
