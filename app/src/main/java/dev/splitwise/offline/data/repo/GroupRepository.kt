package dev.splitwise.offline.data.repo

import dev.splitwise.offline.data.dao.GroupDao
import dev.splitwise.offline.data.entity.GroupEntity
import dev.splitwise.offline.domain.model.Group
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of truth on device.
 *
 * Everything that touches groups goes through here. The repository serializes the
 * full Group CRDT to JSON for storage, so Room just holds opaque blobs and the
 * domain layer owns all merge logic. Sync incoming = load + merge + upsert.
 */
@Singleton
class GroupRepository @Inject constructor(
    private val dao: GroupDao,
    private val json: Json,
) {

    fun observeAll(): Flow<List<Group>> =
        dao.observeAll().map { rows -> rows.map { decode(it) } }

    fun observeGroup(id: String): Flow<Group?> =
        dao.observeById(id).map { it?.let(::decode) }

    suspend fun getGroup(id: String): Group? = dao.getById(id)?.let(::decode)

    suspend fun allHashes(): List<GroupDao.HashRow> = dao.allHashes()

    suspend fun createGroup(name: String, currency: String, deviceId: String, now: Long = System.currentTimeMillis()): Group {
        val group = Group.new(UUID.randomUUID().toString(), name, currency, deviceId, now)
        save(group)
        return group
    }

    /** Apply a local edit: bump the device's clock component for this group, persist. */
    suspend fun mutateGroup(groupId: String, deviceId: String, transform: (Group) -> Group): Group? {
        val current = dao.getById(groupId)?.let(::decode) ?: return null
        val bumped = current.copy(clock = current.clock.increment(deviceId), updatedAt = System.currentTimeMillis())
        val next = transform(bumped)
        save(next)
        return next
    }

    /** Merge an incoming CRDT replica and persist. Returns the merged group (or null if no-op). */
    suspend fun mergeIncoming(incoming: Group): Group? {
        val local = dao.getById(incoming.id)?.let(::decode)
        val merged = if (local != null) local.merge(incoming) else incoming
        // Idempotency: if nothing changed, skip the write.
        if (local != null && local.contentHash() == merged.contentHash()) return null
        save(merged)
        return merged
    }

    private suspend fun save(group: Group) {
        dao.upsert(
            GroupEntity(
                id = group.id,
                name = group.displayName,
                currency = group.currency.value,
                contentHash = group.contentHash(),
                updatedAt = group.updatedAt,
                payloadJson = json.encodeToString(Group.serializer(), group),
            )
        )
    }

    private fun decode(row: GroupEntity): Group = json.decodeFromString(Group.serializer(), row.payloadJson)
}