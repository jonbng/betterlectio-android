package dk.betterlectio.android.feature.absence

import dk.betterlectio.android.core.lectio.scrape.AspNetForm
import dk.betterlectio.android.core.util.LectioDateUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Absence overview + registrations.
 * Overview: Flutter `absence/scraping` (SFTabStudentAbsenceDataTable).
 * Registrations: Flutter/iOS FatabAbsenceFravaerGV + FatabMissingAarsagerGV.
 */
object AbsenceParser {
    fun parseOverview(html: String): List<AbsenceTeamRow> {
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("s_m_Content_Content_SFTabStudentAbsenceDataTable")
            ?: doc.selectFirst("table[id*=Absence]")
            ?: return emptyList()
        val rows = table.select("tr")
        // skip header rows (first ~3) and last total — Flutter extractAbsence
        if (rows.size <= 4) return emptyList()
        return rows.drop(3).dropLast(1).mapNotNull { parseTeamRow(it) }
    }

    fun parseSummaryPercents(html: String): Pair<Double?, Double?> {
        val doc = Jsoup.parse(html)
        fun pct(id: String): Double? {
            val t = doc.getElementById(id)?.text()
                ?.replace("%", "")
                ?.replace(",", ".")
                ?.trim()
                .orEmpty()
            return t.toDoubleOrNull()?.div(100.0)
        }
        // iOS StudentParser summary spans
        return pct("s_m_Content_Content_FremmoedeFravaer") to
            pct("s_m_Content_Content_SkriftligFravaer")
    }

    private fun parseTeamRow(row: Element): AbsenceTeamRow? {
        val cells = row.select("td")
        if (cells.size < 5) return null
        val teamCell = cells[0]
        val teamLink = teamCell.selectFirst("a")
        val team = teamLink?.text()?.trim() ?: teamCell.text().trim()
        if (team.isBlank()) return null
        val teamId = teamLink?.attr("href")?.let { href ->
            AspNetForm.queriesFromUrl(href)["holdelementid"]?.let { "HE$it" }
        }

        fun pct(i: Int): Double {
            val t = cells.getOrNull(i)?.text()?.replace("%", "")?.replace(",", ".")?.trim().orEmpty()
            return t.toDoubleOrNull()?.div(100.0) ?: 0.0
        }

        fun fraction(i: Int): AbsenceFraction {
            val t = cells.getOrNull(i)?.text()?.replace(",", ".")?.trim().orEmpty()
            if (t.isEmpty()) return AbsenceFraction()
            val parts = t.split("/")
            return AbsenceFraction(
                current = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0,
                total = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
            )
        }

        // Flutter: cells 1–8 alternating percent / fraction
        return AbsenceTeamRow(
            team = team,
            teamId = teamId,
            regularCurrentPercent = pct(1),
            regularCurrentModules = fraction(2),
            regularFinalPercent = pct(3),
            regularFinalModules = fraction(4),
            assignmentCurrentPercent = pct(5),
            assignmentCurrentTime = fraction(6),
            assignmentFinalPercent = pct(7),
            assignmentFinalTime = fraction(8),
        )
    }

    fun parseRegistrations(html: String): List<AbsenceRegistration> {
        val doc = Jsoup.parse(html)
        val out = mutableListOf<AbsenceRegistration>()

        val registered = doc.getElementById("s_m_Content_Content_FatabAbsenceFravaerGV")
        if (registered != null) {
            out += parseCauseTable(registered, missingCause = false)
        }
        val missing = doc.getElementById("s_m_Content_Content_FatabMissingAarsagerGV")
        if (missing != null) {
            out += parseCauseTable(missing, missingCause = true)
        }

        // Fallback for simplified fixtures / alternate layouts
        if (out.isEmpty()) {
            val table = doc.selectFirst("table[id*=Absence], table[id*=fravaer], table[id*=Fravaer], table")
                ?: return emptyList()
            return table.select("tr").drop(1).mapIndexedNotNull { i, row ->
                val cells = desktopCells(row)
                if (cells.size < 3) return@mapIndexedNotNull null
                // Prefer real id if present
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
        return out
    }

    private fun parseCauseTable(table: Element, missingCause: Boolean): List<AbsenceRegistration> {
        return table.select("tr").drop(1).mapNotNull { row ->
            val cells = desktopCells(row)
            if (cells.size < 4) return@mapNotNull null

            val week = cells.getOrNull(0)?.text()?.trim().orEmpty()
            val activity = cells.getOrNull(1)?.selectFirst("a.s2skemabrik, a.s2bgbox")
            val activityTitle = activity?.text()?.trim()
                ?: cells.getOrNull(1)?.text()?.trim().orEmpty()
            val percent = cells.getOrNull(2)?.text()
                ?.replace("%", "")
                ?.replace(",", ".")
                ?.trim()
                ?.toDoubleOrNull()
                ?.div(100.0)
            val typeText = cells.getOrNull(3)?.text()?.trim().orEmpty()
            val status = when {
                cells.getOrNull(3)?.selectFirst("img[src*=ok.gif]") != null -> "Godskrevet"
                typeText.contains("Godskrevet", true) -> "Godskrevet"
                typeText.contains("Fravær", true) -> "Fravær"
                else -> typeText
            }
            val registeredRaw = cells.getOrNull(4)?.text()?.trim().orEmpty()
            val registeredAt = parseRegisteredAt(registeredRaw)

            // Cause column: registered table col 6; missing-cause table also col 6 (edit lives there)
            val causeCell = cells.getOrNull(6)
            val causeText = causeCell?.text()?.trim().orEmpty()
            val cause = AbsenceCauses.all.firstOrNull { c ->
                causeText.startsWith(c, ignoreCase = true)
            } ?: causeText.lineSequence().firstOrNull()?.trim().orEmpty()
            val note = causeText.substringAfter('\n', "").trim()

            val id = extractRegistrationId(row) ?: return@mapNotNull null

            AbsenceRegistration(
                id = id,
                date = registeredAt?.toLocalDate(),
                team = activityTitle,
                cause = cause,
                status = status,
                week = week,
                activityTitle = activityTitle,
                percent = percent,
                registeredAt = registeredAt,
                note = note,
                missingCause = missingCause,
            )
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

    private fun parseRegisteredAt(raw: String): java.time.LocalDateTime? {
        // "dd/MM-yyyy HH:mm …" — take first two tokens (date + time)
        val parts = raw.trim().split(Regex("""\s+"""))
        if (parts.size >= 2) {
            LectioDateUtils.parseLectioDate("${parts[0]} ${parts[1]}")?.let { return it }
        }
        return LectioDateUtils.parseLectioDate(raw)
    }

    private fun desktopCells(row: Element): List<Element> {
        val desktop = row.select("td.OnlyDesktop")
        if (desktop.isNotEmpty()) return desktop
        val nonMobile = row.select("td:not(.OnlyMobile)")
        if (nonMobile.isNotEmpty()) return nonMobile
        return row.select("td")
    }
}
