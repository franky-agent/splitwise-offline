package dev.splitwise.offline.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [SyncTransport] the app actually uses: a composition of [NearbyConnectionsTransport]
 * (when devices are in proximity, no network) and [RelaySyncTransport] (when devices are
 * not co-located, via the minimal storage-free relay).
 *
 * From the [SyncEngine]'s point of view this is just one transport — the engine's CRDT
 * sync protocol is unchanged. The composite:
 *   - merges `incoming` from both child transports, deduplicating by message identity
 *     (so a message that arrives over both paths is handled once);
 *   - ORs `isConnected` (true if either child is connected);
 *   - picks the "best" status for the UI (CONNECTED > SEARCHING > ERROR > IDLE);
 *   - broadcasts to BOTH children (Nearby peers and remote peers converge together).
 *
 * This is the architectural payoff of the pluggable-transport design: adding remote
 * sync was a new transport class + this composite, with zero changes to the data layer,
 * CRDT merge logic, or sync protocol.
 *
 * @param appScope a long-lived coroutine scope (provided by Hilt as @ApplicationScope)
 *        used to merge child flows for the lifetime of the transport.
 */
@Singleton
class CompositeSyncTransport @Inject constructor(
    private val nearby: NearbyConnectionsTransport,
    private val relay: RelaySyncTransport,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : SyncTransport {

    private val _incoming = MutableSharedFlow<SyncMessage>(extraBufferCapacity = 128)
    override val incoming: Flow<SyncMessage> = _incoming.asSharedFlow()

    override val isConnected: StateFlow<Boolean> =
        combine(nearby.isConnected, relay.isConnected) { a, b -> a || b }
            .stateIn(appScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    override val status: StateFlow<TransportStatus> =
        combine(nearby.status, relay.status) { a, b -> bestOf(a, b) }
            .stateIn(appScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, TransportStatus.IDLE)

    /** Merge peer-joined signals from both transports. */
    override val peerJoined: Flow<String> = merge(nearby.peerJoined, relay.peerJoined)

    private var collecting = false

    /** Start merging child incoming flows (idempotent). */
    private fun ensureCollecting() {
        if (collecting) return
        collecting = true
        val seen = mutableSetOf<String>()
        appScope.launch { nearby.incoming.collect { handle(it, seen) } }
        appScope.launch { relay.incoming.collect { handle(it, seen) } }
    }

    private suspend fun handle(msg: SyncMessage, seen: MutableSet<String>) {
        val key = dedupKey(msg)
        if (seen.size > 4096) seen.clear()
        if (!seen.add(key)) return
        _incoming.emit(msg)
    }

    /** A stable identity for a message so we can drop duplicates arriving via both transports. */
    private fun dedupKey(msg: SyncMessage): String = when (msg) {
        is SyncMessage.SyncRequest -> "req:${msg.senderDeviceId}:${msg.hashes.hashCode()}"
        is SyncMessage.SyncPayload -> "pay:${msg.senderDeviceId}:${msg.groups.joinToString { it.id + ":" + it.contentHash() }}"
    }

    override fun startAdvertising(deviceId: String) {
        ensureCollecting()
        nearby.startAdvertising(deviceId)
        relay.start(deviceId)
    }

    override fun startDiscovering(deviceId: String) {
        ensureCollecting()
        nearby.startDiscovering(deviceId)
        relay.start(deviceId)
    }

    override suspend fun broadcast(message: SyncMessage) {
        ensureCollecting()
        // Send to both transports. Failures in one don't block the other.
        runCatching { relay.broadcast(message) }
        runCatching { nearby.broadcast(message) }
    }

    override fun stop() {
        nearby.stop()
        relay.stop()
    }

    private fun bestOf(a: TransportStatus, b: TransportStatus): TransportStatus {
        val rank = { s: TransportStatus -> when (s) {
            TransportStatus.CONNECTED -> 3
            TransportStatus.SEARCHING -> 2
            TransportStatus.ERROR -> 1
            TransportStatus.IDLE -> 0
        } }
        return if (rank(a) >= rank(b)) a else b
    }
}