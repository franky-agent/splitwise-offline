package dev.splitwise.offline.sync

import dev.splitwise.offline.data.repo.GroupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the sync protocol over a [SyncTransport].
 *
 * Protocol (intentionally simple — full-state CRDT exchange):
 *   1. When we connect to a peer, we send a [SyncMessage.SyncRequest] carrying our
 *      current (groupId -> contentHash) map.
 *   2. The peer compares against its own hashes and replies with a
 *      [SyncMessage.SyncPayload] of the groups that differ (or that we don't have).
 *   3. On receipt, we [GroupRepository.mergeIncoming] every group — CRDT merge
 *      guarantees convergence — and, if our state changed, we re-broadcast a fresh
 *      SyncRequest so the peer can pull our updates too.
 *
 * Because merges are idempotent, re-broadcasting is safe and self-terminating:
 * once both sides agree on every hash, no further payloads are produced.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val transport: SyncTransport,
    private val repo: GroupRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()
    private val TAG = "SyncEngine"

    fun start(deviceId: String) {
        if (_active.value) return
        _active.value = true
        // Listen for incoming messages for the lifetime of the engine.
        scope.launch {
            transport.incoming.collectLatest { msg -> handleIncoming(msg, deviceId) }
        }
        // When a peer connects (either transport), kick off a round.
        scope.launch {
            transport.isConnected.collectLatest { connected ->
                if (connected) sendSyncRequest(deviceId)
            }
        }
        // When a NEW peer joins a room we're in (relay presence signal), re-send our
        // sync request so they can pull our state. Critical for non-co-located sync where
        // our connection stays open but a new device comes online later.
        scope.launch {
            transport.peerJoined.collectLatest { sendSyncRequest(deviceId) }
        }
    }

    fun stop() {
        _active.value = false
        transport.stop()
    }

    /** Push our current hashes to all peers so they can reply with diffs. */
    private suspend fun sendSyncRequest(deviceId: String) {
        runCatching {
            val hashes = repo.allHashes().associate { it.id to it.contentHash }
            transport.broadcast(SyncMessage.SyncRequest(senderDeviceId = deviceId, hashes = hashes))
        }.onFailure { Log.w(TAG, "sendSyncRequest failed", it) }
    }

    private suspend fun handleIncoming(msg: SyncMessage, deviceId: String) {
        when (msg) {
            is SyncMessage.SyncRequest -> {
                // Peer asked for our groups that differ. Send any whose hash differs
                // (plus any the peer doesn't have at all).
                val ours = repo.allHashes().associate { it.id to it.contentHash }
                val toSend = mutableListOf<dev.splitwise.offline.domain.model.Group>()
                for (hash in ours) {
                    if (msg.hashes[hash.key] != hash.value) {
                        repo.getGroup(hash.key)?.let { toSend.add(it) }
                    }
                }
                // Also pull any groups the peer has that we don't — request them by
                // replying with a request that has empty hashes for those ids.
                if (toSend.isNotEmpty()) {
                    transport.broadcast(
                        SyncMessage.SyncPayload(senderDeviceId = deviceId, groups = toSend)
                    )
                }
                // If the peer has groups we lack, ask for them: send back a request
                // whose hashes only cover groups we already have.
                val weLack = msg.hashes.keys - ours.keys
                if (weLack.isNotEmpty()) {
                    val ask = ours.filterKeys { it !in weLack }
                    transport.broadcast(SyncMessage.SyncRequest(senderDeviceId = deviceId, hashes = ask))
                }
            }

            is SyncMessage.SyncPayload -> {
                var changed = false
                for (g in msg.groups) {
                    val merged = repo.mergeIncoming(g)
                    if (merged != null) changed = true
                }
                // If we learned something new, share back so the peer converges too.
                if (changed) sendSyncRequest(deviceId)
            }
        }
    }
}