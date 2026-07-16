package dk.betterlectio.android.ui.screens.messages

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posthog.PostHog
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.betterlectio.android.R
import dk.betterlectio.android.core.i18n.UiText
import dk.betterlectio.android.core.i18n.toUiText
import dk.betterlectio.android.core.result.AppError
import dk.betterlectio.android.core.result.AppResult
import dk.betterlectio.android.feature.messages.ComposeAttachment
import dk.betterlectio.android.feature.messages.ComposeMessageDraft
import dk.betterlectio.android.feature.messages.MessageFolder
import dk.betterlectio.android.feature.messages.MessageRecipient
import dk.betterlectio.android.feature.messages.MessageRepository
import dk.betterlectio.android.feature.messages.MessageThread
import dk.betterlectio.android.feature.messages.MessageThreadDetail
import dk.betterlectio.android.feature.messages.PendingComposeRecipient
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class MessagesUiState(
    val loading: Boolean = true,
    val folders: List<MessageFolder> = MessageFolder.defaults,
    val selectedFolder: MessageFolder = MessageFolder.NEWEST,
    val threads: List<MessageThread> = emptyList(),
    val detail: MessageThreadDetail? = null,
    val error: AppError? = null,
    val replyText: String = "",
    val replyAttachments: List<ComposeAttachment> = emptyList(),
    val replyError: UiText? = null,
    val showCompose: Boolean = false,
    val composeSubject: String = "",
    val composeBody: String = "",
    val recipientQuery: String = "",
    val recipientResults: List<MessageRecipient> = emptyList(),
    val selectedRecipients: List<MessageRecipient> = emptyList(),
    val repliesNotAllowed: Boolean = false,
    val composeAttachments: List<ComposeAttachment> = emptyList(),
    val isSending: Boolean = false,
    val composeMessage: UiText? = null,
    /** Bumped when compose should be shown (e.g. directory preselect handoff). */
    val composeNavToken: Int = 0,
)

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val repository: MessageRepository,
    private val pendingCompose: PendingComposeRecipient,
) : ViewModel() {
    private val _state = MutableStateFlow(MessagesUiState())
    val state: StateFlow<MessagesUiState> = _state.asStateFlow()
    val unreadCount = repository.unreadCount

    init {
        refresh()
        viewModelScope.launch {
            pendingCompose.pending.collect { offered ->
                if (offered == null) return@collect
                val recipient = pendingCompose.consume() ?: return@collect
                openCompose(preselected = listOf(recipient))
            }
        }
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val res = repository.loadFolder(_state.value.selectedFolder, force)) {
                is AppResult.Success -> _state.update {
                    it.copy(loading = false, threads = res.data)
                }
                is AppResult.Failure -> _state.update {
                    it.copy(loading = false, error = res.error)
                }
            }
            repository.refreshUnreadBadge()
        }
    }

    fun selectFolder(folder: MessageFolder) {
        _state.update { it.copy(selectedFolder = folder) }
        refresh(true)
    }

    fun openThread(thread: MessageThread) {
        PostHog.capture(
            event = "message_thread_opened",
            properties = mapOf("is_unread" to thread.unread),
        )
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val res = repository.loadThread(thread)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        detail = res.data,
                        replyText = "",
                        replyAttachments = emptyList(),
                        replyError = null,
                    )
                }
                is AppResult.Failure -> _state.update {
                    it.copy(loading = false, error = res.error)
                }
            }
        }
    }

    fun closeDetail() {
        _state.update {
            it.copy(
                detail = null,
                replyText = "",
                replyAttachments = emptyList(),
                replyError = null,
            )
        }
    }

    fun onReplyChange(t: String) {
        _state.update { it.copy(replyText = t, replyError = null) }
    }

    fun addReplyAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { s ->
            val existing = s.replyAttachments.toMutableList()
            for (uri in uris) {
                if (existing.size >= MAX_ATTACHMENTS) break
                if (existing.any { it.uri == uri }) continue
                existing += repository.resolveAttachmentMeta(uri)
            }
            s.copy(replyAttachments = existing)
        }
    }

    fun removeReplyAttachment(uri: Uri) {
        _state.update { s ->
            s.copy(replyAttachments = s.replyAttachments.filterNot { it.uri == uri })
        }
    }

    fun sendReply() {
        val detail = _state.value.detail ?: return
        val text = _state.value.replyText.trim()
        if (text.isEmpty() || _state.value.isSending) return
        val attachments = _state.value.replyAttachments
        val sigIds = buildList {
            detail.thread.senderEntityId?.let { add(it) }
            detail.receiverEntityIds.forEach { add(it) }
            detail.entries.mapNotNullTo(this) { it.senderEntityId }
        }.distinct()
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, replyError = null) }
            val res = try {
                withTimeout(SEND_TIMEOUT_MS) {
                    repository.reply(
                        thread = detail.thread,
                        body = text,
                        attachments = attachments,
                        recipientIdsForSignature = sigIds,
                    )
                }
            } catch (_: TimeoutCancellationException) {
                AppResult.Failure(
                    AppError.Unknown("Afsendelse tog for lang tid – tjek netværket og prøv igen"),
                )
            }
            when (res) {
                is AppResult.Success -> {
                    PostHog.capture(
                        event = "message_reply_sent",
                        properties = mapOf(
                            "attachment_count" to attachments.size,
                            "has_formatting" to text.contains('['),
                        ),
                    )
                    _state.update {
                        it.copy(
                            replyText = "",
                            replyAttachments = emptyList(),
                            isSending = false,
                            replyError = null,
                        )
                    }
                    openThread(detail.thread)
                }
                is AppResult.Failure -> {
                    _state.update {
                        it.copy(
                            isSending = false,
                            replyError = res.error.toUiText(),
                        )
                    }
                }
            }
        }
    }

    fun openCompose(preselected: List<MessageRecipient> = emptyList()) {
        _state.update {
            it.copy(
                showCompose = true,
                composeSubject = "",
                composeBody = "",
                recipientQuery = "",
                selectedRecipients = preselected,
                recipientResults = emptyList(),
                repliesNotAllowed = false,
                composeAttachments = emptyList(),
                isSending = false,
                composeMessage = null,
                composeNavToken = it.composeNavToken + 1,
            )
        }
    }

    fun closeCompose() {
        if (_state.value.isSending) return
        _state.update {
            it.copy(
                showCompose = false,
                composeAttachments = emptyList(),
                repliesNotAllowed = false,
            )
        }
    }

    fun updateCompose(subject: String? = null, body: String? = null) {
        _state.update {
            it.copy(
                composeSubject = subject ?: it.composeSubject,
                composeBody = body ?: it.composeBody,
            )
        }
    }

    fun setRepliesNotAllowed(v: Boolean) {
        _state.update { it.copy(repliesNotAllowed = v) }
    }

    fun addComposeAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { s ->
            val existing = s.composeAttachments.toMutableList()
            for (uri in uris) {
                if (existing.size >= MAX_ATTACHMENTS) break
                if (existing.any { it.uri == uri }) continue
                existing += repository.resolveAttachmentMeta(uri)
            }
            s.copy(composeAttachments = existing)
        }
    }

    fun removeComposeAttachment(uri: Uri) {
        _state.update { s ->
            s.copy(composeAttachments = s.composeAttachments.filterNot { it.uri == uri })
        }
    }

    fun onRecipientQuery(q: String) {
        _state.update { it.copy(recipientQuery = q) }
        viewModelScope.launch {
            when (val res = repository.searchRecipients(q)) {
                is AppResult.Success -> _state.update { it.copy(recipientResults = res.data) }
                is AppResult.Failure -> Unit
            }
        }
    }

    fun toggleRecipient(r: MessageRecipient) {
        _state.update { s ->
            val selected = s.selectedRecipients.toMutableList()
            if (selected.any { it.id == r.id }) selected.removeAll { it.id == r.id }
            else selected.add(r)
            s.copy(selectedRecipients = selected)
        }
    }

    fun sendCompose() {
        val s = _state.value
        if (s.isSending) return
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, composeMessage = null) }
            val draft = ComposeMessageDraft(
                subject = s.composeSubject,
                body = s.composeBody,
                recipientIds = s.selectedRecipients.map { it.id },
                recipientNames = s.selectedRecipients.map { it.name },
                repliesNotAllowed = s.repliesNotAllowed,
                attachments = s.composeAttachments,
            )
            val res = try {
                withTimeout(SEND_TIMEOUT_MS) {
                    repository.compose(draft)
                }
            } catch (_: TimeoutCancellationException) {
                AppResult.Failure(
                    AppError.Unknown("Afsendelse tog for lang tid – tjek netværket og prøv igen"),
                )
            }
            when (res) {
                is AppResult.Success -> {
                    PostHog.capture(
                        event = "message_composed_sent",
                        properties = mapOf(
                            "recipient_count" to s.selectedRecipients.size,
                            "attachment_count" to s.composeAttachments.size,
                            "replies_not_allowed" to s.repliesNotAllowed,
                            "has_formatting" to s.composeBody.contains('['),
                        ),
                    )
                    _state.update {
                        it.copy(
                            showCompose = false,
                            isSending = false,
                            composeMessage = UiText.Res(R.string.message_sent),
                            recipientQuery = "",
                            composeAttachments = emptyList(),
                            repliesNotAllowed = false,
                        )
                    }
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isSending = false, composeMessage = res.error.toUiText())
                }
            }
        }
    }

    fun markRead() {
        val detail = _state.value.detail ?: return
        markThreadRead(detail.thread, fromDetail = true)
    }

    fun markThreadRead(thread: MessageThread, fromDetail: Boolean = false) {
        viewModelScope.launch {
            repository.markRead(thread)
            _state.update { s ->
                s.copy(
                    threads = s.threads.map { t ->
                        if (t.id == thread.id) t.copy(unread = false) else t
                    },
                    detail = s.detail?.let { d ->
                        if (d.thread.id == thread.id) d.copy(thread = d.thread.copy(unread = false)) else d
                    },
                )
            }
            repository.refreshUnreadBadge()
            if (fromDetail) {
                openThread(thread.copy(unread = false))
            }
        }
    }

    fun deleteCurrent() {
        val detail = _state.value.detail ?: return
        deleteThread(detail.thread)
    }

    fun deleteThread(thread: MessageThread) {
        viewModelScope.launch {
            repository.deleteThread(thread)
            _state.update { s ->
                s.copy(
                    detail = if (s.detail?.thread?.id == thread.id) null else s.detail,
                    threads = s.threads.filterNot { it.id == thread.id },
                )
            }
            repository.refreshUnreadBadge()
        }
    }

    fun toggleFlag() {
        val detail = _state.value.detail ?: return
        toggleThreadFlag(detail.thread)
    }

    fun toggleThreadFlag(thread: MessageThread) {
        viewModelScope.launch {
            when (val res = repository.toggleFlag(thread)) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            detail = it.detail?.let { d ->
                                if (d.thread.id == res.data.id) d.copy(thread = res.data) else d
                            },
                            threads = it.threads.map { t ->
                                if (t.id == res.data.id) res.data else t
                            },
                        )
                    }
                }
                is AppResult.Failure -> Unit
            }
        }
    }

    companion object {
        const val MAX_ATTACHMENTS = 10

        /** Wall-clock budget for multi-step Lectio compose/reply (includes queue wait). */
        private const val SEND_TIMEOUT_MS = 120_000L
    }
}
