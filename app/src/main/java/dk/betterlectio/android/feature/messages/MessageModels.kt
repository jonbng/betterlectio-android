package dk.betterlectio.android.feature.messages

import android.net.Uri
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
    /** Latest sender display name (title attribute when present). */
    val sender: String,
    val dateChanged: LocalDateTime?,
    val folderId: String,
    val normalizedId: String = id,
    val unread: Boolean = false,
    val flagged: Boolean = false,
    /** Lectio context-card id when present on the list row (`S…` / `T…`). */
    val senderEntityId: String? = null,
    /** `STUDENT` / `TEACHER` when inferred from list row fonticon classes. */
    val senderKind: String? = null,
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
    val senderEntityId: String? = null,
    val senderKind: String? = null,
) {
    val attachmentNames: List<String> get() = attachments.map { it.name }
}

data class MessageThreadDetail(
    val thread: MessageThread,
    val entries: List<ThreadEntry>,
    val receivers: List<String> = emptyList(),
    /** Lectio context-card ids for thread recipients (`S…` / `T…`) — signature skip. */
    val receiverEntityIds: List<String> = emptyList(),
)

/**
 * Local attachment selected in compose/reply UI (uploaded at send time).
 */
data class ComposeAttachment(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long? = null,
)

data class ComposeMessageDraft(
    val subject: String,
    /** BBCode body (Lectio `EditModeContentBBTB`). */
    val body: String,
    val recipientIds: List<String>,
    val recipientNames: List<String>,
    /** When true, inject Lectio `RepliesNotAllowedChkBox=on` on compose postbacks. */
    val repliesNotAllowed: Boolean = false,
    val attachments: List<ComposeAttachment> = emptyList(),
)

data class MessageRecipient(
    val id: String,
    val name: String,
    val kind: String = "person",
)

/**
 * Resolved attachment field names from a compose/reply form HTML page.
 */
data class MessageAttachTargets(
    val docIdFieldName: String,
    val postbackTarget: String,
)
