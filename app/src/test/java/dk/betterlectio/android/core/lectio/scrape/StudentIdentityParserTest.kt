package dk.betterlectio.android.core.lectio.scrape

import org.junit.Assert.assertEquals
import org.junit.Test

class StudentIdentityParserTest {

    @Test
    fun parses_student_header_fixture() {
        val html = javaClass.classLoader!!
            .getResourceAsStream("lectio/fixtures/student_header.html")!!
            .bufferedReader()
            .readText()

        val identity = StudentIdentityParser.parse(html)
        assertEquals("72721770937", identity.studentId)
        assertEquals("Elliott Friedrich", identity.name)
        assertEquals("74096219865", identity.pictureId)
    }
}
