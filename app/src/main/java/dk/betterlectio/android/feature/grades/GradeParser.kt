package dk.betterlectio.android.feature.grades

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object GradeParser {
    fun parse(html: String): List<GradeRow> {
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("s_m_Content_Content_karakterView_KarakterGV")
            ?: doc.selectFirst("table[id*=Karakter]")
            ?: return emptyList()
        return table.select("tr").drop(1).mapNotNull { parseRow(it) }
    }

    fun parseNotes(html: String): List<String> {
        val doc = Jsoup.parse(html)
        return doc.select("table tr, .grade-note, [id*=Note] li, [id*=note] p")
            .map { it.text().trim() }
            .filter { it.length in 3..300 && !it.equals("note", true) }
            .distinct()
            .take(50)
    }

    private fun parseRow(row: Element): GradeRow? {
        val cells = row.select("td")
        if (cells.size < 3) return null
        val team = cells[0].text().trim()
        val subjectParts = cells[1].text().split(",").map { it.trim() }
        fun cell(i: Int): String? = cells.getOrNull(i)?.text()?.trim()?.takeIf { it.isNotEmpty() && it != "--" }
        return GradeRow(
            team = team,
            subject = subjectParts.firstOrNull().orEmpty().ifBlank { team },
            subjectType = subjectParts.getOrNull(1),
            firstStandpunkt = cell(2),
            secondStandpunkt = cell(3),
            finalYear = cell(4),
            internalTest = cell(5),
            yearGrade = cell(6),
            examGrade = cell(7),
        )
    }
}
