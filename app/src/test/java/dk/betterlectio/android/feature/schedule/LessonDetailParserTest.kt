package dk.betterlectio.android.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonDetailParserTest {
    @Test
    fun parses_lesson_detail_fixture() {
        val html = javaClass.classLoader!!
            .getResourceAsStream("lectio/fixtures/lesson_detail.html")!!
            .bufferedReader().readText()
        val detail = LessonDetailParser.parse(html, "111", "Fallback")
        assertTrue(detail.note?.contains("regnemaskine") == true || detail.contentBlocks.any { it.kind == "note" })
        assertTrue(detail.contentBlocks.isNotEmpty())
        assertTrue(detail.resources.any { it.title.contains("Opgavesæt") || it.isFile })
        assertTrue(detail.participants.any { it.name.contains("Jens") })
        assertTrue(detail.title.isNotBlank())
        val images = detail.contentBlocks.filter { it.kind == "image" }
        assertTrue("expected image content block from fixture", images.isNotEmpty())
        assertTrue(images.any { it.url?.contains("documentid=55") == true })
    }

    @Test
    fun private_event_draft_shape_is_usable() {
        val draft = PrivateEventDraft(
            title = "Læge",
            startDate = "10/07-2026",
            startTime = "10:00",
            endDate = "10/07-2026",
            endTime = "11:00",
            note = "Check-up",
        )
        assertEquals("Læge", draft.title)
        assertTrue(draft.startDate.contains("-2026"))
    }
}
