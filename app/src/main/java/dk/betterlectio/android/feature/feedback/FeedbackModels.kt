package dk.betterlectio.android.feature.feedback

import android.graphics.Bitmap
import androidx.annotation.StringRes
import dk.betterlectio.android.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class FeedbackCategory(
    val analyticsKey: String,
    @param:StringRes val labelRes: Int,
    @param:StringRes val hintRes: Int,
) {
    BUG(
        analyticsKey = "bug",
        labelRes = R.string.feedback_category_bug,
        hintRes = R.string.feedback_hint_bug,
    ),
    IDEA(
        analyticsKey = "idea",
        labelRes = R.string.feedback_category_idea,
        hintRes = R.string.feedback_hint_idea,
    ),
    OTHER(
        analyticsKey = "other",
        labelRes = R.string.feedback_category_other,
        hintRes = R.string.feedback_hint_other,
    ),
}

/**
 * Snapshot of the screen + diagnostics captured at shake time (before the sheet covers UI).
 */
data class FeedbackCapture(
    val screenshot: Bitmap?,
    val logs: String,
    val capturedAtMs: Long = System.currentTimeMillis(),
)

data class FeedbackSubmission(
    val category: FeedbackCategory,
    val message: String,
    val includeScreenshot: Boolean,
    val includeLogs: Boolean,
    val capture: FeedbackCapture,
)

sealed class FeedbackSubmitResult {
    data object Success : FeedbackSubmitResult()
    data class Failure(val throwable: Throwable? = null) : FeedbackSubmitResult()
}

@Serializable
data class FeedbackInboxItem(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("last_public_activity_at") val lastPublicActivityAt: String,
    val category: String,
    val status: String,
    @SerialName("conversation_state") val conversationState: String,
    val title: String? = null,
    val message: String,
    val platform: String,
    @SerialName("is_unread") val isUnread: Boolean = false,
    @SerialName("last_reply") val lastReply: String? = null,
) {
    val displayTitle: String
        get() = title?.trim()?.takeIf(String::isNotEmpty)
            ?: message.lineSequence().firstOrNull()?.take(72).orEmpty().ifEmpty { "Feedback" }
}

@Serializable
data class FeedbackThreadComment(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("author_kind") val authorKind: String,
    val body: String,
)

@Serializable
data class FeedbackThread(
    val item: FeedbackInboxItem,
    val comments: List<FeedbackThreadComment> = emptyList(),
    @SerialName("status_events") val statusEvents: List<FeedbackStatusEvent> = emptyList(),
)

@Serializable
data class FeedbackStatusEvent(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("from_status") val fromStatus: String? = null,
    @SerialName("to_status") val toStatus: String,
    val note: String? = null,
)
