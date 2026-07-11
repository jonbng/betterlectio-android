package dk.betterlectio.android.feature.grades

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Grades from `grade_report.aspx` / KarakterGV.
 * Flutter column layout + iOS OnlyDesktop cells + weight from title.
 */
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
        // Flutter/iOS structured notes grid
        val grid = doc.getElementById("s_m_Content_Content_karakterView_KarakterNoterGrid")
            ?: doc.selectFirst("table[id*=KarakterNoter], table[id*=NoterGrid]")
        if (grid != null) {
            val structured = grid.select("tr").drop(1).mapNotNull { row ->
                val cells = desktopCells(row)
                if (cells.isEmpty()) return@mapNotNull null
                cells.joinToString(" · ") { it.text().trim() }
                    .takeIf { it.length in 3..400 }
            }
            if (structured.isNotEmpty()) return structured.distinct().take(50)
        }
        return doc.select("table tr, .grade-note, [id*=Note] li, [id*=note] p")
            .map { it.text().trim() }
            .filter { it.length in 3..300 && !it.equals("note", true) }
            .distinct()
            .take(50)
    }

    private fun parseRow(row: Element): GradeRow? {
        val cells = desktopCells(row)
        if (cells.size < 3) return null
        val teamCell = cells[0]
        val teamSpan = teamCell.selectFirst("[data-lectiocontextcard]")
        val team = teamSpan?.text()?.trim() ?: teamCell.text().trim()
        val teamId = teamSpan?.attr("data-lectiocontextcard")?.ifBlank { null }
        // Flutter: split ", " for subject + type
        val subjectParts = cells[1].text().split(", ").map { it.trim() }
            .ifEmpty { cells[1].text().split(",").map { it.trim() } }
        fun cell(i: Int): String? {
            val el = cells.getOrNull(i) ?: return null
            return extractGradeText(el)
        }
        fun weight(i: Int): Double? = extractGradeWeight(cells.getOrNull(i))
        return GradeRow(
            team = team,
            teamId = teamId,
            subject = subjectParts.firstOrNull().orEmpty().ifBlank { team },
            subjectType = subjectParts.getOrNull(1),
            firstStandpunkt = cell(2),
            secondStandpunkt = cell(3),
            finalYear = cell(4),
            internalTest = cell(5),
            yearGrade = cell(6),
            examGrade = cell(7),
            firstStandpunktWeight = weight(2),
            secondStandpunktWeight = weight(3),
            finalYearWeight = weight(4),
            internalTestWeight = weight(5),
            yearGradeWeight = weight(6),
            examGradeWeight = weight(7),
        )
    }

    private fun extractGradeText(cell: Element): String? {
        val child = cell.children().firstOrNull() ?: cell
        val text = child.text().trim()
        if (text.isEmpty() || text == "--") return null
        // Flutter takes first 1–2 chars as int grade when possible
        return text
    }

    private fun extractGradeWeight(cell: Element?): Double? {
        if (cell == null) return null
        val title = cell.children().firstOrNull()?.attr("title")
            ?: cell.attr("title")
        if (title.isBlank()) return null
        // Flutter: line with "Vægt: x"
        title.lineSequence().forEach { line ->
            if (line.contains("Vægt", ignoreCase = true) || line.contains("Weight", ignoreCase = true)) {
                val num = line.substringAfter(':').trim().replace(",", ".")
                return num.toDoubleOrNull()
            }
        }
        return null
    }

    private fun desktopCells(row: Element): List<Element> {
        val desktop = row.select("td.OnlyDesktop")
        if (desktop.isNotEmpty()) return desktop
        val nonMobile = row.select("td:not(.OnlyMobile)")
        if (nonMobile.isNotEmpty()) return nonMobile
        return row.select("td")
    }
}
