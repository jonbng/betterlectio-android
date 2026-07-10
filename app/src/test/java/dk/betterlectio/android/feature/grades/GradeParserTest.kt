package dk.betterlectio.android.feature.grades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeParserTest {
    @Test
    fun parses_grades_table_fixture() {
        val html = javaClass.classLoader!!
            .getResourceAsStream("lectio/fixtures/grades_table.html")!!
            .bufferedReader().readText()
        val rows = GradeParser.parse(html)
        assertEquals(1, rows.size)
        assertTrue(rows[0].subject.contains("Matematik"))
        assertEquals("10", rows[0].firstStandpunkt)
        assertEquals("12", rows[0].secondStandpunkt)
    }
}
