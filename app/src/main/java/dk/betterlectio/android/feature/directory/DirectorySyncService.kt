package dk.betterlectio.android.feature.directory

import dk.betterlectio.android.core.cache.SimpleCache
import dk.betterlectio.android.core.lectio.LectioClient
import dk.betterlectio.android.core.lectio.model.FetchPriority
import dk.betterlectio.android.core.lectio.session.SessionController
import dk.betterlectio.android.core.result.AppResult
import dk.betterlectio.android.feature.demo.DemoData
import dk.betterlectio.android.feature.offline.OfflineDirectoryStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opportunistic full-catalog snapshot + hold-member bootstrap after auth.
 * iOS parity: DirectorySyncService (no UI required; offline search works after sync).
 */
@Singleton
class DirectorySyncService @Inject constructor(
    private val client: LectioClient,
    private val session: SessionController,
    private val offline: OfflineDirectoryStore,
    private val cache: SimpleCache,
) {
    private val mutex = Mutex()
    @Volatile var lastSyncEpochMs: Long? = null
        private set

    /**
     * Fetch students/teachers/classes/holds/rooms lists and persist offline.
     * Safe to call multiple times; concurrent calls serialize.
     */
    suspend fun syncFullCatalog(): AppResult<Int> = mutex.withLock {
        val student = session.currentStudent
            ?: return AppResult.Failure(dk.betterlectio.android.core.result.AppError.Unauthorized)

        if (student.isDemo) {
            offline.saveAll(student.studentId, DemoData.directory)
            lastSyncEpochMs = System.currentTimeMillis()
            return AppResult.Success(DemoData.directory.size)
        }

        val kinds = listOf(
            DirectoryEntityKind.STUDENT to "FindSkema.aspx?type=elev",
            DirectoryEntityKind.TEACHER to "FindSkema.aspx?type=laerer",
            DirectoryEntityKind.CLASS to "FindSkema.aspx?type=klasse",
            DirectoryEntityKind.HOLD to "FindSkema.aspx?type=hold",
            DirectoryEntityKind.ROOM to "FindSkema.aspx?type=lokale",
        )
        val merged = linkedMapOf<String, DirectoryEntity>()
        for ((kind, path) in kinds) {
            when (val res = client.get(path, FetchPriority.Opportunistic)) {
                is AppResult.Failure -> {
                    Timber.w("Directory sync kind=%s failed: %s", kind, res.error)
                }
                is AppResult.Success -> {
                    cache.put("dir_${student.gymId}_$kind", res.data.body)
                    DirectoryParser.parseFindList(res.data.body, kind).forEach { e ->
                        merged[e.id] = e
                    }
                }
            }
        }
        offline.saveAll(student.studentId, merged.values.toList())
        lastSyncEpochMs = System.currentTimeMillis()
        Timber.i("Directory catalog cached %d entities", merged.size)
        // Bootstrap a few hold/class member lists for offline classmate search
        bootstrapHoldMembers(merged.values.filter {
            it.kind == DirectoryEntityKind.HOLD || it.kind == DirectoryEntityKind.CLASS
        }.take(12))
        AppResult.Success(merged.size)
    }

    /**
     * Load and cache members for the given holds/classes (best-effort).
     */
    suspend fun bootstrapHoldMembers(holds: List<DirectoryEntity>) {
        val student = session.currentStudent ?: return
        if (student.isDemo) return
        for (hold in holds) {
            val path = when (hold.kind) {
                DirectoryEntityKind.CLASS, DirectoryEntityKind.HOLD ->
                    "subnav/members.aspx?holdelementid=${hold.id}"
                else -> continue
            }
            when (val res = client.get(path, FetchPriority.Opportunistic)) {
                is AppResult.Success -> {
                    val members = DirectoryParser.parseMembers(res.data.body, hold)
                    if (members.isNotEmpty()) {
                        val key = "dir_members_${student.gymId}_${hold.id}"
                        cache.put(key, res.data.body)
                        // Merge members into offline catalog
                        val existing = offline.loadAll(student.studentId)
                        offline.saveAll(student.studentId, (existing + members).distinctBy { it.id })
                    }
                }
                is AppResult.Failure -> Timber.w("Hold members bootstrap failed for %s", hold.id)
            }
        }
    }
}
