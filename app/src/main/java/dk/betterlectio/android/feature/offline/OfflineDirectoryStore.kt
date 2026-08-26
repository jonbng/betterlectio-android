package dk.betterlectio.android.feature.offline

import dk.betterlectio.android.feature.directory.DirectoryEntity
import dk.betterlectio.android.feature.directory.DirectoryEntityKind
import dk.betterlectio.android.feature.directory.DirectoryParser
import dk.betterlectio.android.feature.offline.OfflineDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineDirectoryStore @Inject constructor(
    private val db: OfflineDatabase,
) {
    private val dao get() = db.directoryDao()

    suspend fun loadAll(studentId: String): List<DirectoryEntity> =
        dao.loadAll(studentId).map { it.toModel() }

    /**
     * Upsert entities without removing other rows (e.g. hold-member bootstrap).
     * Merges with existing rows so class-code / initials labels never overwrite real names,
     * and avatar URLs are preserved when the incoming row lacks one.
     */
    suspend fun saveAll(studentId: String, entities: List<DirectoryEntity>) {
        if (entities.isEmpty()) return
        val existingById = loadAll(studentId).associateBy { it.id }
        val merged = entities.map { incoming ->
            DirectoryParser.mergeEntity(existingById[incoming.id], incoming)
        }
        val now = System.currentTimeMillis()
        dao.upsertAll(
            merged.map { e ->
                DirectoryEntityRow(
                    compositeKey = "$studentId|${e.id}",
                    studentId = studentId,
                    entityId = e.id,
                    name = e.name,
                    kind = e.kind.name,
                    subtitle = e.subtitle,
                    avatarUrl = e.avatarUrl,
                    avatarUpdatedAt = when {
                        e.avatarUrl.isNullOrBlank() -> existingById[e.id]?.avatarUpdatedAt
                        e.avatarUrl != existingById[e.id]?.avatarUrl -> now
                        else -> existingById[e.id]?.avatarUpdatedAt ?: now
                    },
                    updatedAt = now,
                )
            },
        )
    }

    /**
     * Replace the full offline snapshot for [studentId] (iOS `replaceDirectorySnapshot`).
     * Clears prior rows first so bad HTML scrapes and stale entities are purged.
     * Preserves previously resolved [DirectoryEntity.avatarUrl] values by entity id.
     */
    suspend fun replaceAll(studentId: String, entities: List<DirectoryEntity>) {
        val previousAvatars = loadAll(studentId).associateBy { it.id }
        val merged = entities.map { e ->
            val previous = previousAvatars[e.id]
            if (e.avatarUrl.isNullOrBlank() && !previous?.avatarUrl.isNullOrBlank()) {
                e.copy(avatarUrl = previous?.avatarUrl, avatarUpdatedAt = previous?.avatarUpdatedAt)
            } else {
                e
            }
        }
        dao.clearStudent(studentId)
        saveAll(studentId, merged)
    }

    /**
     * Patch a single entity's avatar URL without rewriting the full catalog.
     * No-op when the entity row is not yet offline.
     */
    suspend fun updateAvatarUrl(studentId: String, entityId: String, avatarUrl: String) {
        if (avatarUrl.isBlank()) return
        updateAvatarObservation(studentId, entityId, avatarUrl)
    }

    /**
     * Persist the result of a successful Lectio avatar check.
     * A null URL is authoritative (the person currently has no portrait), while
     * transient request failures must not call this method.
     */
    suspend fun updateAvatarObservation(studentId: String, entityId: String, avatarUrl: String?) {
        val existing = dao.loadAll(studentId).firstOrNull { it.entityId == entityId } ?: return
        val now = System.currentTimeMillis()
        dao.upsertAll(
            listOf(
                existing.copy(
                    avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
                    avatarUpdatedAt = now,
                    updatedAt = now,
                ),
            ),
        )
    }

    private fun DirectoryEntityRow.toModel() = DirectoryEntity(
        id = entityId,
        name = name,
        kind = runCatching { DirectoryEntityKind.valueOf(kind) }
            .getOrDefault(DirectoryEntityKind.OTHER),
        subtitle = subtitle,
        avatarUrl = avatarUrl,
        avatarUpdatedAt = avatarUpdatedAt,
    )

    suspend fun isAvatarStale(studentId: String, entityId: String, now: Long = System.currentTimeMillis()): Boolean {
        val entity = loadAll(studentId).firstOrNull { it.id == entityId } ?: return true
        val observedAt = entity.avatarUpdatedAt ?: return true
        return now - observedAt >= AVATAR_REFRESH_MS
    }

    private companion object {
        const val AVATAR_REFRESH_MS = 24 * 60 * 60 * 1000L
    }
}
