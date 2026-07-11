package dk.betterlectio.android.feature.messages

import dk.betterlectio.android.core.util.LectioDateUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Message list + thread detail.
 * Thread id normalization: Flutter (`_$_` segment).
 * List flags / mobile layout: iOS MessageParser.
 * Detail timestamps: strip sender name before parse (Flutter/iOS).
 */
object MessageParser {

    fun parseThreadList(html: String, fallbackFolderId: String = MessageFolder.NEWEST.id): List<MessageThread> {
        val doc = Jsoup.parse(html)
        val folderId = doc.getElementById("s_m_Content_Content_ListGridSelectionTree_folders")
            ?.attr("value")
            ?.ifBlank { null }
            ?: fallbackFolderId

        // Desktop tables first (Flutter exact + iOS fuzzy), then iOS mobile layouts
        val table = doc.selectFirst("table#s_m_Content_Content_threadGV_ctl00")
            ?: doc.selectFirst("table[id*=threadGV]")
            ?: doc.selectFirst("table.ls-table-layout5")

        if (table != null) {
            val rows = table.select("tr").drop(1)
            val fromTable = rows.mapNotNull { row -> parseThreadRow(row, folderId) }
            if (fromTable.isNotEmpty()) return fromTable
        }

        // iOS mobile: div.message-list-thread-container
        val mobile = doc.select("div.message-list-thread-container")
        if (mobile.isNotEmpty()) {
            return mobile.mapNotNull { parseMobileThread(it, folderId) }
        }

        return emptyList()
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

        // iOS: row class "unread"; also Danish class / text heuristics
        val unread = row.hasClass("ulæst") ||
            row.className().contains("unread", true) ||
            cells.any { it.text().contains("Ulæst", true) }

        // iOS: img[title*=flag] with flagon src
        val flagged = row.select("img[title*=flag], img[title*=Flag]").any {
            it.attr("src").contains("flagon", ignoreCase = true)
        }

        return MessageThread(
            id = id,
            topic = topic,
            sender = sender,
            dateChanged = date,
            folderId = folderId,
            normalizedId = normalizeThreadId(id),
            unread = unread,
            flagged = flagged,
        )
    }

    private fun parseMobileThread(container: Element, folderId: String): MessageThread? {
        val link = container.selectFirst("a[id*=EmneMobil], a") ?: return null
        val onclick = link.attr("onclick")
        val href = link.attr("href")
        val id = extractThreadId(onclick, href) ?: return null
        val topic = link.text().trim()
        if (topic.isBlank()) return null
        val sender = container.selectFirst("span[title]")?.attr("title")
            ?: container.selectFirst(".message-list-sender, span")?.text()?.trim()
            ?: ""
        val dateRaw = container.selectFirst(".message-list-date, .lpm-datetime, div")?.text()?.trim().orEmpty()
        val date = LectioDateUtils.parseLectioDate(dateRaw)
        val unread = container.className().contains("unread", true) ||
            container.hasClass("ulæst")
        val flagged = container.select("img[title*=flag]").any {
            it.attr("src").contains("flagon", ignoreCase = true)
        }
        return MessageThread(
            id = id,
            topic = topic,
            sender = sender,
            dateChanged = date,
            folderId = folderId,
            normalizedId = normalizeThreadId(id),
            unread = unread,
            flagged = flagged,
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
            .mapNotNull { span ->
                val name = span.text().trim()
                if (name.isEmpty()) null
                else name
            }

        // Prefer MessagesGV rows (iOS), skip textareas; also accept GridRowMessage (Flutter)
        val entryEls = buildList {
            val gv = doc.selectFirst("table[id*=MessagesGV]")
            if (gv != null) {
                for (tr in gv.select("tr")) {
                    if (tr.select("textarea").isNotEmpty()) continue
                    val content = tr.selectFirst(".message-thread-message-content, #GridRowMessage, .message-thread-message")
                        ?: tr.selectFirst("td")
                    if (content != null && tr.selectFirst(".message-thread-message-content, .message-thread-message-sender") != null) {
                        add(tr)
                    }
                }
            }
            if (isEmpty()) {
                addAll(doc.select("#GridRowMessage, .message-thread-message"))
            }
        }

        val entries = entryEls.mapIndexed { index, el ->
            val topic = el.selectFirst(".message-thread-message-header")?.text()?.trim()
            val contentEl = el.selectFirst(".message-thread-message-content")
            val attachments = parseAttachments(contentEl)
            contentEl?.select(".message-attachements, .message-attachments")?.remove()
            var contentHtml = contentEl?.html()?.trim()
            contentHtml = stripAppSignatures(contentHtml)

            val senderSpan = el.selectFirst(".message-thread-message-sender span")
            val senderBlock = el.selectFirst(".message-thread-message-sender")
            val sender = senderSpan?.text()?.trim()
                ?: senderBlock?.text()?.trim()?.substringBefore(',')?.trim()
            val infoText = senderBlock?.text().orEmpty()
            val sentAt = parseMessageTimestamp(infoText, sender)

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
                        contentHtml = stripAppSignatures(contentEl?.html()),
                        senderName = ref.sender,
                        sentAt = ref.dateChanged,
                        attachments = parseAttachments(contentEl),
                    ),
                )
            },
            receivers = receivers,
        )
    }

    /**
     * Flutter/iOS: `"Name (class), 04-03-2026 11:05:41"` → parse timestamp only.
     */
    internal fun parseMessageTimestamp(infoText: String, senderName: String?): java.time.LocalDateTime? {
        val t = infoText.trim()
        if (t.isEmpty()) return null
        // Prefer text after last comma (iOS split on ", ")
        val afterComma = t.substringAfterLast(',').trim()
        LectioDateUtils.parseLectioDate(afterComma)?.let { return it }
        if (!senderName.isNullOrBlank()) {
            val stripped = t.removePrefix(senderName).removePrefix(",").trim()
            LectioDateUtils.parseLectioDate(stripped)?.let { return it }
        }
        return LectioDateUtils.parseLectioDate(t)
    }

    /** iOS signature cleanup for BetterLectio / Flutter footers. */
    private fun stripAppSignatures(html: String?): String? {
        if (html.isNullOrBlank()) return html
        var out: String = html
        listOf(
            "sendt med betterlectio",
            "sendt med BetterLectio",
            "Sendt med BetterLectio",
            "Sendt fra BetterLectio",
        ).forEach { sig ->
            out = out.replace(sig, "", ignoreCase = true)
        }
        return out.trim().ifBlank { null }
    }

    /** Extract attachment name+href pairs from message HTML. */
    fun parseAttachments(root: Element?): List<MessageAttachment> {
        if (root == null) return emptyList()
        // Prefer attachment blocks (Flutter typo + correct spelling); fallback GetFile/document
        val fromBlock = root.select(".message-attachements a, .message-attachments a")
        val links = if (fromBlock.isNotEmpty()) {
            fromBlock
        } else {
            root.select("a[href*=GetFile], a[href*=documentid], a[href*=document]")
        }
        return links
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

    /**
     * iOS parseMessageFolders — custom folders from ListGridSelectionTree postbacks / mobile select.
     */
    fun parseMessageFolders(html: String): List<MessageFolder> {
        val doc = Jsoup.parse(html)
        val folders = MessageFolder.defaults.toMutableList()
        val seen = folders.map { it.id }.toMutableSet()

        // Anchors with __doPostBack(..., 'folderId')
        doc.select("a[href*=ListGridSelectionTree], a[onclick*=ListGridSelectionTree]").forEach { a ->
            val onclick = a.attr("onclick") + a.attr("href")
            val m = Regex("""['"](-?\d+)['"]\s*\)""").findAll(onclick).lastOrNull()
                ?: Regex("""folders['"]?\s*,\s*['"](-?\d+)""").find(onclick)
            val id = m?.groupValues?.get(1) ?: return@forEach
            if (id in seen) return@forEach
            val name = a.text().trim().ifBlank { return@forEach }
            seen += id
            folders += MessageFolder(id = id, displayName = name)
        }

        doc.select("select[id*=ListGridSelectionTree] option, select[name*=ListGridSelectionTree] option")
            .forEach { opt ->
                val id = opt.attr("value").trim()
                if (id.isEmpty() || id in seen) return@forEach
                val name = opt.text().trim().ifBlank { return@forEach }
                seen += id
                folders += MessageFolder(id = id, displayName = name)
            }

        return folders
    }

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
