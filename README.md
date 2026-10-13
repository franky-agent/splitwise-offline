# Splitwise Offline — Serverless P2P Expense Splitting

A **proof-of-concept** Android app that works like Splitwise **without any server infrastructure**.
No backend, no cloud account, no Firebase. Devices sync directly, peer-to-peer.

> 🧪 **POC status**: demonstrates the architecture (CRDT data layer + pluggable P2P sync
> transport + Compose UI). Functional enough to run and explore; not production hardened.

---

## TL;DR — How it works without a server

| Concern | Solution |
|---|---|
| Shared mutable state across devices | **CRDTs** (Conflict-free Replicated Data Types) — every device keeps a full replica, edits offline, merges are deterministic and *always* converge. No coordination server required. |
| Local storage | **Room (SQLite)** — fast offline-first, single source of truth on device. |
| Sync transport (device-to-device) | **Android Nearby Connections API** — true serverless P2P over Wi-Fi Direct / BLE mesh when devices are in proximity. Pluggable `SyncTransport` interface → swap in a Google-Drive-file or WebRTC transport later without touching the data layer. |
| Identity | Locally generated keypair (stored in Android Keystore). Device pubkey = stable device ID; human names are labels. No accounts. |
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

---

## Project structure

```
app/
├── build.gradle.kts                # module config: Compose, Room, Hilt, Nearby
└── src/main/
    ├── AndroidManifest.xml
    ├── java/dev/splitwise/offline/
    │   ├── SplitwiseApp.kt                 # Application + Hilt
    │   ├── MainActivity.kt                 # single activity, hosts Compose nav
    │   ├── di/                             # Hilt modules
    │   ├── domain/                         # pure-Kotlin models + CRDT merge
    │   │   ├── model/                      #   Group, Expense, Member, Settlement
    │   │   └── crdt/                       #   VectorClock, Merkle, merge logic
    │   ├── data/                           # Room: entities, DAOs, Database
    │   │   ├── entity/  dao/  db/  repo/
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

Two devices on the same Wi-Fi → open the app on both → tap **"Sync nearby"** →
expenses and balances converge with no server in the loop.

---

## Roadmap (what this POC does not yet do)

- [ ] Google-Drive / Dropbox file transport (for async, non-co-located sync)
- [ ] Photo receipts (store as content-addressed blobs in a sync'd folder)
- [ ] Multi-currency conversion table
- [ ] Settlement suggestions ("who pays whom to settle up")
- [ ] Hardening: auth on the Nearby payload, tombstone GC, storage pruning

---

## License

MIT — see [LICENSE](LICENSE).