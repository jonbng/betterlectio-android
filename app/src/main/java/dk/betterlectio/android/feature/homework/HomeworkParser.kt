package dk.betterlectio.android.feature.homework

import dk.betterlectio.android.core.util.LectioDateUtils
import org.jsoup.Jsoup
import java.time.LocalDate

object HomeworkParser {
    fun parse(html: String): List<HomeworkItem> {
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("s_m_Content_Content_MaterialLektieOverblikGV")
            ?: doc.selectFirst("table[id*=Lektie]")
            ?: return emptyList()
        val rows = table.select("tr").drop(1)
        return rows.mapIndexedNotNull { index, row ->
            val cells = row.select("td")
            if (cells.isEmpty()) return@mapIndexedNotNull null
            val date = LectioDateUtils.parseLectioDate(cells[0].text())?.toLocalDate()
            val activity = row.selectFirst("a.s2skemabrik, a.s2bgbox")
            val title = activity?.text()?.trim()
                ?: cells.getOrNull(1)?.text()?.trim().orEmpty()
            val note = cells.getOrNull(2)?.text()?.trim().orEmpty()
            val href = activity?.attr("href")?.ifBlank { null }
            val id = href?.let { h ->
                Regex("""absid=(\d+)""").find(h)?.groupValues?.get(1)
            } ?: "hw-$index-${title.hashCode()}"
            if (title.isBlank() && note.isBlank()) return@mapIndexedNotNull null
            HomeworkItem(
                id = id,
                note = note,
                activityTitle = title.ifBlank { "Lektie" },
                date = date,
                team = "",
                href = href,
            )
        }
    }
}
