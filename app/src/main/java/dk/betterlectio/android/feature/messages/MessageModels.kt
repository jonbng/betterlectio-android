package dk.betterlectio.android.feature.messages

import java.time.LocalDateTime

data class MessageFolder(
    val id: String,
    val displayName: String,
) {
    companion object {
        val NEWEST = MessageFolder("-70", "Nyeste")
        val UNREAD = MessageFolder("-40", "Ulæst")
        val INBOX = MessageFolder("-10", "Egne beskeder")
        val SENT = MessageFolder("-80", "Sendte beskeder")
        val DELETED = MessageFolder("-60", "Alle slettede")
        val defaults = listOf(NEWEST, UNREAD, INBOX, SENT, DELETED)
    }
}

data class MessageThread(
    val id: String,
    val topic: String,
    val sender: String,
    val dateChanged: LocalDateTime?,
    val folderId: String,
    val normalizedId: String = id,
    val unread: Boolean = false,
    val flagged: Boolean = false,
)

data class MessageAttachment(
    val name: String,
    val url: String,
)

data class ThreadEntry(
    val id: String,
    val topic: String?,
    val contentHtml: String?,
    val senderName: String?,
    val sentAt: LocalDateTime?,
    val attachments: List<MessageAttachment> = emptyList(),
) {
    val attachmentNames: List<String> get() = attachments.map { it.name }
}

data class MessageThreadDetail(
    val thread: MessageThread,
    val entries: List<ThreadEntry>,
    val receivers: List<String> = emptyList(),
)

data class ComposeMessageDraft(
    val subject: String,
    val body: String,
    val recipientIds: List<String>,
    val recipientNames: List<String>,
)

data class MessageRecipient(
    val id: String,
    val name: String,
    val kind: String = "person",
)
