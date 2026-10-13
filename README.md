# Splitwise Offline — Serverless P2P Expense Splitting

A **proof-of-concept** Android app that works like Splitwise **with minimal-to-no server infrastructure**.
No backend, no cloud account, no Firebase. Devices sync directly, peer-to-peer.

**v1** syncs over Nearby Connections (Wi-Fi Direct/BLE) when devices are in proximity —
zero server. **v2** adds a tiny, storage-free WebSocket relay so devices can sync even
when they're NOT near each other — the relay only forwards bytes, never stores data.

> 🧪 **POC status**: demonstrates the architecture (CRDT data layer + pluggable P2P sync
> transport + Compose UI). Functional enough to run and explore; not production hardened.

---

## TL;DR — How it works without a server

| Concern | Solution |
|---|---|
| Shared mutable state across devices | **CRDTs** (Conflict-free Replicated Data Types) — every device keeps a full replica, edits offline, merges are deterministic and *always* converge. No coordination server required. |
| Local storage | **Room (SQLite)** — fast offline-first, single source of truth on device. |
| Sync transport (proximity) | **Android Nearby Connections API** — true serverless P2P over Wi-Fi Direct / BLE mesh when devices are nearby. |
| Sync transport (remote, v2) | **Minimal WebSocket relay** (`relay/server.js`, ~60 lines) — routes messages between devices on different networks. **Storage-free**: forwards bytes only, forgets on disconnect, never persists app data. Run it on any free-tier host or skip it entirely for proximity-only sync. |
| Identity | Locally generated device id (Android Keystore). Human names are labels. No accounts. |
| Conflict resolution | Vector-clock per group + last-writer-wins per field with content hash. CRDT semantics guarantee eventual convergence. |
| Architecture | Single-activity + Jetpack Compose + Material 3, Hilt DI, MVVM with ViewModels + StateFlow. |

---

## Architecture overview

```
┌──────────────────────────────────────────────────────────┐
│                        UI Layer                          │
│      Jetpack Compose (Material 3) + Navigation Compose    │
│            Composables observe StateFlow from VMs          │
└───────────────────────────┬──────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────┐
│                    Presentation Layer                    │
│            ViewModels (Hilt) + StateFlow + UI state        │
└───────────────────────────┬──────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────┐
│                      Domain Layer                        │
│   Pure Kotlin: Group, Expense, Member, Settlement,        │
│   CRDT merge logic, settlement-up math (no Android deps)  │
└───────────────┬───────────────────────┬──────────────────┘
                │                       │
┌───────────────▼──────────────┐ ┌──────▼───────────────────┐
│        Data Layer (Room)     │ │   Sync Layer (P2P)       │
│  DAOs + Database + Entities  │ │  SyncTransport interface  │
│   = local source of truth    │ │  NearbyConnections impl   │
└──────────────────────────────┘ └──────────────────────────┘
```

### Why CRDTs?

A normal SQL app with last-sync timestamps breaks under offline multi-device edits:
two people edit the same expense while offline → on sync you must either (a) pick a
"winner" and lose data, or (b) run a custom merge that's hard to get right.

A **CRDT** makes every edit commutative: apply edits in any order, on any device, and
all replicas converge to the same state. The cost is a bit of extra metadata
(vector clocks, tombstones) — handled in the domain layer. This is the same family
of techniques used by Notion, Linear, and Apple Notes offline sync.

### Why pluggable transport?

Nearby Connections is great when devices are in the same room, but you might also want
sync over a shared cloud file (Google Drive / Dropbox) or a WebRTC relay. The app only
depends on a `SyncTransport` interface, so any of these can be added without touching
the data model or UI.

**v2 ships two transports and composes them automatically**:

- **Nearby Connections** — proximity P2P, zero infrastructure.
- **Relay WebSocket** — remote sync via `relay/server.js`, a ~60-line storage-free
  message router. The relay never stores app data; it only forwards bytes between
  devices in the same "room" (group id) and forgets everything on disconnect.

`CompositeSyncTransport` merges both into one `SyncTransport`, deduplicating messages
that arrive over both paths. The `SyncEngine` and CRDT layer are unchanged — they
didn't need to know a second transport existed. That's the pluggable-transport payoff.

---

## Sync modes

| Mode | Transport | Server needed? | When |
|---|---|---|---|
| **Proximity** | Nearby Connections (Wi-Fi Direct/BLE) | None | Devices in the same room |
| **Remote (v2)** | WebSocket relay | Minimal relay (storage-free, ~60 lines) | Devices on different networks |
| **Both** | CompositeSyncTransport runs both simultaneously | Optional relay | Always — picks whichever path works |

### Running the relay (optional, for remote sync)

```bash
cd relay/
npm install          # installs ws (~1 dependency)
npm start            # listens on port 8787 (or $PORT)
# or: PORT=8080 node server.js
```

Deploy options (all free-tier friendly):
- **Fly.io / Render / Railway**: push the `relay/` folder, set `PORT`, done.
- **$5 VPS**: `node relay/server.js` behind nginx + Let's Encrypt for `wss://`.
- **Cloudflare Worker** (WebSocket Durable Objects): same routing logic, no VM.
- **Skip it**: leave the relay URL blank in Settings → proximity-only sync.

In the app: **Settings** → enter the relay URL (e.g. `wss://my-relay.fly.dev`) →
tap **Sync**. Devices on different networks converge with no stored data anywhere.

---

## Project structure

```
app/
├── build.gradle.kts                # module config: Compose, Room, Hilt, Nearby, OkHttp
└── src/main/
    ├── AndroidManifest.xml
    ├── java/dev/splitwise/offline/
    │   ├── SplitwiseApp.kt                 # Application + Hilt
    │   ├── MainActivity.kt                 # single activity, hosts Compose nav
    │   ├── di/                             # Hilt modules (AppModule, SyncModule)
    │   ├── domain/                         # pure-Kotlin models + CRDT merge
    │   │   ├── model/                      #   Group, Expense, Member, BalanceCalculator
    │   │   └── crdt/                       #   VectorClock, LWWField, ORSet, Hashing
    │   ├── data/                           # Room: entities, DAOs, Database
    │   │   ├── entity/  dao/  repo/
    │   ├── sync/                           # P2P sync (v1 + v2)
    │   │   ├── SyncTransport.kt            #   transport interface (+ peerJoined)
    │   │   ├── NearbyConnectionsTransport.kt  # v1: proximity P2P
    │   │   ├── RelaySyncTransport.kt       # v2: remote WebSocket relay client
    │   │   ├── CompositeSyncTransport.kt   #   merges both, dedupes
    │   │   ├── RelayConfig.kt              #   persisted relay URL
    │   │   ├── SyncMessage.kt              #   serde envelope
    │   │   └── SyncEngine.kt               #   orchestrates CRDT exchange
    │   └── ui/
    │       ├── nav/AppNavHost.kt
    │       ├── theme/                      # Material 3
    │       ├── groups/                     # group list + detail + balances
    │       ├── expenses/                   # add/edit expense
    │       └── settings/                   # relay URL config (v2)
    └── res/
relay/                                   # minimal storage-free WebSocket relay (v2)
├── server.js                              # ~60 lines, room-routed, no storage
└── package.json
    │   ├── sync/                           # P2P sync
    │   │   ├── SyncTransport.kt            #   transport interface
    │   │   ├── NearbyConnectionsTransport.kt
    │   │   ├── SyncMessage.kt              #   serde envelope
    │   │   └── SyncEngine.kt               #   orchestrates merge on receive
    │   └── ui/
    │       ├── nav/AppNavHost.kt
    │       ├── theme/                      # Material 3 colors/type
    │       ├── groups/                     # group list + detail + balances
    │       └── expenses/                   # add/edit expense
    └── res/                                # strings, themes, icons
```

---

## Build & run

Requirements: Android Studio (Ladybug+), JDK 17, Android SDK 34.

```bash
# open in Android Studio, or CLI:
./gradlew :app:assembleDebug
./gradlew :app:installDebug   # on a connected device/emulator
```

Two devices on the same Wi-Fi → open the app on both → tap **"Sync"** →
expenses and balances converge with no server in the loop.

For remote sync (devices on different networks): start the relay (`cd relay && npm start`),
enter its URL in **Settings**, then tap **Sync** on both devices.

---

## Roadmap (what this POC does not yet do)

- [x] ~~Remote sync without proximity~~ ✅ **v2: minimal storage-free relay**
- [ ] Google-Drive / Dropbox file transport (async sync without even a relay)
- [ ] Delta sync (Merkle-tree diff instead of full-state CRDT exchange)
- [ ] Photo receipts (store as content-addressed blobs in a sync'd folder)
- [ ] Multi-currency conversion table
- [ ] Hardening: auth on the P2P payload, tombstone GC, storage pruning, relay TLS by default

---

## License

MIT — see [LICENSE](LICENSE).