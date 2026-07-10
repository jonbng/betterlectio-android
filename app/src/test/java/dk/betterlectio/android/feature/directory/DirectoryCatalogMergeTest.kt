package dk.betterlectio.android.feature.directory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryCatalogMergeTest {

    @Test
    fun parseMembers_extracts_students_with_parent_subtitle() {
        val html = """
            <html><body>
            <table>
              <tr><td><a data-lectiocontextcard="S10">Anna Andersen</a></td></tr>
              <tr><td><a data-lectiocontextcard="S11">Bo Berg</a></td></tr>
            </table>
            </body></html>
        """.trimIndent()
        val parent = DirectoryEntity("H1", "3x Ma", DirectoryEntityKind.HOLD)
        val members = DirectoryParser.parseMembers(html, parent)
        assertEquals(2, members.size)
        assertEquals("S10", members[0].id)
        assertEquals("Anna Andersen", members[0].name)
        assertEquals(DirectoryEntityKind.STUDENT, members[0].kind)
        assertEquals("3x Ma", members[0].subtitle)
    }

    @Test
    fun mergeCatalog_later_wins_on_id_collision() {
        val existing = listOf(
            DirectoryEntity("S1", "Old Name", DirectoryEntityKind.STUDENT, "2x"),
            DirectoryEntity("T1", "Teacher", DirectoryEntityKind.TEACHER),
        )
        val incoming = listOf(
            DirectoryEntity("S1", "New Name", DirectoryEntityKind.STUDENT, "3x"),
            DirectoryEntity("C1", "3x", DirectoryEntityKind.CLASS),
        )
        val merged = DirectoryParser.mergeCatalog(existing, incoming)
        assertEquals(3, merged.size)
        val s1 = merged.first { it.id == "S1" }
        assertEquals("New Name", s1.name)
        assertEquals("3x", s1.subtitle)
        assertTrue(merged.any { it.id == "T1" })
        assertTrue(merged.any { it.id == "C1" })
    }

    @Test
    fun parseFindList_collects_entities_for_kind() {
        val html = """
            <html><body>
            <a data-lectiocontextcard="R1" href="FindSkema.aspx?type=lokale&id=1">201</a>
            <a data-lectiocontextcard="R2" href="FindSkema.aspx?type=lokale&id=2">105</a>
            </body></html>
        """.trimIndent()
        val rooms = DirectoryParser.parseFindList(html, DirectoryEntityKind.ROOM)
        assertEquals(2, rooms.size)
        assertEquals(DirectoryEntityKind.ROOM, rooms[0].kind)
        assertEquals("R1", rooms[0].id)
    }
}
