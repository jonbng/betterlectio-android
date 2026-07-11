package dk.betterlectio.android.ui.screens.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posthog.PostHog
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.betterlectio.android.R
import dk.betterlectio.android.core.i18n.UiText
import dk.betterlectio.android.core.i18n.toUiText
import dk.betterlectio.android.core.result.AppError
import dk.betterlectio.android.core.result.AppResult
import dk.betterlectio.android.feature.messages.ComposeMessageDraft
import dk.betterlectio.android.feature.messages.MessageFolder
import dk.betterlectio.android.feature.messages.MessageRecipient
import dk.betterlectio.android.feature.messages.MessageRepository
import dk.betterlectio.android.feature.messages.MessageThread
import dk.betterlectio.android.feature.messages.MessageThreadDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessagesUiState(
    val loading: Boolean = true,
    val folders: List<MessageFolder> = MessageFolder.defaults,
    val selectedFolder: MessageFolder = MessageFolder.NEWEST,
    val threads: List<MessageThread> = emptyList(),
    val detail: MessageThreadDetail? = null,
    val error: AppError? = null,
    val replyText: String = "",
    val showCompose: Boolean = false,
    val composeSubject: String = "",
    val composeBody: String = "",
    val recipientQuery: String = "",
    val recipientResults: List<MessageRecipient> = emptyList(),
    val selectedRecipients: List<MessageRecipient> = emptyList(),
    val composeMessage: UiText? = null,
)

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val repository: MessageRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MessagesUiState())
    val state: StateFlow<MessagesUiState> = _state.asStateFlow()
    val unreadCount = repository.unreadCount

    init {
        refresh()
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
                    it.copy(loading = false, detail = res.data)
                }
                is AppResult.Failure -> _state.update {
                    it.copy(loading = false, error = res.error)
                }
            }
        }
    }

    fun closeDetail() {
        _state.update { it.copy(detail = null, replyText = "") }
    }

    fun onReplyChange(t: String) {
        _state.update { it.copy(replyText = t) }
    }

    fun sendReply() {
        val detail = _state.value.detail ?: return
        val text = _state.value.replyText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            repository.reply(detail.thread, text)
            PostHog.capture(event = "message_reply_sent")
            _state.update { it.copy(replyText = "") }
            openThread(detail.thread)
        }
    }

    fun openCompose() {
        _state.update {
            it.copy(
                showCompose = true,
                composeSubject = "",
                composeBody = "",
                recipientQuery = "",
                selectedRecipients = emptyList(),
                recipientResults = emptyList(),
                composeMessage = null,
            )
        }
    }

    fun closeCompose() {
        _state.update { it.copy(showCompose = false) }
    }

    fun updateCompose(subject: String? = null, body: String? = null) {
        _state.update {
            it.copy(
                composeSubject = subject ?: it.composeSubject,
                composeBody = body ?: it.composeBody,
            )
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
        viewModelScope.launch {
            val draft = ComposeMessageDraft(
                subject = s.composeSubject,
                body = s.composeBody,
                recipientIds = s.selectedRecipients.map { it.id },
                recipientNames = s.selectedRecipients.map { it.name },
            )
            when (val res = repository.compose(draft)) {
                is AppResult.Success -> {
                    PostHog.capture(
                        event = "message_composed_sent",
                        properties = mapOf("recipient_count" to s.selectedRecipients.size),
                    )
                    _state.update {
                        it.copy(showCompose = false, composeMessage = UiText.Res(R.string.message_sent), recipientQuery = "")
                    }
                }
                is AppResult.Failure -> _state.update {
                    it.copy(composeMessage = res.error.toUiText())
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
}
