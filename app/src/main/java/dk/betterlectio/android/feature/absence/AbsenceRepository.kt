package dk.betterlectio.android.feature.absence

import dk.betterlectio.android.core.cache.SimpleCache
import dk.betterlectio.android.core.lectio.LectioClient
import dk.betterlectio.android.core.lectio.session.SessionController
import dk.betterlectio.android.core.result.AppError
import dk.betterlectio.android.core.result.AppResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AbsenceRepository @Inject constructor(
    private val client: LectioClient,
    private val cache: SimpleCache,
    private val session: SessionController,
    private val demoAbsenceState: DemoAbsenceState,
) {
    suspend fun loadOverview(forceRefresh: Boolean = false): AppResult<AbsenceOverview> {
        val student = session.currentStudent ?: return AppResult.Failure(AppError.Unauthorized)
        if (student.isDemo) return AppResult.Success(demoAbsenceState.overview())

        val key = "absence_${student.studentId}"
        if (!forceRefresh) {
            cache.get(key)?.let {
                return AppResult.Success(
                    AbsenceOverview(AbsenceParser.parseOverview(it), emptyList()),
                )
            }
        }
        return when (val res = client.get("subnav/fravaereloversigt.aspx")) {
            is AppResult.Failure -> {
                when (val alt = client.get("fravaer_oversigt.aspx")) {
                    is AppResult.Success -> {
                        cache.put(key, alt.data.body)
                        AppResult.Success(AbsenceOverview(AbsenceParser.parseOverview(alt.data.body)))
                    }
                    is AppResult.Failure -> res
                }
            }
            is AppResult.Success -> {
                cache.put(key, res.data.body)
                val teams = AbsenceParser.parseOverview(res.data.body)
                val regs = loadRegistrations()
                AppResult.Success(
                    AbsenceOverview(
                        teams = teams,
                        registrations = (regs as? AppResult.Success)?.data.orEmpty(),
                    ),
                )
            }
        }
    }

    suspend fun loadRegistrations(): AppResult<List<AbsenceRegistration>> {
        val student = session.currentStudent ?: return AppResult.Failure(AppError.Unauthorized)
        if (student.isDemo) return AppResult.Success(demoAbsenceState.registrations())
        return when (val res = client.get("subnav/fravaerelev_fravaersaarsager.aspx")) {
            is AppResult.Success -> AppResult.Success(AbsenceParser.parseRegistrations(res.data.body))
            is AppResult.Failure -> res
        }
    }

    /**
     * Best-effort cause update via ASP postback (Flutter absence update path).
     * Demo mutates [DemoAbsenceState] so [loadOverview] reflects the new cause.
     */
    suspend fun updateCause(registrationId: String, cause: String): AppResult<Unit> {
        if (session.currentStudent?.isDemo == true) {
            val ok = demoAbsenceState.updateCause(registrationId, cause)
            return if (ok) AppResult.Success(Unit)
            else AppResult.Failure(AppError.Unknown("Ukendt registrering: $registrationId"))
        }
        val path = "subnav/fravaerelev_fravaersaarsager.aspx"
        return when (val page = client.get(path)) {
            is AppResult.Failure -> {
                client.postback(
                    path,
                    "s\$m\$Content\$Content\$savebtn",
                    mapOf("cause" to cause, "id" to registrationId),
                ).let {
                    when (it) {
                        is AppResult.Success -> AppResult.Success(Unit)
                        is AppResult.Failure -> it
                    }
                }
            }
            is AppResult.Success -> {
                val html = page.data.body
                val causeField = dk.betterlectio.android.core.lectio.scrape.SmartPostback.findFieldName(
                    html,
                    listOf("cause", "aarsag", "fravaersaarsag", "Reason"),
                ) ?: "cause"
                val idField = dk.betterlectio.android.core.lectio.scrape.SmartPostback.findFieldName(
                    html,
                    listOf("id", "registration", "absid", "elevid"),
                ) ?: "id"
                val resolved = dk.betterlectio.android.core.lectio.scrape.SmartPostback.resolve(
                    html = html,
                    preferredTargets = listOf(
                        "s\$m\$Content\$Content\$savebtn",
                        "s\$m\$Content\$Content\$SaveBtn",
                        "m\$Content\$Content\$savebtn",
                    ),
                    extra = mapOf(causeField to cause, idField to registrationId),
                    nameContainsAny = listOf("save", "gem", "opdater"),
                )
                when (val post = client.postForm(path, resolved.fields)) {
                    is AppResult.Success -> AppResult.Success(Unit)
                    is AppResult.Failure -> post
                }
            }
        }
    }
}
