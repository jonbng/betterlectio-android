package dk.betterlectio.android.feature.directory

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Pure parsers for Lectio room list + current occupancy pages.
 * Flutter parity: lectio_wrapper rooms/scraping.dart
 */
object RoomParser {

    data class RoomAvailability(
        val shortName: String,
        val name: String,
        val inUse: Boolean,
    )

    data class RoomListItem(
        val id: String,
        val shortName: String,
        val name: String,
    )

    data class RoomWithOccupancy(
        val id: String,
        val shortName: String,
        val name: String,
        val inUse: Boolean?,
    )

    /**
     * Parse `SkemaAvanceret.aspx?type=aktuelleallelokaler` occupancy island.
     * A room is **in use** when its booking table does **not** contain "Der er ingen data".
     */
    fun parseAvailabilities(html: String): List<RoomAvailability> {
        val doc = Jsoup.parse(html)
        val island = doc.getElementById("m_Content_LectioDetailIsland1_pa")
            ?: doc.selectFirst("[id*=LectioDetailIsland]")
            ?: return emptyList()
        val rooms = mutableListOf<RoomAvailability>()
        island.children().forEach { row ->
            if (!row.id().startsWith("printSingleControl") &&
                !row.id().contains("printSingle", ignoreCase = true) &&
                row.selectFirst("h2") == null
            ) {
                // Also accept direct h2 children blocks without the print id
                if (row.selectFirst("h2") == null && row.select("h2").isEmpty()) return@forEach
            }
            parseAvailabilityRow(row)?.let { rooms += it }
        }
        // Fallback: any printSingleControl descendant or h2+table pairs
        if (rooms.isEmpty()) {
            doc.select("[id^=printSingleControl], [id*=printSingleControl]").forEach { row ->
                parseAvailabilityRow(row)?.let { rooms += it }
            }
        }
        if (rooms.isEmpty()) {
            doc.select("h2").forEach { h2 ->
                val parent = h2.parent() ?: return@forEach
                parseAvailabilityFromHeader(h2, parent)?.let { rooms += it }
            }
        }
        return rooms.distinctBy { it.name.lowercase() to it.shortName.lowercase() }
    }

    private fun parseAvailabilityRow(row: Element): RoomAvailability? {
        row.selectFirst("h2")?.let { return parseAvailabilityFromHeader(it, row) }

        val text = row.selectFirst("span")?.text()
            ?.replace(Regex("^Lokale\\s*:?\\s*", RegexOption.IGNORE_CASE), "")
            ?.trim()
            .orEmpty()
        if (text.isBlank()) return null
        val parts = splitRoomHeader(text)
        val booking = row.selectFirst("table") ?: return null
        return RoomAvailability(
            shortName = parts.first,
            name = parts.second,
            inUse = !booking.text().contains("Der er ingen data", ignoreCase = true),
        )
    }

    private fun parseAvailabilityFromHeader(header: Element, container: Element): RoomAvailability? {
        val text = header.text().trim()
        val (short, name) = splitRoomHeader(text)
        if (short.isBlank()) return null
        val booking = container.selectFirst("table")
        val notUsed = booking == null || booking.text().contains("Der er ingen data")
        return RoomAvailability(shortName = short, name = name, inUse = !notUsed)
    }

    private fun splitRoomHeader(text: String): Pair<String, String> {
        val match = Regex("\\s[-–—]\\s*").find(text) ?: return text.trim() to text.trim()
        val short = text.substring(0, match.range.first).trim()
        val name = text.substring(match.range.last + 1).trim()
        return short to name
    }

    /**
     * Parse `FindSkema.aspx?type=lokale` room list.
     */
    fun parseRooms(html: String): List<RoomListItem> {
        val doc = Jsoup.parse(html)
        val container = doc.getElementById("m_Content_listecontainer")
            ?: doc.selectFirst("[id*=listecontainer]")
        val items = mutableListOf<RoomListItem>()
        if (container != null) {
            val rows = container.select("tr, li, a[href*=lokale], a[href*=type=lokale]")
            for (row in rows) {
                parseRoomRow(row)?.let { items += it }
            }
        }
        if (items.isEmpty()) {
            doc.select("a[href*=type=lokale], a[href*=lokale&], a[href*=id=]").forEach { a ->
                parseRoomAnchor(a)?.let { items += it }
            }
        }
        return items.distinctBy { it.id }.take(500)
    }

    private fun parseRoomRow(row: Element): RoomListItem? {
        val a = if (row.tagName() == "a") row else row.selectFirst("a[href]")
        if (a != null) return parseRoomAnchor(a)
        return null
    }

    private fun parseRoomAnchor(a: Element): RoomListItem? {
        val href = a.attr("href")
        val id = AspNetQueries.idFromHref(href) ?: return null
        val full = a.text().trim()
        if (full.isBlank()) return null
        // First token often short code (e.g. "201 Bygning A")
        val parts = full.split(Regex("\\s+"), limit = 2)
        val short = parts[0]
        val name = parts.getOrNull(1)?.ifBlank { short } ?: short
        return RoomListItem(id = id, shortName = short, name = name)
    }

    /**
     * Join room list with availability by matching display name (Flutter behavior).
     */
    fun mergeOccupancy(
        rooms: List<RoomListItem>,
        availabilities: List<RoomAvailability>,
    ): List<RoomWithOccupancy> {
        val byKey = mutableMapOf<String, RoomAvailability>()
        availabilities.forEach { availability ->
            roomKeys(availability.shortName, availability.name).forEach { key ->
                byKey.putIfAbsent(key, availability)
            }
        }
        return rooms.map { room ->
            val match = roomKeys(room.shortName, room.name).firstNotNullOfOrNull(byKey::get)
            RoomWithOccupancy(
                id = room.id,
                shortName = room.shortName,
                name = room.name,
                inUse = match?.inUse,
            )
        }
    }

    private fun roomKeys(shortName: String, name: String): Set<String> = buildSet {
        listOf(shortName, name, "$shortName $name").forEach { value ->
            val normalized = value
                .replace('\u00a0', ' ')
                .replace(Regex("^lokale\\s*:?\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*[-–—]\\s*"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .lowercase()
            if (normalized.isNotEmpty()) {
                add(normalized)
                add(normalized.replace(" ", ""))
            }
        }
    }
}

/** Minimal query-string helper kept local to avoid coupling RoomParser to AspNetForm for id only. */
internal object AspNetQueries {
    fun idFromHref(href: String?): String? {
        if (href.isNullOrBlank()) return null
        val qIndex = href.indexOf('?')
        val query = if (qIndex >= 0) href.substring(qIndex + 1) else href
        return query.split('&')
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null
                else part.substring(0, eq) to part.substring(eq + 1)
            }
            .firstOrNull { it.first.equals("id", ignoreCase = true) }
            ?.second
            ?.takeIf { it.isNotBlank() }
    }
}
