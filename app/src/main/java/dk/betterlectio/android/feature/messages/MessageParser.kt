package dk.betterlectio.android.feature.messages

import dk.betterlectio.android.core.util.LectioDateUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object MessageParser {

    fun parseThreadList(html: String, fallbackFolderId: String = MessageFolder.NEWEST.id): List<MessageThread> {
        val doc = Jsoup.parse(html)
        val folderId = doc.getElementById("s_m_Content_Content_ListGridSelectionTree_folders")
            ?.attr("value")
            ?.ifBlank { null }
            ?: fallbackFolderId

        val table = doc.selectFirst("table#s_m_Content_Content_threadGV_ctl00")
            ?: doc.selectFirst("table[id*=threadGV]")
            ?: return emptyList()

        val rows = table.select("tr").drop(1)
        return rows.mapNotNull { row -> parseThreadRow(row, folderId) }
    }

    private fun parseThreadRow(row: Element, folderId: String): MessageThread? {
        val cells = row.select("td")
        if (cells.size < 4) return null

        val link = cells.mapNotNull { it.selectFirst("a") }.firstOrNull() ?: return null
        val onclick = link.attr("onclick")
        val href = link.attr("href")
        val id = extractThreadId(onclick, href) ?: return null
        val topic = link.text().trim().ifBlank { cells.getOrNull(3)?.text()?.trim().orEmpty() }
        if (topic.isBlank()) return null

        val sender = cells.getOrNull(5)?.selectFirst("[title]")?.attr("title")
            ?: cells.getOrNull(5)?.text()?.trim()
            ?: cells.getOrNull(4)?.text()?.trim()
            ?: ""

        val dateRaw = cells.getOrNull(7)?.text()?.trim()
            ?: cells.lastOrNull()?.text()?.trim()
            ?: ""
        val date = LectioDateUtils.parseLectioDate(dateRaw)

        val unread = row.hasClass("ulæst") || row.className().contains("unread", true) ||
            cells.any { it.text().contains("Ulæst", true) }

        return MessageThread(
            id = id,
            topic = topic,
            sender = sender,
            dateChanged = date,
            folderId = folderId,
            normalizedId = normalizeThreadId(id),
            unread = unread || folderId == MessageFolder.UNREAD.id,
        )
    }

    /**
     * Lectio control ids look like `$ABC_$_42`. Flutter uses the segment after `_$_`.
     * (Not [substringAfterLast] of `_$`, which incorrectly yields `_42`.)
     */
    fun normalizeThreadId(id: String): String {
        val marker = "_\$_"
        val idx = id.indexOf(marker)
        if (idx < 0) return id
        return id.substring(idx + marker.length).ifBlank { id }
    }

    fun parseThreadDetail(html: String, ref: MessageThread): MessageThreadDetail {
        val doc = Jsoup.parse(html)
        val receivers = doc.select("#s_m_Content_Content_MessageThreadCtrl_RecipientsReadMode span")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }

        val entries = doc.select("#GridRowMessage, .message-thread-message").mapIndexed { index, el ->
            val topic = el.selectFirst(".message-thread-message-header")?.text()?.trim()
            val contentEl = el.selectFirst(".message-thread-message-content")
            val attachments = parseAttachments(contentEl)
            contentEl?.select(".message-attachements, .message-attachments")?.remove()
            val contentHtml = contentEl?.html()?.trim()
            val sender = el.selectFirst(".message-thread-message-sender span")?.text()?.trim()
                ?: el.selectFirst(".message-thread-message-sender")?.text()?.trim()
            val infoText = el.selectFirst(".message-thread-message-sender")?.text().orEmpty()
            val sentAt = LectioDateUtils.parseLectioDate(infoText)
            ThreadEntry(
                id = "${ref.id}_$index",
                topic = topic,
                contentHtml = contentHtml,
                senderName = sender,
                sentAt = sentAt,
                attachments = attachments,
            )
        }

        return MessageThreadDetail(
            thread = ref,
            entries = entries.ifEmpty {
                val contentEl = doc.selectFirst(
                    "#s_m_Content_Content_MessageThreadCtrl_MessageContent, .message-thread-message-content",
                )
                listOf(
                    ThreadEntry(
                        id = ref.id,
                        topic = ref.topic,
                        contentHtml = contentEl?.html(),
                        senderName = ref.sender,
                        sentAt = ref.dateChanged,
                        attachments = parseAttachments(contentEl),
                    ),
                )
            },
            receivers = receivers,
        )
    }

    /** Extract attachment name+href pairs from message HTML. */
    fun parseAttachments(root: Element?): List<MessageAttachment> {
        if (root == null) return emptyList()
        return root.select(".message-attachements a, .message-attachments a, a[href*=GetFile], a[href*=documentid]")
            .mapNotNull { a ->
                val href = a.attr("href").trim()
                if (href.isEmpty()) return@mapNotNull null
                val name = a.text().trim().ifBlank { href.substringAfterLast('/') }
                val url = when {
                    href.startsWith("http") -> href
                    href.startsWith("/") -> "https://www.lectio.dk$href"
                    else -> "https://www.lectio.dk/$href"
                }
                MessageAttachment(name = name, url = url)
            }
            .distinctBy { it.url }
    }

    fun parseAttachmentsFromHtml(html: String): List<MessageAttachment> =
        parseAttachments(Jsoup.parse(html).body())

    private fun extractThreadId(onclick: String, href: String): String? {
        val dollar = onclick.indexOf('$')
        if (dollar >= 0) {
            val end = onclick.indexOf('\'', dollar).takeIf { it > dollar }
                ?: onclick.indexOf('"', dollar).takeIf { it > dollar }
            if (end != null) {
                return onclick.substring(dollar, end)
            }
        }
        Regex("""threadid=([^&'"]+)""", RegexOption.IGNORE_CASE).find(href)?.let {
            return it.groupValues[1]
        }
        Regex("""['"](\$[^'"]+)['"]""").find(onclick)?.let { return it.groupValues[1] }
        return null
    }
}
