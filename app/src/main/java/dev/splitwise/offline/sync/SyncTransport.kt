package dev.splitwise.offline.sync

import dev.splitwise.offline.domain.model.Group
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the P2P link. The app never talks to a specific transport
 * directly — it depends on this interface, so we can swap [NearbyConnectionsTransport]
 * for a Google-Drive-file transport, a WebRTC relay, or even a QR-code / NFC exchange
 * without touching the data layer or UI.
 *
 * Lifecycle:
 *   1. [startAdvertising] / [startDiscovering] to find peers.
 *   2. [incoming] emits [SyncMessage]s received from any connected peer.
 *   3. [broadcast] pushes a message to all currently connected peers.
 *   4. [stop] tears down connections.
 */
interface SyncTransport {

    /** Whether the transport currently has ≥1 connected peer. */
    val isConnected: Flow<Boolean>

    /** Human-readable status for the UI ("Idle", "Searching…", "Connected to 2 devices"). */
    val status: Flow<TransportStatus>

    /** Messages received from peers. */
    val incoming: Flow<SyncMessage>

    fun startAdvertising(deviceId: String)
    fun startDiscovering(deviceId: String)
    suspend fun broadcast(message: SyncMessage)
    fun stop()
}

enum class TransportStatus { IDLE, SEARCHING, CONNECTED, ERROR }