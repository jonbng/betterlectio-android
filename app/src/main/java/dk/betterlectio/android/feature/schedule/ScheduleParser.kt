package dk.betterlectio.android.feature.schedule

import dk.betterlectio.android.core.util.LectioDateUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure HTML → schedule models.
 * iOS parity: ScheduleParser (table.s2skema / data-date / s2skemabrik)
 * Flutter parity: weeks/scraping.extractModul
 */
object ScheduleParser {

    private val dateAttrFmt = DateTimeFormatter.ofPattern("d/M-yyyy", Locale.ROOT)

    fun parseWeek(html: String, year: Int, week: Int): ScheduleWeek {
        val doc = Jsoup.parse(html)
        val table = doc.selectFirst("table.s2skema")
            ?: return ScheduleWeek(year, week, emptyList())

        val dayColumns = table.select("td[data-date]")
        if (dayColumns.isEmpty()) {
            return parseLegacyWeek(html, year, week)
        }

        val days = mutableListOf<ScheduleDay>()
        val dateByCol = mutableMapOf<Int, LocalDate>()

        for (col in dayColumns) {
            val dateStr = col.attr("data-date")
            val date = parseDataDate(dateStr) ?: continue
            dateByCol[col.elementSiblingIndex()] = date
            val events = col.select("a.s2skemabrik.s2brik, a.s2skemabrik.s2bgbox, a.s2bgbox")
                .mapNotNull { parseBrick(it, date) }
            days += ScheduleDay(date = date, events = events)
        }

        // All-day info row
        dayColumns.firstOrNull()?.parent()?.previousElementSibling()?.let { infoRow ->
            for (cell in infoRow.children()) {
                if (!cell.hasClass("s2infoHeader")) continue
                val date = dateByCol[cell.elementSiblingIndex()] ?: continue
                val allDay = cell.select("a.s2skemabrik").mapNotNull { brick ->
                    val tip = brick.attr("data-tooltip")
                    if (!tip.contains("Hele dagen", ignoreCase = true)) null
                    else parseBrick(brick, date)?.copy(isAllDay = true)
                }
                if (allDay.isNotEmpty()) {
                    val idx = days.indexOfFirst { it.date == date }
                    if (idx >= 0) {
                        days[idx] = days[idx].copy(events = allDay + days[idx].events)
                    }
                }
            }
        }

        return ScheduleWeek(year, week, days.sortedBy { it.date })
    }

    /** Flutter-style tbody fallback when data-date columns missing. */
    private fun parseLegacyWeek(html: String, year: Int, week: Int): ScheduleWeek {
        val doc = Jsoup.parse(html)
        val containers = doc.select("div.s2skemabrikcontainer")
        if (containers.size < 2) return ScheduleWeek(year, week, emptyList())

        val titles = doc.select("tr.s2dayHeader td").drop(1).map { it.text() }
        val dayContainers = containers.drop(1)
        val days = mutableListOf<ScheduleDay>()
        for (i in dayContainers.indices) {
            val title = titles.getOrNull(i).orEmpty()
            val dm = Regex("""(\d{1,2})/(\d{1,2})""").find(title)
            val date = if (dm != null) {
                runCatching {
                    LocalDate.of(year, dm.groupValues[2].toInt(), dm.groupValues[1].toInt())
                }.getOrElse {
                    LectioDateUtils.weekStart(year, week).plusDays(i.toLong())
                }
            } else {
                LectioDateUtils.weekStart(year, week).plusDays(i.toLong())
            }
            val events = dayContainers[i].select("a.s2bgbox, a.s2skemabrik")
                .mapNotNull { parseBrick(it, date) }
            days += ScheduleDay(date, events)
        }
        return ScheduleWeek(year, week, days)
    }

    fun parseBrick(brick: Element, date: LocalDate): ScheduleEvent? {
        val tooltip = brick.attr("data-tooltip")
        val href = brick.attr("href")
        val brikId = brick.attr("data-brikid").ifBlank {
            extractIdFromHref(href) ?: ("AD" + (date.toString() + tooltip).hashCode().toUInt().toString(16))
        }

        val status = when {
            brick.hasClass("s2cancelled") || tooltip.contains("Aflyst") -> EventStatus.CANCELLED
            brick.hasClass("s2changed") || tooltip.contains("Ændret") -> EventStatus.CHANGED
            else -> EventStatus.NORMAL
        }

        val lines = tooltip.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var team = ""
        var teacher: String? = null
        var room: String? = null
        var notes: String? = null
        var homework: String? = null
        var title = brick.text().trim().ifBlank { "" }
        var start: LocalDateTime? = null
        var end: LocalDateTime? = null
        var isAllDay = tooltip.contains("Hele dagen", ignoreCase = true)

        for (line in lines) {
            when {
                line.startsWith("Hold:", ignoreCase = true) -> {
                    team = line.substringAfter(':').trim()
                    if (title.isBlank()) title = team
                }
                line.startsWith("Lærer:", ignoreCase = true) || line.startsWith("Lærere:", ignoreCase = true) ->
                    teacher = line.substringAfter(':').trim()
                line.startsWith("Lokale:", ignoreCase = true) || line.startsWith("Lokaler:", ignoreCase = true) ->
                    room = line.substringAfter(':').trim()
                line.startsWith("Note:", ignoreCase = true) || line.startsWith("Øvrigt", ignoreCase = true) ->
                    notes = line.substringAfter(':').trim().ifBlank { line }
                line.startsWith("Lektier:", ignoreCase = true) ->
                    homework = line.substringAfter(':').trim()
                line.contains("Hele dagen", ignoreCase = true) -> isAllDay = true
                else -> {
                    val range = LectioDateUtils.parseTimeRange(line)
                    if (range != null && start == null) {
                        start = LocalDateTime.of(date, range.first)
                        end = LocalDateTime.of(date, range.second)
                    } else if (
                        title.isBlank() &&
                        !line.contains("Ændret") &&
                        !line.contains("Aflyst") &&
                        ':' !in line &&
                        !line.matches(Regex(""".*\d{1,2}/\d{1,2}.*"""))
                    ) {
                        title = line
                    }
                }
            }
        }

        if (title.isBlank()) title = team.ifBlank { "Modul" }

        return ScheduleEvent(
            id = brikId,
            title = title,
            team = team,
            teacher = teacher,
            room = room,
            status = status,
            start = start,
            end = end,
            date = date,
            notes = notes,
            homework = homework,
            isAllDay = isAllDay,
            href = href.ifBlank { null },
        )
    }

    private fun parseDataDate(raw: String): LocalDate? {
        // formats: "2/2-2026" or ISO-ish
        return try {
            LocalDate.parse(raw, dateAttrFmt)
        } catch (_: Exception) {
            try {
                LocalDate.parse(raw.take(10))
            } catch (_: Exception) {
                val m = Regex("""(\d{1,2})/(\d{1,2})-(\d{4})""").find(raw) ?: return null
                LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
            }
        }
    }

    private fun extractIdFromHref(href: String): String? {
        Regex("""absid=(\d+)""").find(href)?.let { return "ABS${it.groupValues[1]}" }
        Regex("""aftaleid=(\d+)""").find(href)?.let { return "AFT${it.groupValues[1]}" }
        Regex("""ProeveholdId=(\d+)""").find(href)?.let { return "PRV${it.groupValues[1]}" }
        return null
    }
}
