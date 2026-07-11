package dk.betterlectio.android.feature.grades

import dk.betterlectio.android.core.cache.SimpleCache
import dk.betterlectio.android.core.lectio.LectioClient
import dk.betterlectio.android.core.lectio.session.SessionController
import dk.betterlectio.android.core.result.AppError
import dk.betterlectio.android.core.result.AppResult
import dk.betterlectio.android.feature.demo.DemoData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GradeRepository @Inject constructor(
    private val client: LectioClient,
    private val cache: SimpleCache,
    private val session: SessionController,
) {
    suspend fun load(forceRefresh: Boolean = false): AppResult<List<GradeRow>> {
        val student = session.currentStudent ?: return AppResult.Failure(AppError.Unauthorized)
        if (student.isDemo) {
            return AppResult.Success(
                DemoData.grades.map { row ->
                    row.copy(notes = DemoData.gradeNotes[row.team].orEmpty())
                },
            )
        }
        val key = "grades_${student.studentId}"
        if (!forceRefresh) cache.get(key)?.let { return AppResult.Success(GradeParser.parse(it)) }
        // Flutter/iOS: grades/grade_report.aspx?elevid=…
        val paths = listOf(
            "grades/grade_report.aspx?elevid=${student.studentId}",
            "grades/grade_report.aspx",
            "grades/grade_student.aspx",
            "Karakterer.aspx",
        )
        var lastFailure: AppResult.Failure? = null
        for (path in paths) {
            when (val res = client.get(path)) {
                is AppResult.Failure -> lastFailure = res
                is AppResult.Success -> {
                    cache.put(key, res.data.body)
                    return AppResult.Success(GradeParser.parse(res.data.body))
                }
            }
        }
        cache.get(key)?.let { return AppResult.Success(GradeParser.parse(it)) }
        return lastFailure ?: AppResult.Failure(AppError.Unknown("Kunne ikke hente karakterer"))
    }

    suspend fun loadSubjectDetail(row: GradeRow): AppResult<GradeSubjectDetail> {
        val student = session.currentStudent ?: return AppResult.Failure(AppError.Unauthorized)
        if (student.isDemo) {
            return AppResult.Success(
                GradeSubjectDetail(row, DemoData.gradeNotes[row.team] ?: listOf("Ingen noter")),
            )
        }
        // Notes live on grade_report page (Flutter); fallback grade_student_note
        val paths = listOf(
            "grades/grade_report.aspx?elevid=${student.studentId}",
            "grades/grade_report.aspx",
            "grades/grade_student_note.aspx",
        )
        for (path in paths) {
            when (val res = client.get(path)) {
                is AppResult.Success -> {
                    val notes = GradeParser.parseNotes(res.data.body)
                    if (notes.isNotEmpty()) {
                        return AppResult.Success(GradeSubjectDetail(row, notes))
                    }
                }
                is AppResult.Failure -> continue
            }
        }
        return AppResult.Success(GradeSubjectDetail(row, row.notes))
    }
}
