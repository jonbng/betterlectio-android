package dk.betterlectio.android.feature.assignments

import org.junit.Assert.assertEquals
import org.junit.Test

class AssignmentParserTest {
    @Test
    fun parses_assignments_fixture() {
        val html = javaClass.classLoader!!
            .getResourceAsStream("lectio/fixtures/assignments_list.html")!!
            .bufferedReader().readText()
        val items = AssignmentParser.parseList(html)
        assertEquals(1, items.size)
        assertEquals("555", items[0].id)
        assertEquals("Rapport", items[0].title)
        assertEquals("Fy B", items[0].team)
        assertEquals("HE555", items[0].holdElementId)
        assertEquals(11, items[0].week)
        assertEquals(5.0, items[0].studentTime, 0.01)
        assertEquals("Venter", items[0].status)
        assertEquals("7", items[0].grade)
        assertEquals("Fin", items[0].gradeNote)
        assertEquals("OK", items[0].studentNote)
    }

    @Test
    fun parses_reordered_student_fields_and_duplicated_mobile_submission_cell() {
        val html = """
            <span id="m_Content_NameLbl">Skriveøvelse</span>
            <table id="m_Content_StudentGV">
              <tr><th>Elev</th><th>Elevnote</th><th>Karakter</th><th>Afventer</th><th>Karakternote</th><th>Status</th></tr>
              <tr>
                <td><span class="ls-elevaflevering-field-label">Elev:</span><span data-lectioContextCard="S7">Grace</span></td>
                <td><span class="ls-elevaflevering-field-label">Elevnote:</span></td>
                <td><span class="ls-elevaflevering-field-label">Karakter:</span><span class="ls-elevaflevering-field-value">4</span></td>
                <td><span class="ls-elevaflevering-field-label">Afventer:</span><span class="ls-elevaflevering-field-value">Lærer</span></td>
                <td><span class="ls-elevaflevering-field-label">Karakternote:</span></td>
                <td><span class="ls-elevaflevering-field-label">Status - fravær:</span><span class="ls-elevaflevering-field-value">Anden elev</span></td>
              </tr>
              <tr>
                <td><span class="ls-elevaflevering-field-label">Elev:</span><span data-lectioContextCard="S42">Ada</span></td>
                <td><span class="ls-elevaflevering-field-label">Elevnote:</span><span class="ls-elevaflevering-note-value">God struktur<br>Husk kilder</span></td>
                <td><span class="ls-elevaflevering-field-label">Karakter:</span><span class="ls-elevaflevering-field-value">10</span></td>
                <td><span class="ls-elevaflevering-field-label">Afventer:</span><span class="ls-elevaflevering-field-value">Elev</span></td>
                <td><span class="ls-elevaflevering-field-label">Karakternote:</span><span class="ls-elevaflevering-note-value">Flot analyse</span></td>
                <td><span class="ls-elevaflevering-field-label">Status - fravær:</span><span class="ls-elevaflevering-field-value">Afleveret / Fravær: 0%</span></td>
              </tr>
            </table>
            <table id="m_Content_RecipientGV">
              <tr><th></th><th>Tidspunkt</th><th>Bruger</th><th>Indlæg</th><th>Dokument</th></tr>
              <tr>
                <td class="OnlyMobile"><span class="ls-elevaflevering-entry-label">Bruger:</span><span class="ls-elevaflevering-entry-value">Lærer</span></td>
                <td class="OnlyDesktop">24/9-2026 10:15</td>
                <td class="OnlyDesktop"><span data-lectioContextCard="T99" title="Lise Lærer">LL</span></td>
                <td class="OnlyDesktop">Se kommentarer<br>i dokumentet</td>
                <td class="OnlyDesktop"><a href="/lectio/94/ExerciseFileGet.aspx?entryid=7">Rettet.pdf</a></td>
              </tr>
            </table>
        """.trimIndent()
        val item = AssignmentItem("1", "Fallback", "1x DA", 1, null, "", 0.0, "", "")

        val detail = AssignmentParser.parseDetail(html, item, requestedStudentId = "42")

        assertEquals("Elev", detail.item.awaits)
        assertEquals("Afleveret / Fravær: 0%", detail.item.status)
        assertEquals("10", detail.studentGrade)
        assertEquals("Flot analyse", detail.studentGradeNote)
        assertEquals("God struktur\nHusk kilder", detail.item.studentNote)
        assertEquals("24/9-2026 10:15", detail.submissions.single().timestamp)
        assertEquals("Lise Lærer", detail.submissions.single().user)
        assertEquals("Se kommentarer\ni dokumentet", detail.submissions.single().comment)
        assertEquals("Rettet.pdf", detail.submissions.single().documentName)
    }
}
