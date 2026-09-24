package dk.betterlectio.android.feature.assignments

import dk.betterlectio.android.core.cache.SimpleCache
import dk.betterlectio.android.core.cache.CacheFreshness
import dk.betterlectio.android.core.cache.CachePolicy
import dk.betterlectio.android.core.cache.freshness
import dk.betterlectio.android.core.lectio.LectioClient
import dk.betterlectio.android.core.lectio.session.SessionController
import dk.betterlectio.android.core.result.AppError
import dk.betterlectio.android.core.result.AppResult
import dk.betterlectio.android.feature.demo.DemoData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssignmentRepository @Inject constructor(
    private val client: LectioClient,
    private val cache: SimpleCache,
    private val session: SessionController,
) {
    suspend fun load(forceRefresh: Boolean = false): AppResult<List<AssignmentItem>> {
        val student = session.currentStudent
            ?: return AppResult.Failure(AppError.Unauthorized)
        if (student.isDemo) return AppResult.Success(DemoData.assignments)

        val key = "assignments_${student.studentId}"
        if (!forceRefresh) {
            cache.get(key)?.let { return AppResult.Success(AssignmentParser.parseList(it)) }
        }
        return when (val res = client.get("OpgaverElev.aspx")) {
            is AppResult.Failure -> {
                cache.get(key)?.let { return AppResult.Success(AssignmentParser.parseList(it)) }
                res
            }
            is AppResult.Success -> {
                cache.put(key, res.data.body)
                AppResult.Success(AssignmentParser.parseList(res.data.body))
            }
        }
    }

    fun cacheFreshness(): CacheFreshness {
        val student = session.currentStudent ?: return CacheFreshness.MISSING
        if (student.isDemo) return CacheFreshness.FRESH
        return cache.freshness("assignments_${student.studentId}", CachePolicy.MAIN_DATA)
    }

    suspend fun loadDetail(
        item: AssignmentItem,
        forceRefresh: Boolean = false,
    ): AppResult<AssignmentDetail> {
        val student = session.currentStudent ?: return AppResult.Failure(AppError.Unauthorized)
        if (student.isDemo) {
            return AppResult.Success(
                AssignmentDetail(
                    item = item,
                    description = item.note.ifBlank { "Demo-opgavebeskrivelse for ${item.title}." },
                    files = listOf("Opgave.pdf" to "https://www.lectio.dk/demo.pdf"),
                    responsible = "Jens Jensen",
                    grading = "7-trinsskala",
                ),
            )
        }
        val path = "ElevAflevering.aspx?elevid=${student.studentId}&exerciseid=${item.id}"
        val cacheKey = "assignment_detail_${student.studentId}_${item.id}"
        val cached = cache.getWithMeta(cacheKey)
        if (!forceRefresh && cached?.freshness(CachePolicy.MUTABLE_DETAIL) == CacheFreshness.FRESH) {
            return AppResult.Success(AssignmentParser.parseDetail(cached.value, item, student.studentId))
        }
        return when (val res = client.get(path)) {
            is AppResult.Failure -> AppResult.Success(
                cached?.let { AssignmentParser.parseDetail(it.value, item, student.studentId) }
                    ?: AssignmentDetail(item = item, description = item.note),
            )
            is AppResult.Success -> {
                cache.put(cacheKey, res.data.body)
                AppResult.Success(AssignmentParser.parseDetail(res.data.body, item, student.studentId))
            }
        }
    }
}
