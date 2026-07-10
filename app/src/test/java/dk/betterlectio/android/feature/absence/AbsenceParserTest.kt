package dk.betterlectio.android.feature.absence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AbsenceParserTest {
    @Test
    fun parses_registration_rows() {
        val html = """
            <table>
              <tr><th>Dato</th><th>Hold</th><th>Årsag</th><th>Status</th></tr>
              <tr><td>01/07-2026</td><td>Fy B</td><td>Syg</td><td>Godkendt</td></tr>
            </table>
        """.trimIndent()
        val regs = AbsenceParser.parseRegistrations(html)
        assertEquals(1, regs.size)
        assertEquals("Fy B", regs[0].team)
        assertEquals("Syg", regs[0].cause)
        assertTrue(regs[0].status.contains("Godkendt"))
    }
}
