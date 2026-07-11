package dk.betterlectio.android.feature.schedule

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Lesson detail page (aktivitetforside2.aspx).
 * iOS: [ScheduleParser.parseLessonContent] — homeworkContentContainer, ACH articles, sections.
 * Flutter: events/scraping ActNote + raw content.
 */
object LessonDetailParser {

    fun parse(html: String, eventId: String, fallbackTitle: String = ""): LessonDetail {
        val doc = Jsoup.parse(html)

        // iOS: textarea.activity-note inside #homeworkContentContainer (before empty check)
        // Flutter: #s_m_Content_Content_tocAndToolbar_ActNoteTB_tb
        val homeworkContainer = doc.selectFirst("#homeworkContentContainer")
        val note = homeworkContainer?.selectFirst("textarea.activity-note")
            ?.text()?.trim()?.ifBlank { null }
            ?: doc.getElementById("s_m_Content_Content_tocAndToolbar_ActNoteTB_tb")
                ?.text()?.trim()?.ifBlank { null }
            ?: doc.selectFirst("textarea[id*=ActNote], #m_Content_commentTextBox_tb")
                ?.text()?.trim()?.ifBlank { null }

        val contentRoot = doc.getElementById("s_m_Content_Content_tocAndToolbar_inlineHomeworkDiv")
            ?: homeworkContainer?.selectFirst("[id*=inlineHomework]")
            ?: doc.selectFirst("[id*=inlineHomework], [id*=tocAndToolbar]")
            ?: homeworkContainer

        val blocks = mutableListOf<LessonContentBlock>()
        val resources = mutableListOf<LessonResource>()
        var homework: String? = null
        var sectionIsHomework = false

        if (contentRoot != null) {
            val empty = contentRoot.text().contains("ikke noget indhold", ignoreCase = true)
            if (!empty) {
                // Walk children for section headers + ACH articles (iOS)
                for (child in contentRoot.children()) {
                    val tag = child.tagName().lowercase()
                    val text = child.text().trim()
                    when {
                        tag.matches(Regex("h[1-6]")) || child.hasClass("section-header") -> {
                            when {
                                text.contains("Lektier", ignoreCase = true) -> sectionIsHomework = true
                                text.contains("Øvrigt", ignoreCase = true) -> sectionIsHomework = false
                            }
                            if (text.isNotEmpty()) {
                                blocks += LessonContentBlock(kind = "heading", text = text, isHomework = sectionIsHomework)
                            }
                        }
                        tag == "hr" -> blocks += LessonContentBlock(kind = "divider", text = "", isHomework = sectionIsHomework)
                        tag == "article" || child.hasClass("ls-paper") || child.hasClass("activity-content") ||
                            child.id().startsWith("ACH") || child.hasClass("lc-display-fragment") -> {
                            parseArticle(child, blocks, resources, sectionIsHomework)
                        }
                    }
                }
                if (blocks.isEmpty()) {
                    contentRoot.select("article, .ls-paper, .activity-content, [id^=ACH]").forEach { article ->
                        parseArticle(article, blocks, resources, sectionIsHomework)
                    }
                }
                if (blocks.isEmpty()) {
                    contentRoot.select("p, h1, h2, h3, li, blockquote").forEach { el ->
                        val t = el.text().trim()
                        if (t.isNotEmpty()) {
                            blocks += LessonContentBlock(
                                kind = when {
                                    el.tagName().startsWith("h") -> "heading"
                                    el.tagName() == "blockquote" -> "note"
                                    else -> "paragraph"
                                },
                                text = t,
                                isHomework = sectionIsHomework,
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
                        href.contains("document", ignoreCase = true)
                    if (isFile || href.startsWith("http") || href.startsWith("/")) {
                        resources += LessonResource(
                            title = title,
                            url = absoluteUrl(href),
                            isFile = isFile,
                        )
                    }
                }
                // Images (skip Lectio chrome icons)
                contentRoot.select("img[src]").forEach { img ->
                    val src = img.attr("src")
                    if (src.isBlank() || src.contains("/lectio/img/", ignoreCase = true)) return@forEach
                    blocks += LessonContentBlock(
                        kind = "image",
                        text = img.attr("alt").ifBlank { "Billede" },
                        url = absoluteUrl(src),
                        isHomework = sectionIsHomework,
                    )
                }
                homework = blocks.filter { it.isHomework && it.kind != "heading" && it.kind != "divider" }
                    .joinToString("\n") { it.text }
                    .ifBlank { null }
            }
        }

        val participants = parseParticipants(doc)
        val title = doc.selectFirst("#s_m_Content_Content_ActivityTitle, .ls-activity-title, h1")
            ?.text()?.trim()?.ifBlank { fallbackTitle } ?: fallbackTitle

        return LessonDetail(
            eventId = eventId,
            title = title,
            note = note,
            homework = homework,
            contentBlocks = blocks.distinctBy { it.kind + it.text + (it.url ?: "") },
            participants = participants,
            resources = resources.distinctBy { it.url },
        )
    }

    private fun parseArticle(
        article: Element,
        blocks: MutableList<LessonContentBlock>,
        resources: MutableList<LessonResource>,
        isHomework: Boolean = false,
    ) {
        // iOS: style classes doc-homework / doc-not-homework
        val articleHw = when {
            article.className().contains("doc-homework") -> true
            article.className().contains("doc-not-homework") -> false
            else -> isHomework
        }
        article.select("h1, h2, h3, h4, .ls-paper-header").forEach { h ->
            val t = h.text().trim()
            if (t.isNotEmpty()) blocks += LessonContentBlock("heading", t, isHomework = articleHw)
        }
        article.select("p, .ls-paper-content, li").forEach { p ->
            val t = p.text().trim()
            if (t.isNotEmpty()) blocks += LessonContentBlock("paragraph", t, isHomework = articleHw)
        }
        article.select("blockquote, [data-lc-role=note]").forEach { b ->
            val t = b.text().trim()
            if (t.isNotEmpty()) blocks += LessonContentBlock("note", t, isHomework = articleHw)
        }
        article.select("a[href]").forEach { a ->
            val href = a.attr("href")
            if (href.isBlank()) return@forEach
            resources += LessonResource(
                title = a.text().trim().ifBlank { href },
                url = absoluteUrl(href),
                isFile = href.contains("GetFile", ignoreCase = true) ||
                    href.contains("document", ignoreCase = true),
            )
        }
        article.select("img[src]").forEach { img ->
            val src = img.attr("src")
            if (src.isBlank() || src.contains("/lectio/img/", ignoreCase = true)) return@forEach
            blocks += LessonContentBlock(
                kind = "image",
                text = img.attr("alt").ifBlank { "Billede" },
                url = absoluteUrl(src),
                isHomework = articleHw,
            )
        }
        article.select("hr").forEach {
            blocks += LessonContentBlock("divider", "", isHomework = articleHw)
        }
    }

    private fun parseParticipants(doc: org.jsoup.nodes.Document): List<LessonParticipant> {
        val out = mutableListOf<LessonParticipant>()
        // Prefer explicit participant tables (incl. fixture m_Content_Participants)
        val rows = doc.select(
            "table[id*=Elev] tr, table[id*=deltag] tr, table[id*=Participant] tr, table[id*=participant] tr",
        )
        for (el in rows) {
            val cells = el.select("td")
            if (cells.isEmpty()) continue
            // Skip leading index/number cells; pick first name-like cell
            val name = cells.map { it.text().trim() }
                .firstOrNull { it.isNotEmpty() && !it.matches(Regex("""^\d+$""")) }
                ?: continue
            val role = cells.getOrNull(cells.size - 1)?.text()?.trim()
                ?.takeIf { it != name && !it.matches(Regex("""^\d+$""")) }
            val id = el.selectFirst("[data-lectiocontextcard]")?.attr("data-lectiocontextcard")
                ?: name
            out += LessonParticipant(id = id, name = name, role = role)
        }
        if (out.isEmpty()) {
            doc.select("[data-lectiocontextcard]").forEach { el ->
                val card = el.attr("data-lectiocontextcard")
                if (card.isBlank()) return@forEach
                val name = el.text().trim()
                if (name.isNotEmpty()) out += LessonParticipant(id = card, name = name)
            }
        }
        return out.distinctBy { it.id }.take(80)
    }

    private fun absoluteUrl(href: String): String = when {
        href.startsWith("http") -> href
        href.startsWith("//") -> "https:$href"
        href.startsWith("/") -> "https://www.lectio.dk$href"
        else -> "https://www.lectio.dk/$href"
    }
}
