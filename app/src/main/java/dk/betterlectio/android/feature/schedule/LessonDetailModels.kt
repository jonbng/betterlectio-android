package dk.betterlectio.android.feature.schedule

data class LessonParticipant(
    val id: String,
    val name: String,
    val role: String? = null,
)

data class LessonResource(
    val title: String,
    val url: String,
    val isFile: Boolean = false,
)

data class LessonContentBlock(
    val kind: String, // heading | paragraph | note
    val text: String,
)

data class LessonDetail(
    val eventId: String,
    val title: String,
    val note: String? = null,
    val homework: String? = null,
    val contentBlocks: List<LessonContentBlock> = emptyList(),
    val participants: List<LessonParticipant> = emptyList(),
    val resources: List<LessonResource> = emptyList(),
)

data class PrivateEventDraft(
    val title: String,
    val startDate: String, // dd/MM-yyyy
    val startTime: String, // HH:mm
    val endDate: String,
    val endTime: String,
    val note: String = "",
    /** When set, repository performs update instead of create. */
    val eventId: String? = null,
)
