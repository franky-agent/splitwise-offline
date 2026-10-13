package dev.splitwise.offline.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted group. The full CRDT payload (members, expenses, clock) is stored as
 * a serialized JSON blob — this keeps the offline-first local replica exactly
 * reproducible and makes sync trivial (one row = one group CRDT).
 *
 * We also denormalize a few fields ([name], [contentHash]) for cheap list queries
 * and "do we agree?" checks without deserializing the whole blob.
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currency: String,
    val contentHash: String,
    val updatedAt: Long,
    val payloadJson: String,  // serialized Group CRDT
)