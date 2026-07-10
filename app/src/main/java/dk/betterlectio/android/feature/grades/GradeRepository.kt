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
        return when (val res = client.get("grades/grade_student.aspx")) {
            is AppResult.Failure -> {
                cache.get(key)?.let { return AppResult.Success(GradeParser.parse(it)) }
                when (val alt = client.get("Karakterer.aspx")) {
                    is AppResult.Success -> {
                        cache.put(key, alt.data.body)
                        AppResult.Success(GradeParser.parse(alt.data.body))
                    }
                    is AppResult.Failure -> res
                }
            }
            is AppResult.Success -> {
                cache.put(key, res.data.body)
                AppResult.Success(GradeParser.parse(res.data.body))
            }
        }
    }

    suspend fun loadSubjectDetail(row: GradeRow): AppResult<GradeSubjectDetail> {
        val student = session.currentStudent ?: return AppResult.Failure(AppError.Unauthorized)
        if (student.isDemo) {
            return AppResult.Success(
                GradeSubjectDetail(row, DemoData.gradeNotes[row.team] ?: listOf("Ingen noter")),
            )
        }
        return when (val res = client.get("grades/grade_student_note.aspx")) {
            is AppResult.Success -> {
                val notes = GradeParser.parseNotes(res.data.body)
                AppResult.Success(GradeSubjectDetail(row, notes.ifEmpty { row.notes }))
            }
            is AppResult.Failure -> AppResult.Success(GradeSubjectDetail(row, row.notes))
        }
    }
}
