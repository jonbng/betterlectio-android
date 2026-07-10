package dk.betterlectio.android.feature.absence

import dk.betterlectio.android.core.util.LectioDateUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object AbsenceParser {
    fun parseOverview(html: String): List<AbsenceTeamRow> {
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("s_m_Content_Content_SFTabStudentAbsenceDataTable")
            ?: doc.selectFirst("table[id*=Absence]")
            ?: return emptyList()
        val rows = table.select("tr")
        // skip header rows (first ~3) and last total
        if (rows.size <= 4) return emptyList()
        return rows.drop(3).dropLast(1).mapNotNull { parseTeamRow(it) }
    }

    private fun parseTeamRow(row: Element): AbsenceTeamRow? {
        val cells = row.select("td")
        if (cells.size < 5) return null
        val team = cells[0].text().trim()
        if (team.isBlank()) return null
        fun pct(i: Int): Double {
            val t = cells.getOrNull(i)?.text()?.replace("%", "")?.replace(",", ".")?.trim().orEmpty()
            return t.toDoubleOrNull()?.div(100.0) ?: 0.0
        }
        return AbsenceTeamRow(
            team = team,
            regularCurrentPercent = pct(1),
            regularFinalPercent = pct(3),
            assignmentCurrentPercent = pct(5),
            assignmentFinalPercent = pct(7),
        )
    }

    fun parseRegistrations(html: String): List<AbsenceRegistration> {
        val doc = Jsoup.parse(html)
        val table = doc.selectFirst("table[id*=Absence], table[id*=fravaer], table")
            ?: return emptyList()
        return table.select("tr").drop(1).mapIndexedNotNull { i, row ->
            val cells = row.select("td")
            if (cells.size < 3) return@mapIndexedNotNull null
            AbsenceRegistration(
                id = "reg-$i",
                date = LectioDateUtils.parseLectioDate(cells[0].text())?.toLocalDate(),
                team = cells.getOrNull(1)?.text()?.trim().orEmpty(),
                cause = cells.getOrNull(2)?.text()?.trim().orEmpty(),
                status = cells.getOrNull(3)?.text()?.trim().orEmpty(),
            )
        }
    }
}
