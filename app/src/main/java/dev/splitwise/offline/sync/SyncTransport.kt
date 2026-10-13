package dev.splitwise.offline.sync

import dev.splitwise.offline.domain.model.Group
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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
 *
 * v2 addition: [peerJoined] emits the room id (group id) when a NEW peer appears in a
 * room we've joined — used by relay-style transports so already-connected devices
 * learn that someone new came online and should be sent a fresh sync request.
 * Nearby-style transports emit nothing here (their [isConnected] already toggles on
 * each new peer).
 */
interface SyncTransport {

    /** Whether the transport currently has ≥1 connected peer. */
    val isConnected: Flow<Boolean>

    /** Human-readable status for the UI ("Idle", "Searching…", "Connected to 2 devices"). */
    val status: Flow<TransportStatus>

    /** Messages received from peers. */
    val incoming: Flow<SyncMessage>

    /** Emits a room id (group id) when a new peer joins that room. Default: never. */
    val peerJoined: Flow<String> get() = emptyFlow()

    fun startAdvertising(deviceId: String)
    fun startDiscovering(deviceId: String)
    suspend fun broadcast(message: SyncMessage)
    fun stop()
}

enum class TransportStatus { IDLE, SEARCHING, CONNECTED, ERROR }