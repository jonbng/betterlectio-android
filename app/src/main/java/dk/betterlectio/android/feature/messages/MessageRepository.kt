package dk.betterlectio.android.feature.messages

import dk.betterlectio.android.core.cache.SimpleCache
import dk.betterlectio.android.core.lectio.LectioClient
import dk.betterlectio.android.core.lectio.model.FetchPriority
import dk.betterlectio.android.core.lectio.scrape.SmartPostback
import dk.betterlectio.android.core.lectio.session.SessionController
import dk.betterlectio.android.core.result.AppError
import dk.betterlectio.android.core.result.AppResult
import dk.betterlectio.android.feature.demo.DemoData
import dk.betterlectio.android.feature.directory.DirectoryEntityKind
import dk.betterlectio.android.feature.directory.DirectoryRepository
import dk.betterlectio.android.feature.offline.OfflineMessageStore
import dk.betterlectio.android.feature.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val client: LectioClient,
    private val cache: SimpleCache,
    private val session: SessionController,
    private val directoryRepository: DirectoryRepository,
    private val settings: SettingsStore,
    private val offlineMessages: OfflineMessageStore,
) {
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    /** Demo-mutable state; same instance production demo mode uses. Exposed for tests via companion. */
    internal val demoState = DemoMessageState()

    suspend fun loadFolder(
        folder: MessageFolder = MessageFolder.NEWEST,
        forceRefresh: Boolean = false,
    ): AppResult<List<MessageThread>> {
        val student = session.currentStudent
            ?: return AppResult.Failure(AppError.Unauthorized)
        if (student.isDemo) {
            val list = demoState.listForFolder(folder)
            _unreadCount.value = demoState.unreadCount()
            return AppResult.Success(list)
        }

        val key = "messages_${student.studentId}_${folder.id}"
        if (!forceRefresh) {
            cache.get(key)?.let {
                val parsed = MessageParser.parseThreadList(it, folder.id)
                if (folder.id == MessageFolder.UNREAD.id) _unreadCount.value = parsed.size
                return AppResult.Success(parsed)
            }
            val roomCached = offlineMessages.loadFolder(student.studentId, folder.id)
            if (roomCached.isNotEmpty()) {
                if (folder.id == MessageFolder.UNREAD.id) _unreadCount.value = roomCached.count { it.unread }
                return AppResult.Success(roomCached)
            }
        }

        val path = "beskeder2.aspx?type=liste&mappeid=${folder.id}"
        return when (val res = client.get(path, FetchPriority.Important)) {
            is AppResult.Failure -> {
                cache.get(key)?.let {
                    return AppResult.Success(MessageParser.parseThreadList(it, folder.id))
                }
                val roomCached = offlineMessages.loadFolder(student.studentId, folder.id)
                if (roomCached.isNotEmpty()) return AppResult.Success(roomCached)
                res
            }
            is AppResult.Success -> {
                cache.put(key, res.data.body)
                val parsed = MessageParser.parseThreadList(res.data.body, folder.id)
                offlineMessages.saveFolder(student.studentId, folder.id, parsed)
                if (folder.id == MessageFolder.UNREAD.id) _unreadCount.value = parsed.size
                AppResult.Success(parsed)
            }
        }
    }

    suspend fun refreshUnreadBadge() {
        loadFolder(MessageFolder.UNREAD, forceRefresh = true)
    }

    suspend fun loadThread(thread: MessageThread): AppResult<MessageThreadDetail> {
        val student = session.currentStudent
            ?: return AppResult.Failure(AppError.Unauthorized)
        if (student.isDemo) {
            // Merge live demo list (flag/unread) into detail so reopen keeps mutations
            return AppResult.Success(demoState.loadDetail(thread.id))
        }

        val cacheKey = "message_thread_${student.studentId}_${thread.normalizedId}"
        cache.get(cacheKey)?.let {
            return AppResult.Success(MessageParser.parseThreadDetail(it, thread))
        }

        val path = "beskeder2.aspx?type=showthread&mappeid=${thread.folderId}&id=${thread.normalizedId}"
        return when (val res = client.get(path, FetchPriority.Important)) {
            is AppResult.Failure -> res
            is AppResult.Success -> {
                cache.put(cacheKey, res.data.body)
                AppResult.Success(MessageParser.parseThreadDetail(res.data.body, thread))
            }
        }
    }

    suspend fun reply(thread: MessageThread, body: String): AppResult<Unit> {
        if (session.currentStudent?.isDemo == true) {
            settings.appendNotificationHistory("Svar sendt (demo): ${thread.topic}")
            return AppResult.Success(Unit)
        }
        val path =
            "beskeder2.aspx?type=showthread&mappeid=${thread.folderId}&id=${thread.normalizedId}"
        return when (val page = client.get(path, FetchPriority.Important)) {
            is AppResult.Failure -> page
            is AppResult.Success -> {
                val html = page.data.body
                val contentField = SmartPostback.findFieldName(
                    html,
                    listOf("WriteContent", "CreateNewAnswer", "MessageBody", "txtMessage"),
                ) ?: MessagePostbackFields.replyVariants.first().contentField
                val targets = MessagePostbackFields.replyVariants.map { it.sendTarget }
                val resolved = SmartPostback.resolve(
                    html = html,
                    preferredTargets = targets,
                    extra = mapOf(contentField to body),
                    nameContainsAny = listOf("SendAnswer", "Send", "Svar", "CreateNewAnswer"),
                )
                when (val post = client.postForm("beskeder2.aspx", resolved.fields)) {
                    is AppResult.Success -> {
                        settings.appendNotificationHistory("Svar sendt: ${thread.topic}")
                        AppResult.Success(Unit)
                    }
                    is AppResult.Failure -> post
                }
            }
        }
    }

    suspend fun searchRecipients(query: String): AppResult<List<MessageRecipient>> {
        if (session.currentStudent?.isDemo == true) {
            val q = query.trim().lowercase()
            return AppResult.Success(
                DemoData.directory
                    .filter { it.kind == DirectoryEntityKind.STUDENT || it.kind == DirectoryEntityKind.TEACHER }
                    .filter { q.isEmpty() || it.name.lowercase().contains(q) }
                    .map { MessageRecipient(it.id, it.name, it.kind.name) },
            )
        }
        return when (val res = directoryRepository.search(query, DirectoryEntityKind.STUDENT)) {
            is AppResult.Failure -> res
            is AppResult.Success -> AppResult.Success(
                res.data.map { MessageRecipient(it.id, it.name, it.kind.name) },
            )
        }
    }

    suspend fun markRead(thread: MessageThread): AppResult<Unit> {
        if (session.currentStudent?.isDemo == true) {
            demoState.markRead(thread.id)
            _unreadCount.value = demoState.unreadCount()
            return AppResult.Success(Unit)
        }
        // iOS/Flutter: __Page + READMESSAGE_<normalizedId> + folders
        postListPageEvent(
            folderId = thread.folderId,
            eventArgument = MessagePostbackFields.readMessageArg(thread.normalizedId),
            fallbackTargets = MessagePostbackFields.markReadTargets,
            threadId = thread.normalizedId,
        )
        return AppResult.Success(Unit)
    }

    suspend fun deleteThread(thread: MessageThread): AppResult<Unit> {
        if (session.currentStudent?.isDemo == true) {
            demoState.delete(thread.id)
            _unreadCount.value = demoState.unreadCount()
            return AppResult.Success(Unit)
        }
        // Flutter: HIDEMESSAGE_<normalizedId>
        postListPageEvent(
            folderId = thread.folderId,
            eventArgument = MessagePostbackFields.hideMessageArg(thread.normalizedId),
            fallbackTargets = MessagePostbackFields.deleteTargets,
            threadId = thread.normalizedId,
        )
        return AppResult.Success(Unit)
    }

    /** Toggle flag (demo mutates list; live best-effort postback). */
    suspend fun toggleFlag(thread: MessageThread): AppResult<MessageThread> {
        if (session.currentStudent?.isDemo == true) {
            val updated = demoState.toggleFlag(thread.id)
                ?: return AppResult.Success(thread.copy(flagged = !thread.flagged))
            return AppResult.Success(updated)
        }
        val next = !thread.flagged
        // iOS: FLAGMESSAGE_<id>
        postListPageEvent(
            folderId = thread.folderId,
            eventArgument = MessagePostbackFields.flagMessageArg(thread.normalizedId),
            fallbackTargets = MessagePostbackFields.flagTargets,
            threadId = thread.normalizedId,
            extra = mapOf("flag" to if (next) "1" else "0"),
        )
        return AppResult.Success(thread.copy(flagged = next))
    }

    /**
     * GET folder page → merge VIEWSTATE → POST with iOS/Flutter event args.
     */
    private suspend fun postListPageEvent(
        folderId: String,
        eventArgument: String,
        fallbackTargets: List<String>,
        threadId: String,
        extra: Map<String, String> = emptyMap(),
    ) {
        val path = "beskeder2.aspx"
        val page = client.get("$path?type=liste&mappeid=$folderId")
        val html = (page as? AppResult.Success)?.data?.body
        if (html != null) {
            val resolved = dk.betterlectio.android.core.lectio.scrape.SmartPostback.resolve(
                html = html,
                preferredTargets = listOf(MessagePostbackFields.PAGE_EVENT_TARGET),
                extra = mapOf(
                    "__EVENTARGUMENT" to eventArgument,
                    MessagePostbackFields.FOLDERS_FIELD to folderId,
                ) + extra,
                nameContainsAny = emptyList(),
            )
            // Force __Page target + event argument
            val fields = resolved.fields.toMutableMap()
            fields["__EVENTTARGET"] = MessagePostbackFields.PAGE_EVENT_TARGET
            fields["__EVENTARGUMENT"] = eventArgument
            fields[MessagePostbackFields.FOLDERS_FIELD] = folderId
            if (client.postForm(path, fields) is AppResult.Success) return
        }
        // Fallback: legacy button names
        val fallbackExtra = mapOf("threadid" to threadId) + extra + mapOf(
            MessagePostbackFields.FOLDERS_FIELD to folderId,
        )
        for (target in fallbackTargets) {
            if (client.postback(path, target, fallbackExtra) is AppResult.Success) break
        }
    }

    suspend fun compose(draft: ComposeMessageDraft): AppResult<Unit> {
        if (draft.subject.isBlank() || draft.recipientIds.isEmpty()) {
            return AppResult.Failure(AppError.Unknown("Emne og modtager er påkrævet"))
        }
        if (session.currentStudent?.isDemo == true) {
            settings.appendNotificationHistory(
                "Ny besked (demo) til ${draft.recipientNames.joinToString()}: ${draft.subject}",
            )
            return AppResult.Success(Unit)
        }
        return when (val page = client.get("beskeder2.aspx?type=nybesked", FetchPriority.Important)) {
            is AppResult.Failure -> {
                // Fallback: try compose variants via classic postback
                var lastError: AppError = page.error
                for (variant in MessagePostbackFields.composeVariants) {
                    val extra = mapOf(
                        variant.recipientField to draft.recipientIds.first(),
                        variant.subjectField to draft.subject,
                        variant.bodyField to draft.body,
                    )
                    when (val post = client.postback("beskeder2.aspx", variant.sendTarget, extra)) {
                        is AppResult.Success -> {
                            settings.appendNotificationHistory("Ny besked: ${draft.subject}")
                            return AppResult.Success(Unit)
                        }
                        is AppResult.Failure -> lastError = post.error
                    }
                }
                AppResult.Failure(lastError)
            }
            is AppResult.Success -> {
                val html = page.data.body
                val subjectField = SmartPostback.findFieldName(
                    html,
                    listOf("Subject", "MessagesSubject", "emne"),
                ) ?: MessagePostbackFields.composeVariants.first().subjectField
                val bodyField = SmartPostback.findFieldName(
                    html,
                    listOf("WriteContent", "CreateNewMessage", "body"),
                ) ?: MessagePostbackFields.composeVariants.first().bodyField
                val recipientField = SmartPostback.findFieldName(
                    html,
                    listOf("Recipient", "addRecipient", "modtager"),
                ) ?: MessagePostbackFields.composeVariants.first().recipientField
                val resolved = SmartPostback.resolve(
                    html = html,
                    preferredTargets = MessagePostbackFields.composeVariants.map { it.sendTarget },
                    extra = mapOf(
                        recipientField to draft.recipientIds.first(),
                        subjectField to draft.subject,
                        bodyField to draft.body,
                    ),
                    nameContainsAny = listOf("CreateMessage", "Send", "Opret"),
                )
                when (val post = client.postForm("beskeder2.aspx", resolved.fields)) {
                    is AppResult.Success -> {
                        settings.appendNotificationHistory("Ny besked: ${draft.subject}")
                        AppResult.Success(Unit)
                    }
                    is AppResult.Failure -> post
                }
            }
        }
    }
}
