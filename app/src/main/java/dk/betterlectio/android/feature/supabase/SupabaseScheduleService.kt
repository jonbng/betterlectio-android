package dk.betterlectio.android.feature.supabase

import dk.betterlectio.android.feature.schedule.EventStatus
import dk.betterlectio.android.feature.schedule.LessonDetail
import dk.betterlectio.android.feature.schedule.ScheduleEvent
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedule week sync to Supabase (iOS: `SupabaseScheduleService`).
 * Tables: `lessons`, `student_lessons`, `week_sync`.
 */
@Singleton
class SupabaseScheduleService @Inject constructor(
    private val manager: SupabaseManager,
) {
    suspend fun syncWeek(studentId: String, weekKey: String, events: List<ScheduleEvent>) {
        val client = manager.client ?: run {
            Timber.i("Supabase not configured, skipping schedule sync")
            return
        }
        manager.awaitSessionReady()

        try {
            val now = TIMESTAMP_FORMAT.format(Instant.now())
            val payload = events.map { event ->
                SupabaseLessonRecord(
                    lessonKey = ScheduleIdentity.lessonKey(event, studentId),
                    weekKey = weekKey,
                    lessonDate = event.date.format(DAY_FORMAT),
                    startTime = event.start?.let { "%02d:%02d".format(it.hour, it.minute) }.orEmpty(),
                    endTime = event.end?.let { "%02d:%02d".format(it.hour, it.minute) }.orEmpty(),
                    title = event.title,
                    teacher = event.teacher,
                    room = event.room,
                    status = event.status.name.lowercase(),
                    notes = event.notes,
                    homework = event.homework,
                    sourceUpdatedAt = now,
                    updatedAt = now,
                )
            }

            if (payload.isNotEmpty()) {
                client.from("lessons").upsert(payload) {
                    onConflict = "lesson_key"
                }
            }

            linkStudentToLessons(studentId, payload.map { it.lessonKey })
            markMissingLessonsAsCancelled(studentId, weekKey, payload.map { it.lessonKey }.toSet())
            upsertWeekSync(studentId, weekKey)

            Timber.i("Synced week %s to Supabase (%d events)", weekKey, events.size)
        } catch (e: Exception) {
            Timber.w(e, "Supabase schedule sync failed for week %s", weekKey)
        }
    }

    private suspend fun linkStudentToLessons(studentId: String, lessonKeys: List<String>) {
        val client = manager.client ?: return
        if (lessonKeys.isEmpty()) return

        val fetched = client.from("lessons")
            .select(Columns.list("id", "lesson_key")) {
                filter {
                    isIn("lesson_key", lessonKeys)
                }
            }
            .decodeList<SupabaseLessonIdRow>()

        if (fetched.isEmpty()) return

        val junction = fetched.map {
            StudentLessonRecord(studentId = studentId, lessonId = it.id)
        }
        client.from("student_lessons").upsert(junction)
    }

    private suspend fun markMissingLessonsAsCancelled(
        studentId: String,
        weekKey: String,
        fetchedLessonKeys: Set<String>,
    ) {
        val client = manager.client ?: return
        val remoteKeys = fetchRemoteLessonKeys(studentId, weekKey).toSet()
        val missing = remoteKeys - fetchedLessonKeys
        if (missing.isEmpty()) return

        val now = TIMESTAMP_FORMAT.format(Instant.now())
        for (key in missing) {
            client.from("lessons").update(
                {
                    set("status", EventStatus.CANCELLED.name.lowercase())
                    set("updated_at", now)
                },
            ) {
                filter {
                    eq("lesson_key", key)
                    // week_key filter when column exists on lessons
                    eq("week_key", weekKey)
                }
            }
        }
    }

    private suspend fun fetchRemoteLessonKeys(studentId: String, weekKey: String): List<String> {
        val client = manager.client ?: return emptyList()
        return try {
            val nested = client.from("student_lessons")
                .select(Columns.raw("lessons(lesson_key)")) {
                    filter {
                        eq("student_id", studentId)
                        eq("lessons.week_key", weekKey)
                    }
                }
                .decodeList<SupabaseNestedLessonKeyRow>()
            nested.mapNotNull { it.lessons?.lessonKey }
        } catch (e: Exception) {
            Timber.w(e, "fetchRemoteLessonKeys failed")
            emptyList()
        }
    }

    private suspend fun upsertWeekSync(studentId: String, weekKey: String) {
        val client = manager.client ?: return
        val record = SupabaseWeekSyncRecord(
            studentId = studentId,
            weekKey = weekKey,
            lastSyncedAt = TIMESTAMP_FORMAT.format(Instant.now()),
        )
        client.from("week_sync").upsert(record) {
            onConflict = "student_id,week_key"
        }
    }

    /**
     * Syncs lesson content (homework / notes / blocks) to remote `lessons.content`
     * (iOS: `syncLessonContent`).
     */
    suspend fun syncLessonContent(studentId: String, lessonKey: String, detail: LessonDetail) {
        val client = manager.client ?: return
        manager.awaitSessionReady()
        try {
            val content = LessonContentPayload.fromDetail(detail)
            val payload = LessonContentPatch(
                content = content,
                updatedAt = TIMESTAMP_FORMAT.format(Instant.now()),
            )
            client.from("lessons").update(payload) {
                filter {
                    eq("lesson_key", lessonKey)
                }
            }
            Timber.d("Synced lesson content for key=%s student=%s", lessonKey, studentId)
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync lesson content for %s", lessonKey)
        }
    }

    @Serializable
    private data class LessonContentPatch(
        val content: LessonContentPayload,
        @SerialName("updated_at") val updatedAt: String,
    )

    /**
     * Shape compatible with iOS `LessonContent` JSON stored on `lessons.content`.
     */
    @Serializable
    data class LessonContentPayload(
        val teacherNote: String? = null,
        val items: List<LessonContentItemPayload> = emptyList(),
    ) {
        companion object {
            fun fromDetail(detail: LessonDetail): LessonContentPayload {
                val note = detail.note?.takeIf { it.isNotBlank() }
                val homeworkText = detail.homework?.takeIf { it.isNotBlank() }
                val items = buildList {
                    if (!homeworkText.isNullOrBlank()) {
                        add(
                            LessonContentItemPayload(
                                id = "${detail.eventId}-hw",
                                title = null,
                                note = null,
                                blocks = listOf(
                                    ContentBlockPayload(
                                        type = "paragraph",
                                        inlines = listOf(InlinePayload(type = "text", text = homeworkText)),
                                    ),
                                ),
                                links = detail.resources.map {
                                    LessonLinkPayload(
                                        title = it.title,
                                        url = it.url,
                                        type = if (it.isFile) "file" else "external",
                                    )
                                },
                                isHomework = true,
                            ),
                        )
                    }
                    detail.contentBlocks.forEachIndexed { index, block ->
                        if (block.text.isBlank()) return@forEachIndexed
                        add(
                            LessonContentItemPayload(
                                id = "${detail.eventId}-b$index",
                                title = if (block.kind == "heading") block.text else null,
                                note = if (block.kind == "note") block.text else null,
                                blocks = listOf(
                                    ContentBlockPayload(
                                        type = block.kind.ifBlank { "paragraph" },
                                        inlines = listOf(InlinePayload(type = "text", text = block.text)),
                                    ),
                                ),
                                links = emptyList(),
                                isHomework = false,
                            ),
                        )
                    }
                }
                return LessonContentPayload(teacherNote = note, items = items)
            }
        }
    }

    @Serializable
    data class LessonContentItemPayload(
        val id: String,
        val title: String? = null,
        val note: String? = null,
        val blocks: List<ContentBlockPayload> = emptyList(),
        val links: List<LessonLinkPayload> = emptyList(),
        val isHomework: Boolean = true,
    )

    @Serializable
    data class ContentBlockPayload(
        val type: String,
        val inlines: List<InlinePayload> = emptyList(),
    )

    @Serializable
    data class InlinePayload(
        val type: String,
        val text: String? = null,
        val url: String? = null,
    )

    @Serializable
    data class LessonLinkPayload(
        val title: String,
        val url: String,
        val type: String,
    )

    @Serializable
    private data class SupabaseLessonRecord(
        @SerialName("lesson_key") val lessonKey: String,
        @SerialName("week_key") val weekKey: String,
        @SerialName("lesson_date") val lessonDate: String,
        @SerialName("start_time") val startTime: String,
        @SerialName("end_time") val endTime: String,
        val title: String,
        val teacher: String? = null,
        val room: String? = null,
        val status: String,
        val notes: String? = null,
        val homework: String? = null,
        @SerialName("source_updated_at") val sourceUpdatedAt: String,
        @SerialName("updated_at") val updatedAt: String,
    )

    @Serializable
    private data class StudentLessonRecord(
        @SerialName("student_id") val studentId: String,
        @SerialName("lesson_id") val lessonId: String,
    )

    @Serializable
    private data class SupabaseLessonIdRow(
        val id: String,
        @SerialName("lesson_key") val lessonKey: String,
    )

    @Serializable
    private data class SupabaseLessonKeyRow(
        @SerialName("lesson_key") val lessonKey: String,
    )

    @Serializable
    private data class SupabaseNestedLessonKeyRow(
        val lessons: SupabaseLessonKeyRow? = null,
    )

    @Serializable
    private data class SupabaseWeekSyncRecord(
        @SerialName("student_id") val studentId: String,
        @SerialName("week_key") val weekKey: String,
        @SerialName("last_synced_at") val lastSyncedAt: String,
    )

    companion object {
        private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private val TIMESTAMP_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    }
}
