package dk.betterlectio.android.feature.offline

import dk.betterlectio.android.feature.directory.DirectoryEntity
import dk.betterlectio.android.feature.directory.DirectoryEntityKind
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineDirectoryStore @Inject constructor(
    private val db: OfflineDatabase,
) {
    private val dao get() = db.directoryDao()

    suspend fun loadAll(studentId: String): List<DirectoryEntity> =
        dao.loadAll(studentId).map { it.toModel() }

    suspend fun saveAll(studentId: String, entities: List<DirectoryEntity>) {
        val now = System.currentTimeMillis()
        dao.upsertAll(
            entities.map { e ->
                DirectoryEntityRow(
                    compositeKey = "$studentId|${e.id}",
                    studentId = studentId,
                    entityId = e.id,
                    name = e.name,
                    kind = e.kind.name,
                    subtitle = e.subtitle,
                    avatarUrl = e.avatarUrl,
                    updatedAt = now,
                )
            },
        )
    }

    private fun DirectoryEntityRow.toModel() = DirectoryEntity(
        id = entityId,
        name = name,
        kind = runCatching { DirectoryEntityKind.valueOf(kind) }
            .getOrDefault(DirectoryEntityKind.OTHER),
        subtitle = subtitle,
        avatarUrl = avatarUrl,
    )
}
