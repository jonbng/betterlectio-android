package dk.betterlectio.android.feature.messages

import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePostbackFieldsTest {
    @Test
    fun reply_variants_non_empty_and_distinct_targets() {
        assertTrue(MessagePostbackFields.replyVariants.size >= 2)
        val targets = MessagePostbackFields.replyVariants.map { it.sendTarget }.toSet()
        assertTrue(targets.size >= 2)
    }

    @Test
    fun compose_variants_include_subject_and_body() {
        MessagePostbackFields.composeVariants.forEach { v ->
            assertTrue(v.subjectField.isNotBlank())
            assertTrue(v.bodyField.isNotBlank())
            assertTrue(v.recipientField.isNotBlank())
        }
    }

    @Test
    fun action_target_lists_non_empty() {
        assertTrue(MessagePostbackFields.markReadTargets.isNotEmpty())
        assertTrue(MessagePostbackFields.flagTargets.isNotEmpty())
        assertTrue(MessagePostbackFields.deleteTargets.isNotEmpty())
    }
}
