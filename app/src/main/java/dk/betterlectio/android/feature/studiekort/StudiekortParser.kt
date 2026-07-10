package dk.betterlectio.android.feature.studiekort

import org.jsoup.Jsoup

/**
 * Parses Lectio studiekort / elevforside HTML for photo and QR image URLs.
 */
object StudiekortParser {
    data class ParsedCard(
        val name: String?,
        val classLabel: String?,
        val schoolName: String?,
        val photoUrl: String?,
        val qrUrl: String?,
        val pictureId: String?,
        val birthday: String? = null,
    )

    fun parse(html: String, gymId: Int, studentId: String): ParsedCard {
        val doc = Jsoup.parse(html)
        val name = doc.selectFirst("#s_m_HeaderContent_MainTitle, .ls-person-name, h1")
            ?.text()?.trim()?.takeIf { it.isNotBlank() }
        val classLabel = doc.selectFirst(".ls-person-text, #s_m_HeaderContent_MainTitle")
            ?.text()?.let { extractClass(it) }
        val schoolName = doc.selectFirst(".ls-master-header-institutionname, .ls-subtext")
            ?.text()?.trim()?.takeIf { it.isNotBlank() }
        val birthday = parseBirthday(html)

        var photoUrl: String? = null
        var pictureId: String? = null
        doc.select("img[src*=GetImage], img[src*=pictureid]").forEach { img ->
            val src = img.attr("src")
            if (src.contains("studiekortqr", ignoreCase = true)) return@forEach
            if (src.contains("GetImage") || src.contains("pictureid")) {
                photoUrl = absolutize(src, gymId)
                pictureId = Regex("""pictureid=(\d+)""", RegexOption.IGNORE_CASE)
                    .find(src)?.groupValues?.get(1)
            }
        }

        var qrUrl: String? = null
        doc.select("img[src*=studiekortqr], img[src*=qr], a[href*=studiekortqr] img").forEach { img ->
            val src = img.attr("src").ifBlank { img.parent()?.attr("href").orEmpty() }
            if (src.isNotBlank()) qrUrl = absolutize(src, gymId)
        }
        // Also look for explicit studiekortqr links
        doc.select("a[href*=studiekortqr], img[src*=type=studiekortqr]").forEach { el ->
            val src = el.attr("src").ifBlank { el.attr("href") }
            if (src.isNotBlank()) qrUrl = absolutize(src, gymId)
        }

        // Fallback constructed QR if page only has student id
        if (qrUrl == null && studentId.isNotBlank()) {
            qrUrl =
                "https://www.lectio.dk/lectio/$gymId/GetImage.aspx?type=studiekortqr&studentid=$studentId"
        }
        if (photoUrl == null && pictureId != null) {
            photoUrl =
                "https://www.lectio.dk/lectio/$gymId/GetImage.aspx?pictureid=$pictureId&fullsize=1"
        }

        return ParsedCard(name, classLabel, schoolName, photoUrl, qrUrl, pictureId, birthday)
    }

    /**
     * iOS parity: StudentParser.parseBirthday — span#s_m_Content_Content_StudentBirthday
     */
    fun parseBirthday(html: String): String? {
        val doc = Jsoup.parse(html)
        val span = doc.selectFirst(
            "#s_m_Content_Content_StudentBirthday, " +
                "span#s_m_Content_Content_StudentBirthday, " +
                "[id*=StudentBirthday], [id*=studentBirthday]",
        ) ?: return null
        return span.text()
            .replace("Fødselsdag:", "", ignoreCase = true)
            .replace("Fødselsdag", "", ignoreCase = true)
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun extractClass(title: String): String? {
        // e.g. "Demo Elev - 3.x"
        val parts = title.split(" - ", "–").map { it.trim() }
        return parts.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun absolutize(src: String, gymId: Int): String {
        if (src.startsWith("http")) return src
        if (src.startsWith("//")) return "https:$src"
        if (src.startsWith("/")) return "https://www.lectio.dk$src"
        return "https://www.lectio.dk/lectio/$gymId/$src"
    }
}
