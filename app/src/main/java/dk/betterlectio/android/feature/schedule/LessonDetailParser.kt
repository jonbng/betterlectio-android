package dk.betterlectio.android.feature.schedule

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Pure HTML → lesson detail (participants, resources, content blocks).
 * Flutter parity: events/scraping extractRegularEventDetails
 * iOS parity: ScheduleParser.parseLessonContent (simplified)
 */
object LessonDetailParser {

    fun parse(html: String, eventId: String, fallbackTitle: String = ""): LessonDetail {
        val doc = Jsoup.parse(html)

        val note = doc.getElementById("s_m_Content_Content_tocAndToolbar_ActNoteTB_tb")
            ?.text()?.trim()?.ifBlank { null }
            ?: doc.selectFirst("textarea[id*=ActNote], #m_Content_commentTextBox_tb")
                ?.text()?.trim()?.ifBlank { null }

        val contentRoot = doc.getElementById("s_m_Content_Content_tocAndToolbar_inlineHomeworkDiv")
            ?: doc.selectFirst("[id*=inlineHomework], [id*=tocAndToolbar]")

        val blocks = mutableListOf<LessonContentBlock>()
        val resources = mutableListOf<LessonResource>()
        var homework: String? = null

        if (contentRoot != null) {
            val empty = contentRoot.text().contains("ikke noget indhold", ignoreCase = true)
            if (!empty) {
                contentRoot.select("article, .ls-paper, .activity-content").forEach { article ->
                    parseArticle(article, blocks, resources)
                }
                if (blocks.isEmpty()) {
                    contentRoot.select("p, h1, h2, h3, li").forEach { el ->
                        val t = el.text().trim()
                        if (t.isNotEmpty()) {
                            blocks += LessonContentBlock(
                                kind = if (el.tagName().startsWith("h")) "heading" else "paragraph",
                                text = t,
                            )
                        }
                    }
                }
                contentRoot.select("a[href]").forEach { a ->
                    val href = a.attr("href")
                    if (href.isBlank()) return@forEach
                    val title = a.text().trim().ifBlank { href }
                    val isFile = a.attr("data-lc-display-linktype") == "file" ||
                        href.contains("GetFile", ignoreCase = true) ||
                        href.contains("documentid", ignoreCase = true)
                    if (resources.none { it.url == href }) {
                        resources += LessonResource(title = title, url = absoluteUrl(href), isFile = isFile)
                    }
                }
            }
        }

        // Homework hint from common containers
        homework = doc.selectFirst("[class*=lektie], [id*=Homework]")
            ?.text()?.trim()?.takeIf { it.length in 1..500 }

        val participants = parseParticipants(doc)

        val title = doc.selectFirst("h1, #s_m_HeaderContent_MainTitle, .ls-paper-header")
            ?.text()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: fallbackTitle

        if (note != null && blocks.none { it.kind == "note" }) {
            blocks.add(0, LessonContentBlock("note", note))
        }

        return LessonDetail(
            eventId = eventId,
            title = title,
            note = note,
            homework = homework,
            contentBlocks = blocks,
            participants = participants,
            resources = resources,
        )
    }

    private fun parseArticle(
        article: Element,
        blocks: MutableList<LessonContentBlock>,
        resources: MutableList<LessonResource>,
    ) {
        val title = article.selectFirst("h1, h2, [id*=titleHeader]")?.text()?.trim()
        if (!title.isNullOrBlank()) {
            blocks += LessonContentBlock("heading", title)
        }
        article.select("blockquote").forEach { bq ->
            val t = bq.text().trim()
            if (t.isNotEmpty()) blocks += LessonContentBlock("note", t)
        }
        article.select("p, li").forEach { p ->
            val t = p.text().trim()
            if (t.isNotEmpty()) blocks += LessonContentBlock("paragraph", t)
        }
        article.select("a[href]").forEach { a ->
            val href = a.attr("href")
            if (href.isBlank()) return@forEach
            resources += LessonResource(
                title = a.text().trim().ifBlank { href },
                url = absoluteUrl(href),
                isFile = a.attr("data-lc-display-linktype") == "file",
            )
        }
    }

    private fun parseParticipants(doc: org.jsoup.nodes.Document): List<LessonParticipant> {
        val out = mutableListOf<LessonParticipant>()
        // Common Lectio participant tables / lists
        doc.select(
            "table[id*=Participant] tr, table[id*=Elev] tr, " +
                "[id*=participant] a[data-lectiocontextcard], a[data-lectiocontextcard]",
        ).forEachIndexed { i, el ->
            if (el.tagName() == "tr") {
                val cells = el.select("td")
                if (cells.size < 2) return@forEachIndexed
                val name = cells[1].text().trim().ifBlank { cells[0].text().trim() }
                if (name.isBlank() || name.equals("navn", true)) return@forEachIndexed
                out += LessonParticipant(id = "p-$i", name = name, role = cells.getOrNull(2)?.text()?.trim())
            } else {
                val name = el.text().trim()
                val id = el.attr("data-lectiocontextcard").ifBlank { "p-$i" }
                if (name.length in 2..80) {
                    out += LessonParticipant(id = id, name = name)
                }
            }
        }
        return out.distinctBy { it.name.lowercase() }.take(100)
    }

    private fun absoluteUrl(href: String): String =
        when {
            href.startsWith("http") -> href
            href.startsWith("/") -> "https://www.lectio.dk$href"
            else -> "https://www.lectio.dk/lectio/$href"
        }
}
