package dk.betterlectio.android.feature.grades

/**
 * Grade type filter matching iOS [GradeType] / Lectio columns.
 */
enum class GradeType {
    ALL,
    FIRST_STANDPOINT,
    SECOND_STANDPOINT,
    INTERNAL_EXAM,
    YEAR_GRADE,
    FINAL_EXAM,
}

data class GradeCell(
    val value: String,
    val weight: Double,
)

/**
 * Pure helpers for GPA-style averages and type filtering (unit-tested).
 */
object GradeAverage {

    fun cellFor(row: GradeRow, type: GradeType): GradeCell? {
        return when (type) {
            GradeType.ALL -> null
            GradeType.FIRST_STANDPOINT -> row.firstStandpunkt?.let {
                GradeCell(it, row.firstStandpunktWeight ?: 1.0)
            }
            GradeType.SECOND_STANDPOINT -> row.secondStandpunkt?.let {
                GradeCell(it, row.secondStandpunktWeight ?: 1.0)
            }
            GradeType.INTERNAL_EXAM -> row.internalTest?.let {
                GradeCell(it, row.internalTestWeight ?: 1.0)
            }
            GradeType.YEAR_GRADE -> {
                val raw = row.yearGrade ?: row.finalYear
                val w = row.yearGradeWeight ?: row.finalYearWeight ?: 1.0
                raw?.let { GradeCell(it, w) }
            }
            GradeType.FINAL_EXAM -> row.examGrade?.let {
                GradeCell(it, row.examGradeWeight ?: 1.0)
            }
        }
    }

    /** Rows that have a displayable cell for [type] (ALL = all rows). */
    fun filterRows(rows: List<GradeRow>, type: GradeType): List<GradeRow> {
        if (type == GradeType.ALL) return rows
        return rows.filter { cellFor(it, type) != null }
    }

    /**
     * Weighted average of numeric grade cells for [type].
     * ALL averages every numeric cell across all types (each with its weight).
     * Returns null when no numeric values; formatted with comma decimal for DA display.
     */
    fun weightedAverageDisplay(rows: List<GradeRow>, type: GradeType): String? {
        val avg = weightedAverage(rows, type) ?: return null
        return String.format(java.util.Locale.US, "%.2f", avg).replace('.', ',')
    }

    fun weightedAverage(rows: List<GradeRow>, type: GradeType): Double? {
        var sum = 0.0
        var weight = 0.0
        val types = if (type == GradeType.ALL) {
            GradeType.entries.filter { it != GradeType.ALL }
        } else {
            listOf(type)
        }
        for (row in rows) {
            for (t in types) {
                val cell = cellFor(row, t) ?: continue
                val n = parseNumeric(cell.value) ?: continue
                val w = cell.weight.coerceAtLeast(0.0)
                if (w <= 0.0) continue
                sum += n * w
                weight += w
            }
        }
        if (weight <= 0.0) return null
        return sum / weight
    }

    fun parseNumeric(raw: String): Double? {
        val normalized = raw.trim()
            .replace(',', '.')
            .replace(Regex("""[^\d.\-]"""), "")
        if (normalized.isEmpty() || normalized == "-" || normalized == ".") return null
        return normalized.toDoubleOrNull()
    }

    /** Display string for a row under the active type filter. */
    fun displayGrade(row: GradeRow, type: GradeType): String {
        if (type == GradeType.ALL) return row.gradeSummary.ifBlank { "—" }
        return cellFor(row, type)?.value?.ifBlank { "—" } ?: "—"
    }
}
