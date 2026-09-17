package dk.betterlectio.android.feature.homework

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeworkParserTest {
    @Test
    fun parses_homework_fixture() {
        val html = javaClass.classLoader!!
            .getResourceAsStream("lectio/fixtures/homework_list.html")!!
            .bufferedReader().readText()
        val items = HomeworkParser.parse(html)
        assertEquals(1, items.size)
        assertTrue(items[0].activityTitle.contains("Matematik"))
        assertEquals("Læs kapitel 4", items[0].note)
        assertEquals("999", items[0].id)
        assertEquals("Ma A", items[0].team)
        assertEquals(java.time.LocalDate.of(2026, 3, 11), items[0].date)
        // Detail load requires a usable activity link (absid path).
        assertTrue(
            "href must carry absid for detail scrape",
            items[0].href != null && items[0].href!!.contains("absid=999"),
        )
    }

    @Test
    fun preserves_text_only_tasks_alongside_linked_files() {
        val html = """
            <table id="s_m_Content_Content_MaterialLektieOverblikGV">
              <tr><th>Dato</th><th>Aktivitet</th><th>Note &amp; Lektier</th></tr>
              <tr>
                <td class="OnlyDesktop">fr 18/9</td>
                <td class="OnlyDesktop">
                  <a class="s2skemabrik" data-brikid="ABS80296232213"
                     href="/lectio/680/aktivitet/aktivitetforside2.aspx?absid=80296232213"
                     data-tooltip="Mængdeberegninger-2&#10;Hold: BShannon nv ke">Modul</a>
                </td>
                <td class="OnlyDesktop">
                  Mængdeberegninger-2<br><br>
                  <img src="/lectio/img/doc-homework.auto">basis kemi C: side 89-98<br>
                  <a href="/lectio/680/lc/file.pptx"><img src="/lectio/img/doc-homework.auto">PP-2.pptx</a>
                </td>
              </tr>
            </table>
        """.trimIndent()

        val item = HomeworkParser.parse(html).single()

        assertEquals("Mængdeberegninger-2", item.note)
        assertEquals(listOf("basis kemi C: side 89-98"), item.textTasks.map { it.text })
        assertEquals(listOf("PP-2.pptx"), item.linkedTasks.map { it.text })
    }

    @Test
    fun task_only_homework_does_not_duplicate_tasks_into_note() {
        val html = """
            <table id="s_m_Content_Content_MaterialLektieOverblikGV">
              <tr><th>Dato</th><th>Aktivitet</th><th>Note &amp; Lektier</th></tr>
              <tr>
                <td class="OnlyDesktop">ti 22/9</td>
                <td class="OnlyDesktop">
                  <a class="s2skemabrik" data-brikid="ABS42"
                     href="/lectio/680/aktivitet/aktivitetforside2.aspx?absid=42">Kemi</a>
                </td>
                <td class="OnlyDesktop"><img src="/lectio/img/doc-homework.auto">Læs side 89-98</td>
              </tr>
            </table>
        """.trimIndent()

        val item = HomeworkParser.parse(html).single()

        assertEquals("", item.note)
        assertEquals(listOf("Læs side 89-98"), item.textTasks.map { it.text })
    }
}
