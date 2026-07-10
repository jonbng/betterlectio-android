package dk.betterlectio.android.feature.directory

import dk.betterlectio.android.core.cache.SimpleCache
import dk.betterlectio.android.core.lectio.LectioClient
import dk.betterlectio.android.core.lectio.session.SessionController
import dk.betterlectio.android.core.result.AppError
import dk.betterlectio.android.core.result.AppResult
import dk.betterlectio.android.feature.demo.DemoData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectoryRepository @Inject constructor(
    private val client: LectioClient,
    private val cache: SimpleCache,
    private val session: SessionController,
    private val offline: dk.betterlectio.android.feature.offline.OfflineDirectoryStore,
) {
    suspend fun search(query: String, kind: DirectoryEntityKind? = null): AppResult<List<DirectoryEntity>> {
        val student = session.currentStudent ?: return AppResult.Failure(AppError.Unauthorized)
        if (student.isDemo) {
            val q = query.trim().lowercase()
            val all = DemoData.directory
            return AppResult.Success(
                all.filter {
                    (kind == null || it.kind == kind) &&
                        (q.isEmpty() || it.name.lowercase().contains(q) ||
                            it.subtitle?.lowercase()?.contains(q) == true)
                },
            )
        }

        val path = when (kind) {
            DirectoryEntityKind.TEACHER -> "FindSkema.aspx?type=laerer"
            DirectoryEntityKind.CLASS -> "FindSkema.aspx?type=klasse"
            DirectoryEntityKind.ROOM -> "FindSkema.aspx?type=lokale"
            DirectoryEntityKind.HOLD -> "FindSkema.aspx?type=hold"
            else -> "FindSkema.aspx?type=elev"
        }
        val key = "dir_${student.gymId}_${kind ?: "all"}"
        fun filterParsed(parsed: List<DirectoryEntity>): List<DirectoryEntity> {
            val q = query.trim().lowercase()
            return if (q.isEmpty()) parsed
            else parsed.filter {
                it.name.lowercase().contains(q) ||
                    it.subtitle?.lowercase()?.contains(q) == true
            }.let { list ->
                if (kind == null) list else list.filter { it.kind == kind }
            }
        }

        cache.get(key)?.let { html ->
            val parsed = DirectoryParser.parseFindList(html, kind ?: DirectoryEntityKind.STUDENT)
            return AppResult.Success(filterParsed(parsed))
        }

        return when (val res = client.get(path)) {
            is AppResult.Failure -> {
                val room = offline.loadAll(student.studentId)
                if (room.isNotEmpty()) AppResult.Success(filterParsed(room))
                else res
            }
            is AppResult.Success -> {
                cache.put(key, res.data.body)
                val parsed = DirectoryParser.parseFindList(res.data.body, kind ?: DirectoryEntityKind.STUDENT)
                offline.saveAll(student.studentId, parsed)
                AppResult.Success(filterParsed(parsed))
            }
        }
    }

    /** Members of a class/hold (demo always works; live parses elevliste HTML). */
    suspend fun loadMembers(entity: DirectoryEntity): AppResult<List<DirectoryEntity>> {
        val student = session.currentStudent ?: return AppResult.Failure(AppError.Unauthorized)
        if (student.isDemo) {
            return AppResult.Success(
                DemoData.directoryMembers[entity.id]
                    ?: listOf(
                        DirectoryEntity("S1", "Demo Elev", DirectoryEntityKind.STUDENT, entity.name),
                        DirectoryEntity("S2", "Anna Andersen", DirectoryEntityKind.STUDENT, entity.name),
                    ),
            )
        }
        val path = when (entity.kind) {
            DirectoryEntityKind.CLASS, DirectoryEntityKind.HOLD ->
                "subnav/members.aspx?holdelementid=${entity.id}"
            else -> "FindSkemaBew.aspx?type=elev&nosubnav=1&relatedto=${entity.id}"
        }
        return when (val res = client.get(path)) {
            is AppResult.Failure -> when (val alt = client.get("ElevKlasseListe.aspx")) {
                is AppResult.Success -> AppResult.Success(DirectoryParser.parseMembers(alt.data.body, entity))
                is AppResult.Failure -> res
            }
            is AppResult.Success -> AppResult.Success(DirectoryParser.parseMembers(res.data.body, entity))
        }
    }

    /** Offline catalog snapshot for search-before-network UX. */
    suspend fun offlineCatalog(): List<DirectoryEntity> {
        val student = session.currentStudent ?: return emptyList()
        return offline.loadAll(student.studentId)
    }
}
