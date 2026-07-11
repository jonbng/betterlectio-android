package dk.betterlectio.android.feature.grades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeAverageTest {

    private fun row(
        subject: String,
        first: String? = null,
        firstW: Double? = null,
        second: String? = null,
        secondW: Double? = null,
        internal: String? = null,
        year: String? = null,
        exam: String? = null,
        examW: Double? = null,
    ) = GradeRow(
        team = "1x",
        subject = subject,
        subjectType = null,
        firstStandpunkt = first,
        secondStandpunkt = second,
        finalYear = null,
        internalTest = internal,
        yearGrade = year,
        examGrade = exam,
        firstStandpunktWeight = firstW,
        secondStandpunktWeight = secondW,
        examGradeWeight = examW,
    )

    @Test
    fun weightedAverage_firstStandpoint_usesWeights() {
        val rows = listOf(
            row("Ma", first = "10", firstW = 2.0),
            row("Da", first = "7", firstW = 1.0),
        )
        // (10*2 + 7*1) / 3 = 9.0
        assertEquals(9.0, GradeAverage.weightedAverage(rows, GradeType.FIRST_STANDPOINT)!!, 0.001)
        assertEquals("9,00", GradeAverage.weightedAverageDisplay(rows, GradeType.FIRST_STANDPOINT))
    }

    @Test
    fun weightedAverage_all_aggregatesEveryNumericCell() {
        val rows = listOf(
            row("Ma", first = "10", firstW = 1.0, exam = "4", examW = 1.0),
            row("Da", first = "7", firstW = 1.0),
        )
        // (10 + 4 + 7) / 3 = 7.0
        assertEquals(7.0, GradeAverage.weightedAverage(rows, GradeType.ALL)!!, 0.001)
    }

    @Test
    fun filterRows_onlyIncludesRowsWithSelectedType() {
        val rows = listOf(
            row("Ma", first = "10"),
            row("Da", exam = "7"),
            row("En", first = "4", exam = "10"),
        )
        val firstOnly = GradeAverage.filterRows(rows, GradeType.FIRST_STANDPOINT)
        assertEquals(2, firstOnly.size)
        assertTrue(firstOnly.any { it.subject == "Ma" })
        assertTrue(firstOnly.any { it.subject == "En" })

        val examOnly = GradeAverage.filterRows(rows, GradeType.FINAL_EXAM)
        assertEquals(2, examOnly.size)
    }

    @Test
    fun parseNumeric_acceptsCommaDecimal() {
        assertEquals(7.5, GradeAverage.parseNumeric("7,5")!!, 0.001)
        assertNull(GradeAverage.parseNumeric("—"))
        assertNull(GradeAverage.parseNumeric(""))
    }

    @Test
    fun displayGrade_forType() {
        val r = row("Ma", first = "10", exam = "7")
        assertEquals("10", GradeAverage.displayGrade(r, GradeType.FIRST_STANDPOINT))
        assertEquals("7", GradeAverage.displayGrade(r, GradeType.FINAL_EXAM))
        assertTrue(GradeAverage.displayGrade(r, GradeType.ALL).contains("10"))
    }
}
