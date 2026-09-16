package dk.betterlectio.android.feature.teams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModuleStatParserTest {
    @Test
    fun parses_unique_holds_from_study_plan() {
        val html = """
            <a href="studieplan.aspx?displaytype=ugeteksttabel&amp;holdelementid=7301">2v Ma</a>
            <a href="/lectio/94/subnav/modulregnskab.aspx?holdelementid=7301">duplicate</a>
            <a href="studieplan.aspx?holdelementid=7302">2v Da</a>
        """.trimIndent()

        assertEquals(
            listOf(ModuleHold("7301", "2v Ma"), ModuleHold("7302", "2v Da")),
            ModuleStatParser.parseHoldList(html),
        )
    }

    @Test
    fun parses_hold_total_from_module_account_table() {
        val html = """
            <table id="s_m_Content_Content_afholdtelektionertbl">
              <tr><th>Holdelement</th><th>Afholdt</th></tr>
              <tr><td>2x Ke</td><td>9</td><td>67</td><td>0</td><td>1,5</td><td>77,5</td><td>65</td><td>19,2%</td></tr>
              <tr><td><div class="IndentedBlock">Teacher</div></td><td>9</td><td>67</td><td>0</td><td>0</td><td>76</td><td>65</td><td></td></tr>
            </table>
        """.trimIndent()

        val result = ModuleStatParser.parseReport(html, ModuleHold("7301", "fallback"))!!
        assertEquals("7301", result.holdElementId)
        assertEquals("2x Ke", result.team)
        assertEquals(9.0, result.teachingHeld!!, 0.001)
        assertEquals(67.0, result.teachingPlanned!!, 0.001)
        assertEquals(1.5, result.otherPlanned!!, 0.001)
        assertEquals(77.5, result.total!!, 0.001)
        assertEquals(65.0, result.norm!!, 0.001)
        assertEquals("19,2%", result.deviation)
    }

    @Test
    fun returns_null_without_module_account_table() {
        assertNull(ModuleStatParser.parseReport("<html></html>", ModuleHold("1", "Ma")))
    }
}
