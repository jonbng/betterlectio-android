package dk.betterlectio.android.feature.assignments

import dk.betterlectio.android.core.lectio.scrape.AspNetForm
import dk.betterlectio.android.core.util.LectioDateUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Assignments list + detail.
 * List: iOS [AssignmentParser] (`ExerciseGV` + OnlyDesktop) with Flutter column layout.
 * Detail: iOS label-based info table + RecipientGV + StudentGV.
 */
object AssignmentParser {
    fun parseList(html: String): List<AssignmentItem> {
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("s_m_Content_Content_ExerciseGV")
            ?: doc.selectFirst("table[id*=ExerciseGV]")
            ?: doc.selectFirst("table")
            ?: return emptyList()
        return table.select("tr").mapNotNull { row ->
            if (row.select("th").isNotEmpty()) return@mapNotNull null
            parseRow(row)
        }
    }

    private fun parseRow(row: Element): AssignmentItem? {
        val cols = desktopCells(row)
        if (cols.size < 6) return null
        val week = cols[0].text().trim().toIntOrNull() ?: 0
        val holdSpan = cols[1].selectFirst("span[data-lectiocontextcard]")
        val team = holdSpan?.text()?.trim() ?: cols[1].text().trim()
        val holdElementId = holdSpan?.attr("data-lectiocontextcard")?.ifBlank { null }
        val link = cols[2].selectFirst("a") ?: return null
        val href = link.attr("href")
        val id = AspNetForm.queriesFromUrl(href)["exerciseid"]
            ?: Regex("""exerciseid=(\d+)""").find(href)?.groupValues?.get(1)
            ?: return null
        val title = link.text().trim()
        val deadline = LectioDateUtils.parseLectioDate(cols[3].text().trim())
        val studentTime = cols.getOrNull(4)?.text()?.replace(',', '.')?.trim()?.toDoubleOrNull() ?: 0.0
        val statusCell = cols.getOrNull(5)
        val statusText = statusCell?.text()?.trim().orEmpty()
        val status = when {
            statusCell?.selectFirst("span.exercisewait") != null -> "Venter"
            statusText.equals("Venter", true) -> "Venter"
            statusText.equals("Afventer", true) -> "Venter"
            else -> statusText
        }
        val absence = cols.getOrNull(6)?.text()?.trim().orEmpty()
        val awaits = cols.getOrNull(7)?.text()?.trim().orEmpty()
        val note = cols.getOrNull(8)?.text()?.trim().orEmpty()
        var grade: String? = null
        var gradeNote: String? = null
        cols.getOrNull(9)?.let { gradeCell ->
            val parts = gradeCell.html()
                .split(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE))
                .map { Jsoup.parse(it).text().trim() }
                .filter { it.isNotEmpty() }
            grade = parts.getOrNull(0)
            gradeNote = parts.getOrNull(1)
        }
        val studentNote = cols.getOrNull(10)?.text()?.trim()?.ifBlank { null }
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
            grade = grade,
            gradeNote = gradeNote,
            studentNote = studentNote,
            holdElementId = holdElementId,
            detailUrl = href.ifBlank { null },
        )
    }

    fun parseDetail(
        html: String,
        item: AssignmentItem,
        requestedStudentId: String? = null,
    ): AssignmentDetail {
        val doc = Jsoup.parse(html)
        val title = doc.getElementById("m_Content_NameLbl")?.text()?.trim().orEmpty().ifBlank { item.title }

        val infoSection = doc.selectFirst("#m_Content_registerAfl_pa")
        var hold = item.team
        var grading = doc.getElementById("m_Content_gradeScaleIdLbl")?.text()?.trim().orEmpty()
        var responsible = ""
        var studentTime = item.studentTime
        var description = item.note
        val files = mutableListOf<Pair<String, String>>()

        if (infoSection != null) {
            // Description files — iOS: a[id*=showdocumentHyperlnk]
            infoSection.select("a[id*=showdocumentHyperlnk], #m_Content_ExerciseFilePnl a").forEach { a ->
                val href = a.attr("href").trim()
                if (href.isEmpty()) return@forEach
                val name = a.text().trim().ifBlank { "Fil" }
                val url = absolutize(href)
                files += name to url
            }

            for (row in infoSection.select("table.ls-std-table-inputlist tr, table tr")) {
                val th = row.selectFirst("th")?.text()?.trim().orEmpty()
                val td = row.selectFirst("td") ?: continue
                when {
                    th.startsWith("Hold") -> hold = td.text().trim().ifBlank { hold }
                    th.startsWith("Karakterskala") -> grading = td.text().trim()
                    th.startsWith("Ansvarlig") -> responsible = td.text().trim()
                    th.startsWith("Elevtid") -> {
                        val raw = td.text().trim().ifBlank {
                            doc.getElementById("m_Content_WeightLbl")?.text()?.trim().orEmpty()
                        }
                        studentTime = raw.split(Regex("""\s+""")).firstOrNull()
                            ?.replace(',', '.')
                            ?.toDoubleOrNull()
                            ?: studentTime
                    }
                    th.startsWith("Opgavenote") -> description = td.text().trim().ifBlank { description }
                    th.contains("undervisningsbeskrivelse") -> { /* optional flag */ }
                }
            }
            // Fallback WeightLbl if Elevtid row missing
            if (studentTime == item.studentTime) {
                val weight = doc.getElementById("m_Content_WeightLbl")?.text()?.trim().orEmpty()
                studentTime = weight.split(Regex("""\s+""")).firstOrNull()
                    ?.replace(',', '.')
                    ?.toDoubleOrNull()
                    ?: studentTime
            }
        } else {
            // Legacy fallbacks (old Android paths)
            description = doc.selectFirst("#m_Content_DescriptionPnl, .exercise-description")
                ?.text()?.trim().orEmpty().ifBlank { item.note }
            doc.select("#m_Content_ExerciseFilePnl a").forEach { a ->
                val href = a.attr("href")
                if (href.isNotBlank()) files += a.text().trim().ifBlank { "Fil" } to absolutize(href)
            }
            responsible = doc.selectFirst("[data-lectiocontextcard^=T]")?.text()?.trim().orEmpty()
        }

        // Student status row
        var awaits = item.awaits
        var status = item.status
        var completed = false
        var studentGrade: String? = item.grade
        var studentGradeNote: String? = item.gradeNote
        var studentNote: String? = item.studentNote
        doc.selectFirst("#m_Content_StudentGV")?.let { table ->
            val rows = table.select("tr").filter { it.select("td").isNotEmpty() }
            val requestedContextCard = requestedStudentId?.let { "S$it" }
            val row = rows.firstOrNull {
                requestedContextCard != null && contextCardId(it) == requestedContextCard
            } ?: rows.firstOrNull { it.hasClass("heavy") }
                ?: rows.firstOrNull()
            if (row != null) {
                val cells = row.select("td")
                val labeled = cells.mapNotNull { cell ->
                    val label = cell.selectFirst(".ls-elevaflevering-field-label") ?: return@mapNotNull null
                    normalizeLabel(label.text()) to cell
                }.toMap()
                fun cell(label: String, fallback: Int): Element? =
                    labeled[normalizeLabel(label)] ?: cells.getOrNull(fallback)

                awaits = valueWithoutInlineLabel(cell("Afventer", 2)).ifBlank { awaits }
                status = valueWithoutInlineLabel(cell("Status - fravær", 3)).ifBlank { status }
                val completedCell = cell("Afsluttet", 4)
                completed = completedCell?.selectFirst("input[type=checkbox][checked]") != null ||
                    completedCell?.selectFirst("input[type=checkbox]")?.hasAttr("checked") == true
                studentGrade = valueWithoutInlineLabel(cell("Karakter", 5)).ifBlank { null } ?: studentGrade
                studentGradeNote = valueWithoutInlineLabel(cell("Karakternote", 6)).ifBlank { null } ?: studentGradeNote
                studentNote = valueWithoutInlineLabel(cell("Elevnote", 7)).ifBlank { null } ?: studentNote
            }
        }

        // Submissions
        val submissions = mutableListOf<AssignmentSubmission>()
        val recipient = doc.selectFirst("#m_Content_RecipientGV")
        if (recipient != null && recipient.select("span.norecord").isEmpty()) {
            val headerIndexes = recipient.selectFirst("tr")?.select("th")
                ?.mapIndexed { index, header -> normalizeLabel(header.text()) to index }
                ?.toMap()
                .orEmpty()
            var index = 0
            for (row in recipient.select("tr")) {
                val cells = row.select("td")
                if (cells.isEmpty()) continue
                val desktop = desktopCells(row)
                fun mobileValue(label: String): Element? = row
                    .select(".ls-elevaflevering-entry-label")
                    .firstOrNull { normalizeLabel(it.text()) == normalizeLabel(label) }
                    ?.parent()
                    ?.selectFirst(".ls-elevaflevering-entry-value")
                fun entryCell(label: String, fallback: Int): Element? {
                    val headerIndex = headerIndexes[normalizeLabel(label)]
                    return headerIndex?.let { cells.getOrNull(it) }
                        ?: desktop.getOrNull(fallback)
                        ?: mobileValue(label)
                }

                val timestamp = elementText(entryCell("Tidspunkt", 0))
                val userCell = entryCell("Bruger", 1)
                val userElement = userCell?.selectFirst("[data-lectiocontextcard]")
                val userText = userElement?.text()?.trim().orEmpty()
                val userTitle = userElement?.attr("title")?.trim().orEmpty()
                val user = if (userTitle.length > userText.length) userTitle else userText.ifBlank {
                    elementText(userCell)
                }
                if (timestamp.isBlank() && user.isBlank()) continue
                val comment = elementText(entryCell("Indlæg", 2)).ifBlank { null }
                val docLink = entryCell("Dokument", 3)?.selectFirst("a[href*=ExerciseFileGet], a[href]")
                submissions += AssignmentSubmission(
                    id = "$index",
                    timestamp = timestamp,
                    user = user,
                    comment = comment,
                    documentName = docLink?.text()?.trim()?.ifBlank { null },
                    documentUrl = docLink?.attr("href")?.takeIf { it.isNotBlank() }?.let { absolutize(it) },
                )
                index++
            }
        }

        return AssignmentDetail(
            item = item.copy(
                title = title,
                team = hold,
                status = status,
                awaits = awaits,
                studentTime = studentTime,
                note = description.ifBlank { item.note },
                grade = studentGrade,
                gradeNote = studentGradeNote,
                studentNote = studentNote,
            ),
            description = description,
            files = files.distinctBy { it.second },
            responsible = responsible,
            grading = grading,
            submissions = submissions,
            completed = completed,
            studentGrade = studentGrade,
            studentGradeNote = studentGradeNote,
        )
    }

    private fun desktopCells(row: Element): List<Element> {
        val desktop = row.select("td.OnlyDesktop")
        if (desktop.isNotEmpty()) return desktop
        val nonMobile = row.select("td:not(.OnlyMobile)")
        if (nonMobile.isNotEmpty()) return nonMobile
        return row.select("td")
    }

    private fun normalizeLabel(value: String?): String = value.orEmpty()
        .replace('\u00a0', ' ')
        .replace(Regex(":\\s*$"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()

    private fun valueWithoutInlineLabel(cell: Element?): String {
        if (cell == null) return ""
        val value = cell.selectFirst(".ls-elevaflevering-field-value, .ls-elevaflevering-note-value")
        if (value != null) return elementText(value)
        val label = cell.selectFirst(".ls-elevaflevering-field-label")?.text().orEmpty()
        return elementText(cell).removePrefix(label).trim()
    }

    private fun elementText(element: Element?): String = element?.wholeText()
        ?.replace('\u00a0', ' ')
        ?.lines()
        ?.joinToString("\n") { it.replace(Regex("[\\t\\r ]+"), " ").trim() }
        ?.replace(Regex("\n{3,}"), "\n\n")
        ?.trim()
        .orEmpty()

    private fun contextCardId(root: Element): String? = root
        .selectFirst("[data-lectiocontextcard]")
        ?.attr("data-lectiocontextcard")
        ?.takeIf { it.isNotBlank() }

    private fun absolutize(href: String): String = when {
        href.startsWith("http") -> href
        href.startsWith("/") -> "https://www.lectio.dk$href"
        else -> "https://www.lectio.dk/$href"
    }
}
