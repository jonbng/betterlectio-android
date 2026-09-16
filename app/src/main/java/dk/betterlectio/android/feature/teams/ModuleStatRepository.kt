package dk.betterlectio.android.feature.teams

import dk.betterlectio.android.core.lectio.LectioClient
import dk.betterlectio.android.core.lectio.session.SessionController
import dk.betterlectio.android.core.result.AppError
import dk.betterlectio.android.core.result.AppResult
import dk.betterlectio.android.feature.demo.DemoData
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModuleStatRepository @Inject constructor(
    private val client: LectioClient,
    private val session: SessionController,
) {
    suspend fun load(): AppResult<List<ModuleStat>> {
        val student = session.currentStudent ?: return AppResult.Failure(AppError.Unauthorized)
        if (student.isDemo) return AppResult.Success(DemoData.moduleStats)
        val overview = client.get("studieplan.aspx")
        if (overview is AppResult.Failure) return overview

        val holds = ModuleStatParser.parseHoldList((overview as AppResult.Success).data.body)
        val stats = mutableListOf<ModuleStat>()
        var firstFailure: AppResult.Failure? = null
        holds.forEach { hold ->
            when (val result = client.get("subnav/modulregnskab.aspx?holdelementid=${hold.id}")) {
                is AppResult.Success -> ModuleStatParser.parseReport(result.data.body, hold)?.let(stats::add)
                is AppResult.Failure -> if (firstFailure == null) firstFailure = result
            }
        }
        if (holds.isNotEmpty() && stats.isEmpty()) {
            return firstFailure ?: AppResult.Failure(AppError.Parsing("Ingen modulregnskaber kunne parses"))
        }
        return AppResult.Success(stats)
    }
}

data class ModuleHold(val id: String, val name: String)

object ModuleStatParser {
    fun parseHoldList(html: String): List<ModuleHold> {
        val doc = Jsoup.parse(html)
        val seen = linkedMapOf<String, String>()
        doc.select("a[href*=holdelementid]").forEach { anchor ->
            val id = Regex("[?&]holdelementid=(\\d+)", RegexOption.IGNORE_CASE)
                .find(anchor.attr("href"))?.groupValues?.get(1) ?: return@forEach
            val name = anchor.text().trim()
            if (name.isNotEmpty()) seen.putIfAbsent(id, name)
        }
        return seen.map { ModuleHold(it.key, it.value) }
    }

    fun parseReport(html: String, hold: ModuleHold): ModuleStat? {
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("s_m_Content_Content_afholdtelektionertbl")
            ?: doc.selectFirst("table[id*=afholdtelektioner]")
            ?: return null
        val row = table.select("tr").firstOrNull { candidate ->
            val cells = candidate.select("td")
            cells.size >= 8 && cells[0].selectFirst(".IndentedBlock") == null
        } ?: return null
        val cells = row.select("td")
        val parsedName = cells[0].text().trim()
        return ModuleStat(
            holdElementId = hold.id,
            team = parsedName.ifBlank { hold.name },
            teachingHeld = number(cells[1].text()),
            teachingPlanned = number(cells[2].text()),
            otherHeld = number(cells[3].text()),
            otherPlanned = number(cells[4].text()),
            total = number(cells[5].text()),
            norm = number(cells[6].text()),
            deviation = cells[7].text().trim(),
        )
    }

    private fun number(value: String): Double? = value.trim()
        .replace(',', '.')
        .replace(Regex("[^0-9.-]"), "")
        .takeIf { it.isNotEmpty() }
        ?.toDoubleOrNull()
}
