package dk.betterlectio.android.feature.absence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AbsenceParserTest {
    @Test
    fun parses_registration_rows_fallback_table() {
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

    @Test
    fun parses_fatab_registration_with_edit_id() {
        val html = """
            <table id="s_m_Content_Content_FatabAbsenceFravaerGV">
              <tr><th>Uge</th><th>Aktivitet</th><th>%</th><th>Type</th><th>Reg</th><th>By</th><th>Årsag</th><th>Edit</th></tr>
              <tr>
                <td>11</td>
                <td><a class="s2skemabrik" href="?absid=9">Ma A</a></td>
                <td>100%</td>
                <td>Fravær</td>
                <td>01/07-2026 10:00 xx</td>
                <td>lærer</td>
                <td>Sygdom
note her</td>
                <td><a href="fravaer_aarsag.aspx?id=7788&amp;atype=aa">ret</a></td>
              </tr>
            </table>
        """.trimIndent()
        val regs = AbsenceParser.parseRegistrations(html)
        assertEquals(1, regs.size)
        assertEquals("7788", regs[0].id)
        assertEquals("Sygdom", regs[0].cause)
        assertEquals("Fravær", regs[0].status)
    }

    @Test
    fun overview_parses_percent_and_team() {
        val html = """
            <table id="s_m_Content_Content_SFTabStudentAbsenceDataTable">
              <tr><th></th></tr><tr><th></th></tr><tr><th></th></tr>
              <tr>
                <td><a href="?holdelementid=123">Ma A</a></td>
                <td>5%</td><td>1/20</td>
                <td>10%</td><td>2/20</td>
                <td>0%</td><td>0/0</td>
                <td>0%</td><td>0/0</td>
              </tr>
              <tr><td>Total</td></tr>
            </table>
        """.trimIndent()
        val teams = AbsenceParser.parseOverview(html)
        assertEquals(1, teams.size)
        assertEquals("Ma A", teams[0].team)
        assertEquals("HE123", teams[0].teamId)
        assertEquals(0.05, teams[0].regularCurrentPercent, 0.001)
    }
}
