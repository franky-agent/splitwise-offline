package dev.splitwise.offline.sync

import dev.splitwise.offline.domain.model.Group
import kotlinx.serialization.Serializable

/**
 * Wire envelope for the P2P link. Versioned so we can evolve the protocol.
 *
 * Two message kinds:
 *  - [SyncRequest]: "here are my group hashes; send me any that differ".
 *  - [SyncPayload]: the actual group CRDTs (full-state for the POC; in production
 *    you'd delta-pack against a common ancestor hash).
 */
@Serializable
sealed class SyncMessage {

    @Serializable
    data class SyncRequest(
        val senderDeviceId: String,
        /** groupId -> contentHash the sender currently has. */
        val hashes: Map<String, String>,
    ) : SyncMessage()

    @Serializable
    data class SyncPayload(
        val senderDeviceId: String,
        val groups: List<Group>,
    ) : SyncMessage()

    fun senderDeviceId(): String = when (this) {
        is SyncRequest -> senderDeviceId
        is SyncPayload -> senderDeviceId
    }
}