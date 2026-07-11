package dk.betterlectio.android.feature.demo

import dk.betterlectio.android.core.model.School
import dk.betterlectio.android.core.util.LectioDateUtils
import dk.betterlectio.android.feature.absence.AbsenceOverview
import dk.betterlectio.android.feature.absence.AbsenceRegistration
import dk.betterlectio.android.feature.absence.AbsenceTeamRow
import dk.betterlectio.android.feature.assignments.AssignmentItem
import dk.betterlectio.android.feature.directory.DirectoryEntity
import dk.betterlectio.android.feature.directory.DirectoryEntityKind
import dk.betterlectio.android.feature.grades.GradeRow
import dk.betterlectio.android.feature.homework.HomeworkItem
import dk.betterlectio.android.feature.messages.MessageAttachment
import dk.betterlectio.android.feature.messages.MessageFolder
import dk.betterlectio.android.feature.messages.MessageThread
import dk.betterlectio.android.feature.messages.MessageThreadDetail
import dk.betterlectio.android.feature.messages.ThreadEntry
import dk.betterlectio.android.feature.plans.StudyPlan
import dk.betterlectio.android.feature.schedule.EventStatus
import dk.betterlectio.android.feature.schedule.LessonContentBlock
import dk.betterlectio.android.feature.schedule.LessonDetail
import dk.betterlectio.android.feature.schedule.LessonParticipant
import dk.betterlectio.android.feature.schedule.LessonResource
import dk.betterlectio.android.feature.schedule.ScheduleDay
import dk.betterlectio.android.feature.schedule.ScheduleEvent
import dk.betterlectio.android.feature.schedule.ScheduleWeek
import dk.betterlectio.android.feature.teams.ModuleStat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object DemoData {
    val schools = listOf(
        School.Demo,
        School(1, "Demo Gymnasium Nord"),
        School(94, "Sorø Akademis Skole"),
        School(256, "Gammel Hellerup Gymnasium"),
        School(517, "Nørre Gymnasium"),
    )

    fun scheduleWeek(year: Int, week: Int): ScheduleWeek {
        val monday = LectioDateUtils.weekStart(year, week)
        val today = LocalDate.now()
        val days = (0..4).map { offset ->
            val date = monday.plusDays(offset.toLong())
            val events = if (date == today || offset == today.dayOfWeek.value - 1) {
                listOf(
                    ScheduleEvent(
                        id = "demo1-$offset",
                        title = "Matematik A",
                        team = "Ma A",
                        teacher = "Jens Jensen",
                        room = "201",
                        status = EventStatus.NORMAL,
                        start = LocalDateTime.of(date, LocalTime.of(8, 15)),
                        end = LocalDateTime.of(date, LocalTime.of(9, 15)),
                        date = date,
                        homework = "Opg. 12–15",
                    ),
                    ScheduleEvent(
                        id = "demo2-$offset",
                        title = "Dansk A",
                        team = "Da A",
                        teacher = "Anne Andersen",
                        room = "105",
                        status = if (offset == 1) EventStatus.CHANGED else EventStatus.NORMAL,
                        start = LocalDateTime.of(date, LocalTime.of(9, 25)),
                        end = LocalDateTime.of(date, LocalTime.of(10, 25)),
                        date = date,
                    ),
                    ScheduleEvent(
                        id = "demo3-$offset",
                        title = "Fysik B",
                        team = "Fy B",
                        teacher = "Peter Petersen",
                        room = "Lab 1",
                        status = if (offset == 2) EventStatus.CANCELLED else EventStatus.NORMAL,
                        start = LocalDateTime.of(date, LocalTime.of(10, 45)),
                        end = LocalDateTime.of(date, LocalTime.of(11, 45)),
                        date = date,
                    ),
                )
            } else {
                listOf(
                    ScheduleEvent(
                        id = "demo-x-$offset",
                        title = "Historie B",
                        team = "Hi B",
                        teacher = "Mette Madsen",
                        room = "312",
                        start = LocalDateTime.of(date, LocalTime.of(8, 15)),
                        end = LocalDateTime.of(date, LocalTime.of(9, 15)),
                        date = date,
                    ),
                )
            }
            ScheduleDay(date, events)
        }
        return ScheduleWeek(year, week, days)
    }

    val messages = listOf(
        MessageThread(
            id = "m1",
            topic = "Velkommen til BetterLectio demo",
            sender = "System",
            dateChanged = LocalDateTime.now().minusHours(2),
            folderId = MessageFolder.UNREAD.id,
            unread = true,
        ),
        MessageThread(
            id = "m2",
            topic = "Aflevering i matematik udsat",
            sender = "Jens Jensen",
            dateChanged = LocalDateTime.now().minusDays(1),
            folderId = MessageFolder.INBOX.id,
            unread = false,
        ),
    )

    fun messageDetail(id: String) = MessageThreadDetail(
        thread = messages.firstOrNull { it.id == id } ?: messages.first(),
        entries = listOf(
            ThreadEntry(
                id = "e1",
                topic = "Besked",
                contentHtml = "<p>Dette er en <b>demo-besked</b>. Log ind med MitID for rigtige data.</p>" +
                    "<div class=\"message-attachements\"><a href=\"https://www.lectio.dk/demo.pdf\">Opgavesæt.pdf</a></div>",
                senderName = "System",
                sentAt = LocalDateTime.now().minusHours(2),
                attachments = listOf(
                    MessageAttachment("Opgavesæt.pdf", "https://www.lectio.dk/demo.pdf"),
                ),
            ),
        ),
        receivers = listOf("Demo Elev", "Jens Jensen"),
    )

    val homework = listOf(
        HomeworkItem(
            id = "h1",
            note = "Læs kapitel 4 og løs opg. 1–5",
            activityTitle = "Matematik A",
            date = LocalDate.now().plusDays(1),
            team = "Ma A",
            href = "aktivitet/aktivitetforside.aspx?absid=demo-h1",
            detailHtml = null,
        ),
        HomeworkItem(
            id = "h2",
            note = "Analyser digtet side 42–45",
            activityTitle = "Dansk A",
            date = LocalDate.now().plusDays(2),
            team = "Da A",
            href = "aktivitet/aktivitetforside.aspx?absid=demo-h2",
        ),
    )

    fun homeworkDetailHtml(item: HomeworkItem): String =
        """
        <h2>${item.activityTitle}</h2>
        <p><b>Lektie:</b> ${item.note}</p>
        <p>Demo lektieindhold for hold ${item.team}. Gennemgå materialet inden timen.</p>
        <ul><li>Medbring bog</li><li>Noter fra sidste gang</li></ul>
        """.trimIndent()

    val assignments = listOf(
        AssignmentItem(
            id = "a1",
            title = "Rapport om bølger",
            team = "Fy B",
            week = LectioDateUtils.isoWeek(),
            deadline = LocalDateTime.now().plusDays(5).withHour(23).withMinute(59),
            status = "Afventer",
            studentTime = 5.0,
            awaits = "Elev",
            note = "",
        ),
        AssignmentItem(
            id = "a2",
            title = "Essay: modernisme",
            team = "Da A",
            week = LectioDateUtils.isoWeek() + 1,
            deadline = LocalDateTime.now().plusDays(12).withHour(12).withMinute(0),
            status = "Afleveret",
            studentTime = 8.0,
            awaits = "Lærer",
            note = "",
        ),
    )

    val grades = listOf(
        GradeRow("Ma A", "Matematik", "A", "10", "12", "10", null, null, null),
        GradeRow("Da A", "Dansk", "A", "7", "10", null, null, null, null),
        GradeRow("En A", "Engelsk", "A", "10", null, null, null, null, null),
    )

    val absence = AbsenceOverview(
        teams = listOf(
            AbsenceTeamRow(
                team = "Ma A",
                regularCurrentPercent = 0.02,
                regularFinalPercent = 0.03,
                assignmentCurrentPercent = 0.0,
                assignmentFinalPercent = 0.0,
            ),
            AbsenceTeamRow(
                team = "Da A",
                regularCurrentPercent = 0.05,
                regularFinalPercent = 0.05,
                assignmentCurrentPercent = 0.0,
                assignmentFinalPercent = 0.0,
            ),
            AbsenceTeamRow(
                team = "Fy B",
                regularCurrentPercent = 0.12,
                regularFinalPercent = 0.10,
                assignmentCurrentPercent = 0.0,
                assignmentFinalPercent = 0.0,
            ),
        ),
        registrations = listOf(
            AbsenceRegistration("r1", LocalDate.now().minusDays(3), "Fy B", "Sygdom", "Godkendt"),
        ),
    )

    val directory = listOf(
        DirectoryEntity(
            "S1", "Demo Elev", DirectoryEntityKind.STUDENT, "3x",
            avatarUrl = "https://www.gravatar.com/avatar/11111111111111111111111111111111?d=identicon&s=128",
        ),
        DirectoryEntity(
            "T1", "Jens Jensen", DirectoryEntityKind.TEACHER, "Matematik",
            avatarUrl = "https://www.gravatar.com/avatar/22222222222222222222222222222222?d=identicon&s=128",
        ),
        DirectoryEntity("C1", "3x", DirectoryEntityKind.CLASS, null),
        DirectoryEntity("R1", "201", DirectoryEntityKind.ROOM, "Bygning A"),
        DirectoryEntity(
            "S2", "Anna Andersen", DirectoryEntityKind.STUDENT, "3x",
            avatarUrl = "https://www.gravatar.com/avatar/33333333333333333333333333333333?d=identicon&s=128",
        ),
    )

    val plans = listOf(
        StudyPlan("p1", "Matematik A – studieplan", "Ma A"),
        StudyPlan("p2", "Dansk A – studieplan", "Da A"),
    )

    val moduleStats = listOf(
        ModuleStat("Ma A", 120, 4, 2),
        ModuleStat("Da A", 110, 6, 1),
        ModuleStat("Fy B", 90, 10, 0),
    )

    fun lessonDetail(event: ScheduleEvent) = LessonDetail(
        eventId = event.id,
        title = event.title,
        note = event.notes ?: "Demo-note: medbring lommeregner.",
        homework = event.homework ?: "Læs kap. 3",
        contentBlocks = listOf(
            LessonContentBlock("heading", "Indhold"),
            LessonContentBlock("paragraph", "Gennemgang af opgaver og fælles opsamling."),
            LessonContentBlock("note", "Husk bog."),
        ),
        participants = listOf(
            LessonParticipant("T1", "Jens Jensen", "Lærer"),
            LessonParticipant("S1", "Demo Elev", "Elev"),
            LessonParticipant("S2", "Anna Andersen", "Elev"),
        ),
        resources = listOf(
            LessonResource("Opgavesæt (PDF)", "https://www.lectio.dk/", isFile = true),
            LessonResource("Geogebra", "https://www.geogebra.org/", isFile = false),
        ),
    )

    val directoryMembers = mapOf(
        "C1" to listOf(
            DirectoryEntity("S1", "Demo Elev", DirectoryEntityKind.STUDENT, "3x"),
            DirectoryEntity("S2", "Anna Andersen", DirectoryEntityKind.STUDENT, "3x"),
            DirectoryEntity("S3", "Bo Berg", DirectoryEntityKind.STUDENT, "3x"),
        ),
    )

    val gradeNotes = mapOf(
        "Ma A" to listOf("God indsats til terminsprøve", "Mundtlig fremlæggelse: 10"),
        "Da A" to listOf("Essay mangler kildehenvisninger"),
    )
}
