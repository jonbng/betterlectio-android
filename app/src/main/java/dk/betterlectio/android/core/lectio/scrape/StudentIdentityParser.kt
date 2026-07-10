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
 * Dart parity: Student.getBasicInfo / extractBasicInfo / msapplication-starturl
 */
object StudentIdentityParser {

    private const val META_START_URL = "msapplication-starturl"
    private const val ELEV_ID_KEY = "elevid"
    private const val LAERER_ID_KEY = "laererid"

    fun parse(html: String): StudentIdentity {
        val doc = Jsoup.parse(html)

        var studentId: String? = null
        var teacherId: String? = null

        val meta = doc.selectFirst("meta[name=$META_START_URL]")
            ?.attr("content")
        val queries = AspNetForm.queriesFromUrl(meta)
        studentId = queries[ELEV_ID_KEY]
        teacherId = queries[LAERER_ID_KEY]

        var pictureId: String? = null
        val img = doc.getElementById("s_m_HeaderContent_picctrlthumbimage")
            ?: doc.selectFirst("img#s_m_HeaderContent_picctrlthumbimage")
        val src = img?.attr("src")
        if (!src.isNullOrBlank() && src.contains("pictureid")) {
            pictureId = AspNetForm.queriesFromUrl(src)["pictureid"]
        }

        var name: String? = null
        val title = doc.getElementById("s_m_HeaderContent_MainTitle")?.text()
        if (!title.isNullOrBlank()) {
            // Typical: "Eleven First Last, 3a" or similar — best-effort extract.
            name = parseNameFromTitle(title)
        }

        return StudentIdentity(
            studentId = studentId,
            teacherId = teacherId,
            name = name,
            pictureId = pictureId,
        )
    }

    internal fun parseNameFromTitle(title: String): String? {
        val trimmed = title.trim()
        // "Eleven NAME, class" / "Eleven NAME - class"
        val afterPrefix = when {
            trimmed.contains("Eleven ", ignoreCase = true) ->
                trimmed.substringAfter("Eleven ", missingDelimiterValue = trimmed)
            trimmed.contains("Lærer ", ignoreCase = true) ->
                trimmed.substringAfter("Lærer ", missingDelimiterValue = trimmed)
            else -> trimmed
        }
        val namePart = afterPrefix.split(',', '–', '-').firstOrNull()?.trim()
        return namePart?.takeIf { it.isNotEmpty() }
    }
}
