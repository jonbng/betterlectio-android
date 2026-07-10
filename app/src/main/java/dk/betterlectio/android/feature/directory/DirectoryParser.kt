package dk.betterlectio.android.feature.directory

import org.jsoup.Jsoup

object DirectoryParser {
    fun parseFindList(html: String, kind: DirectoryEntityKind): List<DirectoryEntity> {
        val doc = Jsoup.parse(html)
        val items = mutableListOf<DirectoryEntity>()
        // Lectio find pages often use tables or ul with links + context cards
        doc.select("a[data-lectiocontextcard], a[href*=Find], table tr a").forEach { a ->
            val name = a.text().trim()
            if (name.isBlank() || name.length < 2) return@forEach
            val id = a.attr("data-lectiocontextcard").ifBlank {
                a.attr("href")
            }
            items += DirectoryEntity(id = id.ifBlank { name }, name = name, kind = kind)
        }
        // Also parse simple list items
        if (items.isEmpty()) {
            doc.select("li, .list-item, tr").forEachIndexed { i, el ->
                val t = el.text().trim()
                if (t.length in 2..80) {
                    items += DirectoryEntity("item-$i", t, kind)
                }
            }
        }
        return items.distinctBy { it.name.lowercase() }.take(500)
    }

    /**
     * Parse hold/class member list HTML into student entities.
     * Used by [DirectoryRepository] and [DirectorySyncService] bootstrap.
     */
    fun parseMembers(html: String, parent: DirectoryEntity): List<DirectoryEntity> {
        val doc = Jsoup.parse(html)
        return doc.select("a[data-lectiocontextcard], table tr a, li a")
            .mapIndexedNotNull { i, a ->
                val name = a.text().trim()
                if (name.length < 2) return@mapIndexedNotNull null
                DirectoryEntity(
                    id = a.attr("data-lectiocontextcard").ifBlank { "m-$i" },
                    name = name,
                    kind = DirectoryEntityKind.STUDENT,
                    subtitle = parent.name,
                )
            }
            .distinctBy { it.name.lowercase() }
            .take(200)
    }

    /**
     * Merge two catalog snapshots by entity id (later list wins on id collision).
     */
    fun mergeCatalog(
        existing: List<DirectoryEntity>,
        incoming: List<DirectoryEntity>,
    ): List<DirectoryEntity> {
        val map = linkedMapOf<String, DirectoryEntity>()
        existing.forEach { map[it.id] = it }
        incoming.forEach { map[it.id] = it }
        return map.values.toList()
    }
}
