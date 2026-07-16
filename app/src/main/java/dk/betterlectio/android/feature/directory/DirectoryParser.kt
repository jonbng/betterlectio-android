package dk.betterlectio.android.feature.directory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Lectio directory parsers.
 *
 * Full catalog comes from the AvanceretSkema autocomplete dropdown JSON
 * (`FindSkemaAdv.aspx` → `cache/DropDown.aspx`), matching iOS [DirectoryParser]
 * and the extension [findskema-cache]. FindSkema letter pages only contain one
 * initial page and must not be used as the primary catalog source.
 */
object DirectoryParser {

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Lectio context-card ids: S/T + digits, or HE/RO/RE/GE/SC + digits. */
    private val PREFIXED_ID = Regex(
        "^(S|T|HE|RO|RE|GE|SC)(\\d+)$",
        RegexOption.IGNORE_CASE,
    )

    private val DROPDOWN_URL = Regex(
        """Autocomplete\.registerDataSetUrl\(\s*'AvanceretSkema_[^']*'\s*,\s*'([^']+)'""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Extract the relative DropDown URL from FindSkemaAdv.aspx HTML.
     * Example: `/lectio/94/cache/DropDown.aspx?type=AvanceretSkema&afdeling=…&subcache=…`
     */
    fun parseDropdownUrl(html: String): String? {
        val match = DROPDOWN_URL.find(html) ?: return null
        return match.groupValues[1].trim().ifBlank { null }
    }

    /**
     * Parse AvanceretSkema dropdown JSON into directory entities (full school catalog).
     * JSON shape: `{ "items": [ ["Name (class)", "S123", "", "11", " fs", null, true], … ] }`
     */
    fun parseDropdownJson(json: String): List<DirectoryEntity> {
        val root = try {
            jsonParser.parseToJsonElement(json).jsonObject
        } catch (_: Exception) {
            return emptyList()
        }
        val items = root["items"] as? JsonArray ?: return emptyList()
        val out = ArrayList<DirectoryEntity>(items.size)
        for (element in items) {
            val row = element as? JsonArray ?: continue
            parseDropdownRow(row)?.let { out += it }
        }
        return out.distinctBy { it.id }
    }

    private fun parseDropdownRow(row: JsonArray): DirectoryEntity? {
        if (row.size < 2) return null
        val rawLabel = row.jsonString(0)?.trim().orEmpty()
        val rawPrefixedId = row.jsonString(1)?.trim().orEmpty()
        if (rawLabel.isEmpty() || rawPrefixedId.isEmpty()) return null

        // Skip inactive / archived (field [2] == "i") — iOS StudentParser does the same.
        if (row.size > 2 && row.jsonString(2) == "i") return null

        val kind = kindForPrefixedId(rawPrefixedId) ?: return null
        return buildEntity(rawPrefixedId, rawLabel, kind)
    }

    private fun JsonArray.jsonString(index: Int): String? {
        if (index !in indices) return null
        return when (val el = this[index]) {
            is JsonNull -> null
            is JsonPrimitive -> el.contentOrNull
            else -> null
        }
    }

    /**
     * Strict FindSkema HTML list parse — only real Lectio context cards.
     * Used as a last-resort fallback; primary path is [parseDropdownJson].
     */
    fun parseFindList(html: String, kind: DirectoryEntityKind): List<DirectoryEntity> {
        val doc = Jsoup.parse(html)
        val items = mutableListOf<DirectoryEntity>()
        doc.select("a[data-lectiocontextcard], td[data-lectiocontextcard], [data-lectiocontextcard]").forEach { el ->
            val id = el.attr("data-lectiocontextcard").trim()
            if (!isValidPrefixedId(id)) return@forEach
            val inferred = kindForPrefixedId(id) ?: kind
            // When caller pins a kind, skip other entity types on mixed pages.
            if (inferred != kind) return@forEach
            val name = el.ownText().ifBlank { el.text() }.trim()
            if (name.length < 2) return@forEach
            if (looksLikeNavChrome(name)) return@forEach
            items += buildEntity(id, name, inferred)
        }
        return items.distinctBy { it.id }
    }

    /**
     * Parse hold/class member list HTML into student entities.
     * Prefer the Lectio members grid (`data-lectiocontextcard` on cells).
     *
     * When [gymId] is set, thumbnails with `pictureid=` are turned into full GetImage
     * avatar URLs (iOS [DirectoryParser.parseHoldMembers] parity).
     */
    fun parseMembers(
        html: String,
        parent: DirectoryEntity,
        gymId: Int? = null,
    ): List<DirectoryEntity> {
        val doc = Jsoup.parse(html)
        // Prefer the dedicated members panel when present (iOS parity).
        val panelRows = doc.select(
            "table#s_m_Content_Content_laerereleverpanel_alm_gv tr, " +
                "table[id*=laerereleverpanel] tr",
        )
        val fromPanel = panelRows.mapNotNull { row -> parseMemberRow(row, parent, gymId) }
        if (fromPanel.isNotEmpty()) {
            return fromPanel.distinctBy { it.id }
        }

        // Fallback: any context-card link/cell that is a student/teacher.
        // Do not early-return from generic tables — nav chrome sits in those too.
        return doc.select("[data-lectiocontextcard]")
            .mapNotNull { el ->
                val id = el.attr("data-lectiocontextcard").trim()
                if (!isValidPrefixedId(id)) return@mapNotNull null
                val kind = kindForPrefixedId(id) ?: return@mapNotNull null
                if (kind != DirectoryEntityKind.STUDENT && kind != DirectoryEntityKind.TEACHER) {
                    return@mapNotNull null
                }
                val name = el.ownText().ifBlank { el.text() }.trim()
                if (name.length < 2 || looksLikeNavChrome(name)) return@mapNotNull null
                val pictureId = extractPictureId(el.parent() ?: el)
                DirectoryEntity(
                    id = id,
                    name = cleanStudentName(parsePersonLabel(name).first),
                    kind = kind,
                    subtitle = parent.name,
                    avatarUrl = pictureId?.let { pid ->
                        gymId?.let { AvatarUrls.fromPictureId(it, pid) }
                    },
                )
            }
            .distinctBy { it.id }
    }

    private fun parseMemberRow(
        row: Element,
        parent: DirectoryEntity,
        gymId: Int?,
    ): DirectoryEntity? {
        val card = row.selectFirst("[data-lectiocontextcard]") ?: return null
        val id = card.attr("data-lectiocontextcard").trim()
        if (!isValidPrefixedId(id)) return null
        val kind = kindForPrefixedId(id) ?: return null
        if (kind != DirectoryEntityKind.STUDENT && kind != DirectoryEntityKind.TEACHER) return null
        val name = card.ownText().ifBlank { card.text() }.trim()
        if (name.length < 2 || looksLikeNavChrome(name)) return null
        val pictureId = extractPictureId(row)
        return DirectoryEntity(
            id = id,
            name = cleanStudentName(parsePersonLabel(name).first),
            kind = kind,
            subtitle = parent.name,
            avatarUrl = pictureId?.let { pid ->
                gymId?.let { AvatarUrls.fromPictureId(it, pid) }
            },
        )
    }

    /** Prefer row thumbnail `img[src*=pictureid]`. */
    fun extractPictureId(scope: Element): String? {
        val img = scope.selectFirst("img[src*=pictureid], img[src*=GetImage], img[src*=getimage]")
            ?: return null
        val src = img.attr("src")
        return AvatarUrls.pictureIdFromUrl(src)
    }

    /**
     * Merge two catalog snapshots by entity id (later list wins on id collision).
     * Preserves [DirectoryEntity.avatarUrl] when the incoming row has none.
     */
    fun mergeCatalog(
        existing: List<DirectoryEntity>,
        incoming: List<DirectoryEntity>,
    ): List<DirectoryEntity> {
        val map = linkedMapOf<String, DirectoryEntity>()
        existing.forEach { map[it.id] = it }
        incoming.forEach { e ->
            val prev = map[e.id]
            map[e.id] = if (e.avatarUrl.isNullOrBlank() && !prev?.avatarUrl.isNullOrBlank()) {
                e.copy(avatarUrl = prev!!.avatarUrl)
            } else {
                e
            }
        }
        return map.values.toList()
    }

    /** Strip letter prefix from Lectio ids (`HE123` → `123`, `S456` → `456`). */
    fun numericId(prefixedId: String): String =
        prefixedId.dropWhile { !it.isDigit() }

    fun isValidPrefixedId(id: String): Boolean =
        PREFIXED_ID.matches(id.trim())

    fun kindForPrefixedId(rawId: String): DirectoryEntityKind? {
        val id = rawId.trim()
        if (!isValidPrefixedId(id)) return null
        val prefix = PREFIXED_ID.matchEntire(id)?.groupValues?.get(1)?.uppercase() ?: return null
        return when (prefix) {
            "S" -> DirectoryEntityKind.STUDENT
            "T" -> DirectoryEntityKind.TEACHER
            "HE" -> DirectoryEntityKind.HOLD
            "RO" -> DirectoryEntityKind.ROOM
            "RE" -> DirectoryEntityKind.RESOURCE
            "GE" -> DirectoryEntityKind.GROUP
            "SC" -> DirectoryEntityKind.CLASS
            else -> DirectoryEntityKind.OTHER
        }
    }

    private fun buildEntity(
        rawPrefixedId: String,
        rawLabel: String,
        kind: DirectoryEntityKind,
    ): DirectoryEntity {
        val (namePart, info) = parsePersonLabel(rawLabel)
        return when (kind) {
            DirectoryEntityKind.STUDENT -> {
                val name = cleanStudentName(namePart)
                val subtitle = studentClassSubtitle(info)
                DirectoryEntity(rawPrefixedId, name, kind, subtitle)
            }
            DirectoryEntityKind.TEACHER -> {
                DirectoryEntity(
                    rawPrefixedId,
                    namePart.ifBlank { rawLabel },
                    kind,
                    info?.takeIf { it.isNotBlank() },
                )
            }
            DirectoryEntityKind.ROOM, DirectoryEntityKind.RESOURCE -> {
                val short = rawLabel.substringBefore(" - ").trim().ifBlank { null }
                DirectoryEntity(
                    rawPrefixedId,
                    rawLabel,
                    kind,
                    short?.takeIf { it != rawLabel },
                )
            }
            DirectoryEntityKind.HOLD, DirectoryEntityKind.GROUP, DirectoryEntityKind.CLASS -> {
                DirectoryEntity(
                    rawPrefixedId,
                    rawLabel,
                    kind,
                    info?.takeIf { it.isNotBlank() },
                )
            }
            DirectoryEntityKind.OTHER -> {
                DirectoryEntity(rawPrefixedId, rawLabel, kind, null)
            }
        }
    }

    private fun parsePersonLabel(label: String): Pair<String, String?> {
        val open = label.lastIndexOf('(')
        val close = label.lastIndexOf(')')
        if (open < 0 || close <= open) {
            return label.trim() to null
        }
        val name = label.substring(0, open).trim()
        val info = label.substring(open + 1, close).trim()
        return name to info.ifBlank { null }
    }

    private fun cleanStudentName(name: String): String {
        var cleaned = name.trim()
        if (cleaned.endsWith("(k)")) {
            cleaned = cleaned.dropLast(3).trim()
        }
        return cleaned
    }

    /** Active student info is typically `"3x 12"` → class `3x`. */
    private fun studentClassSubtitle(info: String?): String? {
        if (info.isNullOrBlank()) return null
        val parts = info.split(Regex("\\s+")).filter { it.isNotBlank() }
        val first = parts.firstOrNull() ?: return null
        return if (first.firstOrNull()?.isDigit() == true) first else null
    }

    /**
     * Lectio / material-icon chrome that used to leak in via loose HTML scraping
     * (e.g. "hjælp", "import_contactsBøger", "starKarakterer").
     */
    fun looksLikeNavChrome(name: String): Boolean {
        val t = name.trim()
        if (t.length < 2) return true
        // Material icon name + label mashed together
        if (t.contains('_')) return true
        val lower = t.lowercase()
        if (lower in NAV_SINGLE_WORDS) return true
        if (NAV_PREFIXES.any { lower.startsWith(it) && lower.length < 40 }) {
            // "starKarakterer", "import_contactsBøger" style camel/snake mashups
            if (t.any { it.isUpperCase() } && t.any { it.isLowerCase() } && !t.contains(' ')) {
                return true
            }
        }
        return false
    }

    private val NAV_SINGLE_WORDS = setOf(
        "hjælp", "hjaelp", "help", "søg", "soeg", "search", "menu", "luk", "close",
        "tilbage", "back", "home", "forside", "log ud", "logud", "logout",
    )

    private val NAV_PREFIXES = listOf(
        "star", "import", "export", "settings", "help", "search", "menu",
        "person", "group", "school", "book", "grade", "mail", "chat",
    )
}
