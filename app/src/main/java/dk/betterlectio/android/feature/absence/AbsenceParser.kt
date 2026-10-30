package dk.betterlectio.android.feature.absence

import dk.betterlectio.android.core.lectio.scrape.AspNetForm
import dk.betterlectio.android.core.util.LectioDateUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Absence overview + registrations.
 * Overview: Flutter `absence/scraping` (SFTabStudentAbsenceDataTable).
 * Registrations: Flutter/iOS FatabAbsenceFravaerGV + FatabMissingAarsagerGV
 * with activity details from schedule-brick tooltips (iOS StudentParser).
 */
object AbsenceParser {
    fun parseOverview(html: String): List<AbsenceTeamRow> {
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("s_m_Content_Content_SFTabStudentAbsenceDataTable")
            ?: doc.selectFirst("table[id$=SFTabStudentAbsenceDataTable]")
            ?: doc.selectFirst("table[id*=Absence]")
            ?: return emptyList()
        val headerText = table.select("th").text()
        val modern = headerText.contains("Periode", true) && headerText.contains("Opgjort", true)
        val topHeaders = table.selectFirst("tr")?.select("th").orEmpty()
        val regularColumns = topHeaders.getOrNull(1)?.attr("colspan")?.toIntOrNull() ?: 0
        val writtenColumns = topHeaders.getOrNull(2)?.attr("colspan")?.toIntOrNull() ?: 0
        return table.select("tr").mapNotNull { row ->
            if (row.select("th").isNotEmpty()) return@mapNotNull null
            val first = row.selectFirst("td")?.text()?.trim().orEmpty()
            if (first.equals("Samlet", ignoreCase = true) || first.equals("Ej hold", ignoreCase = true)) {
                return@mapNotNull null
            }
            parseTeamRow(row, modern, regularColumns, writtenColumns)
        }
    }

    fun parseSummaryPercents(html: String): Pair<Double?, Double?> {
        val totals = parseTotals(html)
        return totals.regular.assessedPercent to totals.written.assessedPercent
    }

    fun parseTotals(html: String): AbsenceTotals {
        val doc = Jsoup.parse(html)
        val legacyRegular = percent(doc.getElementById("s_m_Content_Content_FremmoedeFravaer")?.text())
        val legacyWritten = percent(doc.getElementById("s_m_Content_Content_SkriftligFravaer")?.text())

        val table = doc.selectFirst("table[id$=SFTabStudentAbsenceDataTable]")
            ?: doc.selectFirst("table[id*=Absence]")
        var totals = AbsenceTotals(
            regular = AbsenceMetricTotals(assessedPercent = legacyRegular),
            written = AbsenceMetricTotals(assessedPercent = legacyWritten),
        )
        if (table != null) {
            val headerText = table.select("th").text()
            val modern = headerText.contains("Periode", true) && headerText.contains("Opgjort", true)
            val topHeaders = table.selectFirst("tr")?.select("th").orEmpty()
            val regularColumns = topHeaders.getOrNull(1)?.attr("colspan")?.toIntOrNull() ?: 0
            val writtenColumns = topHeaders.getOrNull(2)?.attr("colspan")?.toIntOrNull() ?: 0
            val totalRow = table.select("tr").firstOrNull {
                it.selectFirst("td")?.text()?.trim()?.equals("Samlet", true) == true
            }
            val row = totalRow?.let { parseTeamRow(it, modern, regularColumns, writtenColumns) }
            if (row != null) {
                totals = AbsenceTotals(
                    regular = AbsenceMetricTotals(
                        periodPercent = row.regularCurrentPercent,
                        periodAmount = row.regularCurrentModules,
                        assessedPercent = row.regularFinalPercent,
                        assessedAmount = row.regularFinalModules,
                        yearPercent = row.regularYearPercent,
                        yearAmount = row.regularYearModules,
                    ),
                    written = AbsenceMetricTotals(
                        periodPercent = row.assignmentCurrentPercent,
                        periodAmount = row.assignmentCurrentTime,
                        assessedPercent = row.assignmentFinalPercent,
                        assessedAmount = row.assignmentFinalTime,
                        yearPercent = row.assignmentYearPercent,
                        yearAmount = row.assignmentYearTime,
                    ),
                )
            }
        }

        // The visible summary island is Lectio's authoritative Opgjort total.
        val summaryValues = doc.select("[id$=FravaerSamlet_Samletfravaer_pa] .title-large")
            .mapNotNull { percent(it.text()) }
        if (summaryValues.size >= 2) {
            totals = totals.copy(
                regular = totals.regular.copy(assessedPercent = summaryValues[0]),
                written = totals.written.copy(assessedPercent = summaryValues[1]),
            )
        }
        return totals
    }

    private fun parseTeamRow(
        row: Element,
        modern: Boolean,
        regularColumnCount: Int,
        writtenColumnCount: Int,
    ): AbsenceTeamRow? {
        val cells = row.select("td")
        if (cells.size < 5) return null
        val teamCell = cells[0]
        val teamLink = teamCell.selectFirst("a")
        val team = teamLink?.text()?.trim() ?: teamCell.text().trim()
        if (team.isBlank()) return null
        val teamId = teamLink?.attr("href")?.let { href ->
            AspNetForm.queriesFromUrl(href)["holdelementid"]?.let { "HE$it" }
        }

        fun pct(i: Int): Double = percent(cells.getOrNull(i)?.text()).orZero()

        fun fraction(raw: String?): AbsenceFraction {
            val t = raw?.replace(",", ".")?.trim().orEmpty()
            if (t.isEmpty()) return AbsenceFraction()
            val parts = t.split("/")
            return AbsenceFraction(
                current = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0,
                total = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
            )
        }

        fun fraction(i: Int): AbsenceFraction = fraction(cells.getOrNull(i)?.text())

        fun tooltipMetric(groupCells: List<Element>, label: String): Pair<Double?, AbsenceFraction?> {
            val title = groupCells.firstNotNullOfOrNull { cell ->
                val own = cell.attr("title")
                val nested = cell.selectFirst("[title]")?.attr("title").orEmpty()
                (own.ifBlank { nested }).takeIf {
                    it.contains("Opgjort:", true) || it.contains("Periode:", true) ||
                        it.contains("For året:", true)
                }
            }.orEmpty()
            val match = Regex(
                """${Regex.escape(label)}:\s*(\d+(?:[,.]\d+)?)%\s+(\d+(?:[,.]\d+)?/\d+(?:[,.]\d+)?)""",
                RegexOption.IGNORE_CASE,
            ).find(title) ?: return null to null
            return percent(match.groupValues[1] + "%") to fraction(match.groupValues[2])
        }

        val values = cells.drop(1).map { it.text().trim() }
        val hasFractions = values.any { it.contains('/') }
        // 24.031: regular fraction, regular %, written fraction, written %.
        // Older layouts contain four percentages, or eight alternating values.
        val regularCurrentPercent: Double
        val regularFinalPercent: Double
        val assignmentCurrentPercent: Double
        val assignmentFinalPercent: Double
        val regularCurrentModules: AbsenceFraction
        val regularFinalModules: AbsenceFraction
        val assignmentCurrentTime: AbsenceFraction
        val assignmentFinalTime: AbsenceFraction
        var regularYearPercent: Double? = null
        var regularYearModules: AbsenceFraction? = null
        var assignmentYearPercent: Double? = null
        var assignmentYearTime: AbsenceFraction? = null
        if (modern && hasFractions) {
            val dataCells = cells.drop(1)
            val regularCount = regularColumnCount.takeIf { it > 0 } ?: dataCells.size / 2
            val writtenCount = writtenColumnCount.takeIf { it > 0 } ?: dataCells.size - regularCount
            val regularCells = dataCells.take(regularCount)
            val assignmentCells = dataCells.drop(regularCount).take(writtenCount)
            val regularTexts = regularCells.map { it.text().trim() }
            val assignmentTexts = assignmentCells.map { it.text().trim() }

            val regularPeriod = tooltipMetric(regularCells, "Periode")
            val regularOpgjort = tooltipMetric(regularCells, "Opgjort")
            val regularYear = tooltipMetric(regularCells, "For året")
            val assignmentPeriod = tooltipMetric(assignmentCells, "Periode")
            val assignmentOpgjort = tooltipMetric(assignmentCells, "Opgjort")
            val assignmentYear = tooltipMetric(assignmentCells, "For året")

            fun firstPercent(texts: List<String>) = texts.firstNotNullOfOrNull { percent(it) }
            fun lastPercent(texts: List<String>) = texts.asReversed().firstNotNullOfOrNull { percent(it) }
            fun firstFraction(texts: List<String>) = texts.firstNotNullOfOrNull {
                fraction(it).takeIf { parsed -> parsed.total > 0.0 || it.trim().matches(Regex("""0(?:[,.]0+)?\s*/\s*\d+(?:[,.]\d+)?""")) }
            }
            fun lastFraction(texts: List<String>) = texts.asReversed().firstNotNullOfOrNull {
                fraction(it).takeIf { parsed -> parsed.total > 0.0 || it.trim().matches(Regex("""0(?:[,.]0+)?\s*/\s*\d+(?:[,.]\d+)?""")) }
            }

            regularCurrentPercent = regularPeriod.first ?: firstPercent(regularTexts) ?: 0.0
            regularFinalPercent = regularOpgjort.first ?: lastPercent(regularTexts) ?: 0.0
            assignmentCurrentPercent = assignmentPeriod.first ?: firstPercent(assignmentTexts) ?: 0.0
            assignmentFinalPercent = assignmentOpgjort.first ?: lastPercent(assignmentTexts) ?: 0.0
            regularCurrentModules = regularPeriod.second ?: firstFraction(regularTexts) ?: AbsenceFraction()
            regularFinalModules = regularOpgjort.second ?: lastFraction(regularTexts) ?: regularCurrentModules
            assignmentCurrentTime = assignmentPeriod.second ?: firstFraction(assignmentTexts) ?: AbsenceFraction()
            assignmentFinalTime = assignmentOpgjort.second ?: lastFraction(assignmentTexts) ?: assignmentCurrentTime
            regularYearPercent = regularYear.first
            regularYearModules = regularYear.second
            assignmentYearPercent = assignmentYear.first
            assignmentYearTime = assignmentYear.second
        } else {
            regularCurrentPercent = pct(1)
            regularCurrentModules = fraction(2)
            regularFinalPercent = pct(3)
            regularFinalModules = fraction(4)
            assignmentCurrentPercent = pct(5)
            assignmentCurrentTime = fraction(6)
            assignmentFinalPercent = pct(7)
            assignmentFinalTime = fraction(8)

            // The pre-24.031 production table had four percentage-only cells.
            if (!hasFractions && values.size <= 4) {
                return AbsenceTeamRow(
                    team = team,
                    teamId = teamId,
                    regularCurrentPercent = pct(1),
                    regularFinalPercent = pct(2),
                    assignmentCurrentPercent = pct(3),
                    assignmentFinalPercent = pct(4),
                )
            }
        }

        return AbsenceTeamRow(
            team = team,
            teamId = teamId,
            regularCurrentPercent = regularCurrentPercent,
            regularCurrentModules = regularCurrentModules,
            regularFinalPercent = regularFinalPercent,
            regularFinalModules = regularFinalModules,
            assignmentCurrentPercent = assignmentCurrentPercent,
            assignmentCurrentTime = assignmentCurrentTime,
            assignmentFinalPercent = assignmentFinalPercent,
            assignmentFinalTime = assignmentFinalTime,
            regularYearPercent = regularYearPercent,
            regularYearModules = regularYearModules,
            assignmentYearPercent = assignmentYearPercent,
            assignmentYearTime = assignmentYearTime,
        )
    }

    fun parseRegistrations(html: String): List<AbsenceRegistration> {
        val doc = Jsoup.parse(html)
        val registered = doc.getElementById("s_m_Content_Content_FatabAbsenceFravaerGV")
            ?.let { parseCauseTable(it, missingCause = false) }
            .orEmpty()
        val missing = doc.getElementById("s_m_Content_Content_FatabMissingAarsagerGV")
            ?.let { parseCauseTable(it, missingCause = true) }
            .orEmpty()

        // Missing-reason rows are a subset of the full registration table. Merge
        // their state by stable registration id instead of counting them twice.
        val missingById = missing.associateBy { it.id }
        val registeredIds = registered.mapTo(mutableSetOf()) { it.id }
        val out = registered.map { entry ->
            if (missingById.containsKey(entry.id)) {
                entry.copy(cause = "", note = "", missingCause = true)
            } else {
                entry
            }
        } + missing.filterNot { it.id in registeredIds }

        // Fallback for simplified fixtures / alternate layouts
        if (out.isEmpty()) {
            val table = doc.selectFirst("table[id*=Absence], table[id*=fravaer], table[id*=Fravaer], table")
                ?: return emptyList()
            return table.select("tr").drop(1).mapIndexedNotNull { i, row ->
                val cells = desktopCells(row)
                if (cells.size < 3) return@mapIndexedNotNull null
                val id = extractRegistrationId(row) ?: "reg-$i"
                AbsenceRegistration(
                    id = id,
                    date = LectioDateUtils.parseLectioDate(cells[0].text())?.toLocalDate(),
                    team = cells.getOrNull(1)?.text()?.trim().orEmpty(),
                    cause = cells.getOrNull(2)?.text()?.trim().orEmpty()
                        .ifBlank { cells.getOrNull(6)?.text()?.trim().orEmpty() },
                    status = cells.getOrNull(3)?.text()?.trim().orEmpty(),
                )
            }
        }
        return AbsencePresentation.sortNewestFirst(out)
    }

    private fun parseCauseTable(table: Element, missingCause: Boolean): List<AbsenceRegistration> {
        return table.select("tr").mapNotNull { row ->
            // Skip header rows (iOS: has th)
            if (row.select("th").isNotEmpty()) return@mapNotNull null
            val cells = desktopCells(row)
            if (cells.size < 4) return@mapNotNull null

            val activityIndex = cells.indexOfFirst {
                it.selectFirst("a.s2skemabrik, a.s2bgbox") != null
            }.takeIf { it >= 0 } ?: 1
            val week = cells.getOrNull(activityIndex - 1)?.text()?.trim().orEmpty()
            val activityCell = cells.getOrNull(activityIndex)
            val activity = activityCell?.selectFirst("a.s2skemabrik, a.s2bgbox")
            val activityText = activity?.text()?.trim()
                ?: activityCell?.text()?.trim().orEmpty()
            val details = parseActivityDetails(activity, activityText)

            val firstAbsenceIndex = activityIndex + 1
            val firstAbsenceText = cells.getOrNull(firstAbsenceIndex)?.text()?.trim().orEmpty()
            val mergedTypeAndPercent = firstAbsenceText.contains('%') &&
                (firstAbsenceText.contains("Fravær", true) || firstAbsenceText.contains("Godskrevet", true))
            val typeIndex = if (mergedTypeAndPercent) firstAbsenceIndex else firstAbsenceIndex + 1
            val registeredIndex = typeIndex + 1
            val percent = percent(firstAbsenceText)

            val typeCell = cells.getOrNull(typeIndex)
            val typeText = typeCell?.text()?.trim().orEmpty()
            val hasOk = typeCell?.selectFirst("img[src*=ok.gif]") != null
            val isApproved = hasOk || typeText.contains("Godskrevet", ignoreCase = true)
            val status = when {
                isApproved -> "Godskrevet"
                typeText.contains("Fravær", true) -> "Fravær"
                typeText.isNotBlank() -> typeText
                else -> "Fravær"
            }

            val registeredRaw = cells.getOrNull(registeredIndex)?.text()?.trim().orEmpty()
            // First line is date/time; second may be teacher initials
            val registeredDateTime = Regex("""\d{1,2}/\d{1,2}-\d{4}(?:\s+\d{1,2}:\d{2})?""")
                .find(registeredRaw)?.value
                ?: registeredRaw.lineSequence().firstOrNull()?.trim().orEmpty()
            val registeredAt = parseRegisteredAt(registeredDateTime.ifBlank { registeredRaw })

            val editIndex = cells.indexOfFirst {
                it.selectFirst("a[href*=fravaer_aarsag]") != null
            }.takeIf { it >= 0 } ?: cells.size
            val causeIndex = if (missingCause) -1 else editIndex - 1
            val remarkIndex = registeredIndex + 1
            val remark = if (remarkIndex < (if (causeIndex >= 0) causeIndex else editIndex)) {
                cells.getOrNull(remarkIndex)?.text()?.trim().orEmpty()
            } else ""

            val causeCell = cells.getOrNull(causeIndex)
            val causeText = causeCell?.wholeText()?.trim().orEmpty()
                .ifBlank { causeCell?.text()?.trim().orEmpty() }
            val causeLines = causeText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            val cause = when {
                missingCause -> ""
                else -> AbsenceCauses.all.firstOrNull { c ->
                    causeText.startsWith(c, ignoreCase = true)
                } ?: causeLines.firstOrNull().orEmpty()
            }
            val note = if (missingCause) {
                ""
            } else {
                // Prefer second line; else text after matched cause name
                when {
                    causeLines.size > 1 -> causeLines.drop(1).joinToString(" ")
                    cause.isNotBlank() && causeText.length > cause.length ->
                        causeText.removePrefix(cause).trim().trimStart(':', '-', '–')
                    else -> ""
                }
            }

            val id = extractRegistrationId(row) ?: return@mapNotNull null

            val hold = details.hold.ifBlank {
                // Fallback: "fr 10/10 1. modul - 1g4 da • Ka • 24"
                extractHoldFromActivityText(activityText)
            }

            val date: LocalDate? = details.dateFromTooltip
                ?: registeredAt?.toLocalDate()

            AbsenceRegistration(
                id = id,
                date = date,
                team = hold.ifBlank { activityText },
                cause = cause,
                status = status,
                week = week,
                activityTitle = activityText,
                percent = percent,
                registeredAt = registeredAt,
                note = note,
                missingCause = missingCause,
                teacher = details.teacher,
                room = details.room,
                dateTimeLabel = details.dateTimeLabel.ifBlank {
                    registeredDateTime
                },
                lessonTitle = details.title,
                remark = remark,
                isApproved = isApproved,
            )
        }
    }

    private data class ActivityDetails(
        val title: String = "",
        val hold: String = "",
        val teacher: String = "",
        val room: String = "",
        val dateTimeLabel: String = "",
        val dateFromTooltip: LocalDate? = null,
    )

    /**
     * iOS [StudentParser.parseActivityDetails] — tooltip lines + activity text fallback.
     */
    private fun parseActivityDetails(link: Element?, activityText: String): ActivityDetails {
        if (link == null) {
            return ActivityDetails(hold = extractHoldFromActivityText(activityText))
        }
        val tooltip = link.attr("data-tooltip")
        val lines = tooltip.lines().map { it.trim() }.filter { it.isNotEmpty() }

        var title = ""
        var dateTimeLabel = ""
        var hold = ""
        var teacher = ""
        var room = ""
        var dateFromTooltip: LocalDate? = null

        lines.forEachIndexed { index, line ->
            when {
                line.startsWith("Hold:", ignoreCase = true) ->
                    hold = line.substringAfter(':').trim()
                line.startsWith("Lærer:", ignoreCase = true) ||
                    line.startsWith("Lærere:", ignoreCase = true) -> {
                    val teacherPart = line.substringAfter(':').trim()
                    // "Name (Abbrev)" → Abbrev
                    teacher = Regex("""\(([^)]+)\)\s*$""").find(teacherPart)?.groupValues?.get(1)
                        ?: teacherPart
                }
                line.startsWith("Lokale:", ignoreCase = true) ||
                    line.startsWith("Lokaler:", ignoreCase = true) ->
                    room = line.substringAfter(':').trim()
                line.contains("/") && (
                    line.contains("til", ignoreCase = true) ||
                        Regex("""\d{1,2}/\d{1,2}""").containsMatchIn(line)
                    ) -> {
                    dateTimeLabel = line
                    dateFromTooltip = parseDateFromDateTimeLabel(line)
                }
                // Free title is first non-prefixed, non-date line
                index == 0 && !line.contains("/") -> title = line
            }
        }

        if (hold.isEmpty()) {
            hold = extractHoldFromActivityText(activityText)
        }
        if (teacher.isEmpty() || room.isEmpty()) {
            val parts = activityText.split("•").map { it.trim() }
            // "… - hold" then • teacher • room
            if (parts.size >= 2 && teacher.isEmpty()) teacher = parts.getOrNull(1).orEmpty()
            if (parts.size >= 3 && room.isEmpty()) room = parts.getOrNull(2).orEmpty()
        }

        return ActivityDetails(
            title = title,
            hold = hold,
            teacher = teacher,
            room = room,
            dateTimeLabel = dateTimeLabel,
            dateFromTooltip = dateFromTooltip,
        )
    }

    private fun extractHoldFromActivityText(activityText: String): String {
        // "fr 10/10 1. modul - 1g4 da • Ka • 24"
        val afterDash = activityText.substringAfter(" - ", "").trim()
        if (afterDash.isNotEmpty()) {
            return afterDash.substringBefore("•").trim()
        }
        return activityText.substringBefore("•").trim()
    }

    private fun parseDateFromDateTimeLabel(label: String): LocalDate? {
        // "10/10-2025 08:10 til 09:50" or "10/10-2025 08:12"
        val cleaned = label.replace(Regex("""\s+til\s+.*"""), "").trim()
        return LectioDateUtils.parseLectioDate(cleaned)?.toLocalDate()
            ?: run {
                val dateToken = cleaned.split(Regex("""\s+""")).firstOrNull().orEmpty()
                LectioDateUtils.parseLectioDate(dateToken)?.toLocalDate()
            }
    }

    private fun extractRegistrationId(row: Element): String? {
        // Flutter: prefer edit-button query `id` over absid on the activity brick
        row.select("a[href]").forEach { a ->
            val href = a.attr("href")
            AspNetForm.queriesFromUrl(href)["id"]?.takeIf { it.isNotBlank() }?.let { return it }
            Regex("""[?&]id=(\d+)""").find(href)?.let { return it.groupValues[1] }
        }
        row.select("a[href]").forEach { a ->
            val href = a.attr("href")
            Regex("""absid=(\d+)""").find(href)?.let { return it.groupValues[1] }
            Regex("""aftaleid=(\d+)""").find(href)?.let { return it.groupValues[1] }
        }
        return null
    }

    private fun parseRegisteredAt(raw: String): LocalDateTime? {
        // "dd/MM-yyyy HH:mm …" — take first two tokens (date + time)
        val parts = raw.trim().split(Regex("""\s+"""))
        if (parts.size >= 2) {
            LectioDateUtils.parseLectioDate("${parts[0]} ${parts[1]}")?.let { return it }
        }
        return LectioDateUtils.parseLectioDate(raw)
    }

    private fun percent(raw: String?): Double? {
        val match = Regex("""\d+(?:[,.]\d+)?\s*%""").find(raw.orEmpty())?.value ?: return null
        return match.replace("%", "").replace(",", ".").trim().toDoubleOrNull()?.div(100.0)
    }

    private fun Double?.orZero(): Double = this ?: 0.0

    private fun desktopCells(row: Element): List<Element> {
        // iOS: td:not(.OnlyMobile) so activity cell (no class) is included
        val nonMobile = row.select("td:not(.OnlyMobile)")
        if (nonMobile.isNotEmpty()) return nonMobile
        val desktop = row.select("td.OnlyDesktop")
        if (desktop.isNotEmpty()) return desktop
        return row.select("td")
    }
}
