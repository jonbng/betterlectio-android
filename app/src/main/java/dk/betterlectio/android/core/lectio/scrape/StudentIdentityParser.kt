package dk.betterlectio.android.core.lectio.scrape

import org.jsoup.Jsoup

data class StudentIdentity(
    val studentId: String?,
    val teacherId: String?,
    val name: String?,
    val pictureId: String?,
) {
    val isTeacher: Boolean get() = !teacherId.isNullOrBlank()
    val personId: String? get() = studentId ?: teacherId
}

/**
 * Parse student id / name / picture from Lectio page chrome.
 *
 * Sources (in priority order), matching both Flutter and iOS:
 * 1. Flutter / lectio_wrapper: `meta[name=msapplication-starturl]` → `elevid` / `laererid`
 * 2. iOS `StudentParser.parseStudentInfo`: first `a[href*=elevid]` / `a[href*=laererid]`
 * 3. Regex fallback on raw HTML for `elevid=` / `laererid=` (covers minified / odd markup)
 * 4. `data-lectiocontextcard` self-links like `S123…` / `T123…` when clearly singular
 */
object StudentIdentityParser {

    private const val META_START_URL = "msapplication-starturl"
    private const val ELEV_ID_KEY = "elevid"
    private const val LAERER_ID_KEY = "laererid"

    private val elevIdInUrl = Regex("""elevid=(\d+)""", RegexOption.IGNORE_CASE)
    private val laererIdInUrl = Regex("""laererid=(\d+)""", RegexOption.IGNORE_CASE)
    private val contextCard = Regex("""data-lectiocontextcard=["']([ST])(\d+)["']""", RegexOption.IGNORE_CASE)

    fun parse(html: String): StudentIdentity {
        val doc = Jsoup.parse(html)

        var studentId: String? = null
        var teacherId: String? = null

        // 1) Flutter: msapplication-starturl meta
        val meta = doc.selectFirst("meta[name=$META_START_URL]")
            ?.attr("content")
        val metaQueries = AspNetForm.queriesFromUrl(meta)
        studentId = metaQueries[ELEV_ID_KEY]?.takeIf { it.isNotBlank() }
        teacherId = metaQueries[LAERER_ID_KEY]?.takeIf { it.isNotBlank() }

        // 2) iOS: first link containing elevid / laererid
        if (studentId.isNullOrBlank()) {
            studentId = firstIdFromLinks(doc, elevIdInUrl, "a[href*=elevid]")
        }
        if (teacherId.isNullOrBlank() && studentId.isNullOrBlank()) {
            teacherId = firstIdFromLinks(doc, laererIdInUrl, "a[href*=laererid]")
        }

        // 3) Regex over full HTML (handles non-anchor URLs, script vars, etc.)
        if (studentId.isNullOrBlank()) {
            studentId = elevIdInUrl.find(html)?.groupValues?.getOrNull(1)
        }
        if (teacherId.isNullOrBlank() && studentId.isNullOrBlank()) {
            teacherId = laererIdInUrl.find(html)?.groupValues?.getOrNull(1)
        }

        // 4) Context card — only if a single distinct person id appears (avoid team lists)
        if (studentId.isNullOrBlank() && teacherId.isNullOrBlank()) {
            val cards = contextCard.findAll(html)
                .map { it.groupValues[1].uppercase() to it.groupValues[2] }
                .distinct()
                .toList()
            if (cards.size == 1) {
                val (kind, id) = cards.first()
                if (kind == "S") studentId = id else teacherId = id
            }
        }

        var pictureId: String? = null
        val img = doc.getElementById("s_m_HeaderContent_picctrlthumbimage")
            ?: doc.selectFirst("img#s_m_HeaderContent_picctrlthumbimage")
        val src = img?.attr("src")
        if (!src.isNullOrBlank() && src.contains("pictureid", ignoreCase = true)) {
            pictureId = AspNetForm.queriesFromUrl(src)["pictureid"]
                ?: Regex("""pictureid=(\d+)""", RegexOption.IGNORE_CASE)
                    .find(src)?.groupValues?.getOrNull(1)
        }

        var name: String? = null
        val title = doc.getElementById("s_m_HeaderContent_MainTitle")?.text()
        if (!title.isNullOrBlank()) {
            name = parseNameFromTitle(title)
        }
        if (name.isNullOrBlank()) {
            // iOS: subHeaderDiv "Elev: Name …"
            val header = doc.selectFirst("div[id*=subHeaderDiv]")?.text().orEmpty()
            val elevMatch = Regex("""Elev:\s*(.+?)(?:\s*\(|$)""", RegexOption.IGNORE_CASE)
                .find(header)
            name = elevMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        }

        return StudentIdentity(
            studentId = studentId,
            teacherId = teacherId,
            name = name,
            pictureId = pictureId,
        )
    }

    private fun firstIdFromLinks(
        doc: org.jsoup.nodes.Document,
        pattern: Regex,
        css: String,
    ): String? {
        for (link in doc.select(css)) {
            val href = link.attr("href")
            val id = pattern.find(href)?.groupValues?.getOrNull(1)
            if (!id.isNullOrBlank()) return id
        }
        return null
    }

    internal fun parseNameFromTitle(title: String): String? {
        val trimmed = title.trim()
        // "Eleven NAME, class" / "Eleven NAME - class" / "Lærer NAME"
        val afterPrefix = when {
            trimmed.contains("Eleven ", ignoreCase = true) ->
                trimmed.substringAfter("Eleven ", missingDelimiterValue = trimmed)
            trimmed.contains("Lærer ", ignoreCase = true) ->
                trimmed.substringAfter("Lærer ", missingDelimiterValue = trimmed)
            else -> trimmed
        }
        // Strip optional (k) kostelev marker before comma/dash split
        val withoutKost = afterPrefix.replace(Regex("""\(k\)""", RegexOption.IGNORE_CASE), "")
        val namePart = withoutKost.split(',', '–', '-').firstOrNull()?.trim()
        return namePart?.takeIf { it.isNotEmpty() }
    }
}
