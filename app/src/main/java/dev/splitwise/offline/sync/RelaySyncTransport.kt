package dev.splitwise.offline.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SyncTransport] over a WebSocket relay — enables sync between devices that are
 * NOT in proximity (v2 requirement). The relay is a minimal, storage-free message
 * router (see `relay/server.js`); this client just connects to it and routes
 * [SyncMessage]s to/from the right "room" (group id).
 *
 * Lifecycle:
 *   - [start] opens a WebSocket to the relay URL from [RelayConfig].
 *   - On open, we send a `join` frame for every group we currently have so the
 *     relay routes future messages for those groups to us. We also rejoin whenever
 *     we learn about a new group (via [joinRoom], called from the sync engine on
 *     outbound broadcasts).
 *   - [broadcast] sends a `sync` frame to the relay, which forwards it to every
 *     OTHER client in the room. We ignore messages we sent ourselves (the relay
 *     excludes the sender, but we double-check by senderDeviceId for safety).
 *   - [stop] closes the socket.
 *
 * The relay never stores anything; if no peer is online, broadcast messages are
 * simply dropped. Convergence happens when both devices are online at overlapping
 * times — the CRDT handles the rest (idempotent merge means re-sending is free).
 */
@Singleton
class RelaySyncTransport @Inject constructor(
    private val config: RelayConfig,
    private val json: Json,
) : SyncTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().build()
    private val TAG = "RelayTransport"

    private val _status = MutableStateFlow(TransportStatus.IDLE)
    private val _connected = MutableStateFlow(false)
    private val _incoming = MutableSharedFlow<SyncMessage>(extraBufferCapacity = 64)
    private val _peerJoined = MutableSharedFlow<String>(extraBufferCapacity = 16)

    override val isConnected: StateFlow<Boolean> = _connected.asStateFlow()
    override val status: StateFlow<TransportStatus> = _status.asStateFlow()
    override val incoming: Flow<SyncMessage> = _incoming.asSharedFlow()
    override val peerJoined: Flow<String> = _peerJoined.asSharedFlow()

    private var socket: WebSocket? = null
    private val joinedRooms = mutableSetOf<String>()
    private var selfDeviceId: String = ""

    override fun startAdvertising(deviceId: String) {
        // No-op for the relay model: "advertising" = connect to the relay.
        // We kick off the connection in startDiscovering, which the engine calls
        // right after. Keep the method for interface parity.
    }

    override fun startDiscovering(deviceId: String) = start(deviceId)

    /** Open the WebSocket to the relay and (re)join all known rooms. */
    fun start(deviceId: String) {
        if (socket != null) return
        selfDeviceId = deviceId
        val url = config.relayUrl
        if (url.isBlank()) {
            _status.value = TransportStatus.IDLE
            return
        }
        _status.value = TransportStatus.SEARCHING
        val req = Request.Builder().url(url).build()
        socket = client.newWebSocket(req, RelayListener())
    }

    /** Join a room so the relay routes messages for this group to us. Called on broadcast. */
    fun joinRoom(roomId: String) {
        if (joinedRooms.add(roomId)) {
            sendControl(buildJsonObject { put("type", JsonPrimitive("join")); put("room", JsonPrimitive(roomId)) })
        }
    }

    override suspend fun broadcast(message: SyncMessage) {
        val s = socket ?: return
        // For a SyncRequest, the "room" is implicit — we want every group we know
        // about. For a SyncPayload, the rooms are the group ids in the payload.
        val rooms = when (message) {
            is SyncMessage.SyncRequest -> message.hashes.keys
            is SyncMessage.SyncPayload -> message.groups.map { it.id }
        }
        val payload = json.encodeToString(SyncMessage.serializer(), message)
        for (roomId in rooms) {
            joinRoom(roomId)
            val frame = buildJsonObject {
                put("type", JsonPrimitive("sync"))
                put("room", JsonPrimitive(roomId))
                put("data", json.parseToJsonElement(payload))
            }
            s.send(frame.toString())
        }
    }

    override fun stop() {
        socket?.close(1000, "bye")
        socket = null
        joinedRooms.clear()
        _connected.value = false
        _status.value = TransportStatus.IDLE
    }

    private fun sendControl(obj: JsonObject) {
        socket?.send(obj.toString())
    }

    private inner class RelayListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "relay connected")
            _connected.value = true
            _status.value = TransportStatus.CONNECTED
            // Rejoin any rooms we knew about (e.g. after reconnect).
            for (room in joinedRooms) joinRoom(room)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val obj = json.parseToJsonElement(text).jsonObject
                val type = obj["type"]?.jsonPrimitive?.content
                when (type) {
                    "sync" -> {
                        val data = obj["data"] ?: return
                        val msg = json.decodeFromJsonElement(SyncMessage.serializer(), data)
                        if (msg.senderDeviceId() == selfDeviceId) return
                        scope.launchSafe { _incoming.emit(msg) }
                    }
                    "peer" -> {
                        // A new peer joined one of our rooms — nudge the engine to re-sync.
                        val room = obj["room"]?.jsonPrimitive?.content ?: return
                        scope.launchSafe { _peerJoined.emit(room) }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "failed to decode relay frame", t)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "relay closed")
            _connected.value = false
            _status.value = TransportStatus.IDLE
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "relay failure", t)
            _connected.value = false
            _status.value = TransportStatus.ERROR
        }
    }
}

private fun CoroutineScope.launchSafe(block: suspend () -> Unit) {
    launch { runCatching { block() } }
}