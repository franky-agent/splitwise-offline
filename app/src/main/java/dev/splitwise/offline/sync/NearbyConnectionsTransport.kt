package dev.splitwise.offline.sync

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.splitwise.offline.domain.crdt.Hashing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import android.util.Log
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SyncTransport] implemented on top of Google Nearby Connections.
 *
 * Nearby Connections gives us a serverless, encrypted, peer-to-peer mesh over
 * Wi-Fi Direct / BLE — devices discover each other in proximity, form a star/mesh,
 * and exchange bytes with no cloud relay for payload data. Perfect for a
 * "no server infrastructure" Splitwise: two phones in the same room sync directly.
 *
 * Wire format: each [Payload] = 4-byte big-endian length prefix + UTF-8 JSON of a
 * [SyncMessage]. (Length-prefixing lets us frame multiple messages on one stream.)
 */
@Singleton
class NearbyConnectionsTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : SyncTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serviceId = "dev.splitwise.offline.v1"
    private val TAG = "NearbyTransport"

    private val _status = MutableStateFlow(TransportStatus.IDLE)
    private val _connected = MutableStateFlow(false)
    private val _incoming = MutableSharedFlow<SyncMessage>(extraBufferCapacity = 64)

    override val isConnected: StateFlow<Boolean> = _connected.asStateFlow()
    override val status: StateFlow<TransportStatus> = _status.asStateFlow()
    override val incoming: Flow<SyncMessage> = _incoming.asSharedFlow()

    // endpointId -> friendly name (for status reporting)
    private val endpoints = mutableMapOf<String, String>()

    override fun startAdvertising(deviceId: String) {
        _status.value = TransportStatus.SEARCHING
        Nearby.getConnectionsClient(context).startAdvertising(
            deviceId,     // advertised name = our device id
            serviceId,
            advertisingConnectionCallback,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build(),
        )
    }

    override fun startDiscovering(deviceId: String) {
        _status.value = TransportStatus.SEARCHING
        Nearby.getConnectionsClient(context).startDiscovery(
            serviceId,
            discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build(),
        )
    }

    override suspend fun broadcast(message: SyncMessage) {
        val bytes = encode(message)
        val payload = Payload.fromBytes(bytes)
        // Send to every connected endpoint.
        endpoints.keys.toList().forEach { id ->
            Nearby.getConnectionsClient(context).sendPayload(id, payload)
        }
    }

    override fun stop() {
        Log.d(TAG, "stop transport")
        Nearby.getConnectionsClient(context).stopAdvertising()
        Nearby.getConnectionsClient(context).stopDiscovery()
        Nearby.getConnectionsClient(context).stopAllEndpoints()
        endpoints.clear()
        _connected.value = false
        _status.value = TransportStatus.IDLE
    }

    private fun encode(message: SyncMessage): ByteArray {
        val text = json.encodeToString(SyncMessage.serializer(), message).toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(4 + text.size).putInt(text.size).put(text)
        return buf.array()
    }

    private fun decode(data: ByteArray): SyncMessage? = try {
        val len = ByteBuffer.wrap(data, 0, 4).int
        require(len + 4 == data.size) { "bad framing" }
        val text = String(data, 4, len, Charsets.UTF_8)
        json.decodeFromString(SyncMessage.serializer(), text)
    } catch (t: Throwable) {
        Log.w(TAG, "failed to decode sync message", t)
        null
    }

    // --- Discovery (we found a peer to connect to) ---
    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: com.google.android.gms.nearby.connection.DiscoveredEndpointInfo) {
            Nearby.getConnectionsClient(context)
                .requestConnection(info.endpointName, endpointId, requestConnectionCallback)
        }

        override fun onEndpointLost(endpointId: String) {
            endpoints.remove(endpointId)
            _connected.value = endpoints.isNotEmpty()
        }
    }

    // --- Advertising (a peer is asking to connect to us) ---
    private val advertisingConnectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Nearby.getConnectionsClient(context)
                .acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                endpoints[endpointId] = endpointId
                _connected.value = true
                _status.value = TransportStatus.CONNECTED
            }
        }

        override fun onDisconnected(endpointId: String) {
            endpoints.remove(endpointId)
            _connected.value = endpoints.isNotEmpty()
            if (endpoints.isEmpty()) _status.value = TransportStatus.SEARCHING
        }
    }

    // --- Connection requests we initiate (from discovery) ---
    private val requestConnectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Nearby.getConnectionsClient(context)
                .acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                endpoints[endpointId] = endpointId
                _connected.value = true
                _status.value = TransportStatus.CONNECTED
            }
        }

        override fun onDisconnected(endpointId: String) {
            endpoints.remove(endpointId)
            _connected.value = endpoints.isNotEmpty()
            if (endpoints.isEmpty()) _status.value = TransportStatus.SEARCHING
        }
    }

    // --- Receiving data ---
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            decode(bytes)?.let { msg -> scope.launchSafe { _incoming.emit(msg) } }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // no-op: we use BYTES payloads which arrive atomically
        }
    }
}

private fun CoroutineScope.launchSafe(block: suspend () -> Unit) {
    kotlinx.coroutines.launch { runCatching { block() } }
}