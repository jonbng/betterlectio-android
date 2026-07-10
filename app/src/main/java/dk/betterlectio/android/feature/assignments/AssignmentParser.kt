package dk.betterlectio.android.feature.assignments

import dk.betterlectio.android.core.lectio.scrape.AspNetForm
import dk.betterlectio.android.core.util.LectioDateUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object AssignmentParser {
    fun parseList(html: String): List<AssignmentItem> {
        val doc = Jsoup.parse(html)
        val table = doc.selectFirst("table") ?: return emptyList()
        val rows = table.select("tr").drop(1)
        return rows.mapNotNull { parseRow(it) }
    }

    private fun parseRow(row: Element): AssignmentItem? {
        val cols = row.select("td")
        if (cols.size < 6) return null
        val week = cols[0].text().trim().toIntOrNull() ?: 0
        val team = cols[1].text().trim()
        val link = cols[2].selectFirst("a") ?: return null
        val href = link.attr("href")
        val id = AspNetForm.queriesFromUrl(href)["exerciseid"]
            ?: Regex("""exerciseid=(\d+)""").find(href)?.groupValues?.get(1)
            ?: return null
        val title = link.text().trim()
        val deadline = LectioDateUtils.parseLectioDate(cols[3].text().trim())
        val studentTime = cols.getOrNull(4)?.text()?.replace(',', '.')?.trim()?.toDoubleOrNull() ?: 0.0
        val status = cols.getOrNull(5)?.text()?.trim().orEmpty()
        val absence = cols.getOrNull(6)?.text()?.trim().orEmpty()
        val awaits = cols.getOrNull(7)?.text()?.trim().orEmpty()
        val note = cols.getOrNull(8)?.text()?.trim().orEmpty()
        return AssignmentItem(
            id = id,
            title = title,
            team = team,
            week = week,
            deadline = deadline,
            status = status,
            studentTime = studentTime,
            awaits = awaits,
            note = note,
            absence = absence,
        )
    }

    fun parseDetail(html: String, item: AssignmentItem): AssignmentDetail {
        val doc = Jsoup.parse(html)
        val title = doc.getElementById("m_Content_NameLbl")?.text()?.trim().orEmpty().ifBlank { item.title }
        val grading = doc.getElementById("m_Content_gradeScaleIdLbl")?.text()?.trim().orEmpty()
        val weight = doc.getElementById("m_Content_WeightLbl")?.text()?.trim().orEmpty()
        val description = doc.selectFirst("#m_Content_DescriptionPnl, .exercise-description, #m_Content_registerAfl_pa")
            ?.text()?.trim().orEmpty().ifBlank { item.note }
        val files = doc.select("#m_Content_ExerciseFilePnl a, a[href*=GetFile], a[href*=documentid]").map {
            val name = it.text().trim().ifBlank { "Fil" }
            val href = it.attr("href")
            val url = if (href.startsWith("http")) href else "https://www.lectio.dk$href"
            name to url
        }
        val responsible = doc.selectFirst("[data-lectiocontextcard^=T]")?.text()?.trim().orEmpty()
        return AssignmentDetail(
            item = item.copy(title = title, studentTime = weight.filter { it.isDigit() || it == ',' || it == '.' }
                .replace(',', '.').toDoubleOrNull() ?: item.studentTime),
            description = description,
            files = files,
            responsible = responsible,
            grading = grading,
        )
    }
}
