/**
 * splitwise-offline relay — v2
 *
 * A minimal, storage-free message relay that lets devices sync when they are NOT
 * co-located. It is a dumb pipe ONLY: it forwards bytes between WebSocket clients
 * and forgets everything on disconnect. It never stores group data, expenses, or
 * any app state — that lives exclusively on devices as CRDTs.
 *
 * Why this exists:
 *   Nearby Connections only works when devices are in proximity. For async,
 *   non-co-located sync we need *something* that can route messages between
 *   devices on different networks. This relay is the smallest possible such
 *   thing: ~60 lines, no database, no auth state, no persistence. You can run
 *   it on any free-tier Node host (Render, Fly.io, Railway, a $5 VPS).
 *
 * What it does NOT do:
 *   - No storage. Messages are forwarded in-memory and dropped if no peer is online.
 *   - No authentication of users. (Clients authenticate each other via the CRDT
 *     payload's authorDeviceId; the relay is untrusted by design.)
 *   - No business logic. It cannot create/edit groups. It only routes bytes.
 *
 * Routing model:
 *   Each WebSocket connection registers one or more "rooms" (groupIds). The relay
 *   broadcasts any incoming message to every OTHER connection in the same room.
 *   This matches the CRDT sync model: every device in a group should see every
 *   other device's sync messages for that group.
 *
 * Wire format:
 *   Text frames containing JSON. Two control frames are understood by the relay:
 *     {"type":"join","room":"<groupId>"}            — subscribe to a room
 *     {"type":"sync","room":"<groupId>","data":<SyncMessage>}
 *                                                   — broadcast to the room (excl. sender)
 *   Anything else is ignored. The `data` field is an opaque JSON blob (a
 *   SyncMessage serialized by the client); the relay never inspects or modifies it.
 *
 * Run:
 *   node relay/server.js            # defaults to port 8787
 *   PORT=8080 node relay/server.js
 *
 * Health check:
 *   GET /health -> 200 "ok"
 *
 * Deploy:
 *   - Render/Fly.io/Railway: push this folder, set PORT, done.
 *   - VPS: `node relay/server.js` behind nginx + TLS (letsencrypt).
 *   - For a truly serverless option, port this to a Cloudflare Worker (WebSocket
 *     Durable Objects) — same routing logic, no VM to manage.
 */

const http = require('http');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 8787;

// room -> Set<WebSocket>. Held only in process memory. Lost on restart, by design.
const rooms = new Map();
function roomOf(name) {
  let r = rooms.get(name);
  if (!r) { r = new Set(); rooms.set(name, r); }
  return r;
}

const server = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'content-type': 'text/plain' });
    res.end('ok');
    return;
  }
  res.writeHead(404, { 'content-type': 'text/plain' });
  res.end('not found');
});

const wss = new WebSocketServer({ server });

wss.on('connection', (ws) => {
  // Track which rooms this socket has joined so we can clean up on disconnect.
  ws.joinedRooms = new Set();

  ws.on('message', (raw) => {
    let msg;
    try { msg = JSON.parse(raw.toString()); } catch { return; } // ignore malformed

    if (msg.type === 'join' && typeof msg.room === 'string') {
      const wasEmpty = !rooms.has(msg.room) || rooms.get(msg.room).size === 0;
      ws.joinedRooms.add(msg.room);
      roomOf(msg.room).add(ws);
      // Notify existing peers that someone joined, so they can re-send a sync request.
      // The relay does not store or inspect any app data — this is a presence signal only.
      if (!wasEmpty) {
        const presence = JSON.stringify({ type: 'peer', room: msg.room });
        for (const peer of rooms.get(msg.room)) {
          if (peer !== ws && peer.readyState === 1) peer.send(presence);
        }
      }
      return;
    }

    if (msg.type === 'sync' && typeof msg.room === 'string') {
      const room = rooms.get(msg.room);
      if (!room) return;
      // Forward to every OTHER client in the room. The relay never stores `data`.
      const out = JSON.stringify({ type: 'sync', room: msg.room, data: msg.data });
      for (const peer of room) {
        if (peer !== ws && peer.readyState === 1 /* OPEN */) peer.send(out);
      }
      return;
    }

    // Unknown message type — ignore. The relay is intentionally permissive.
  });

  ws.on('close', () => {
    for (const room of ws.joinedRooms) {
      const set = rooms.get(room);
      if (set) { set.delete(ws); if (set.size === 0) rooms.delete(room); }
    }
  });
});

server.listen(PORT, () => {
  console.log(`splitwise-offline relay listening on :${PORT} (no storage, room-routed)`);
});