# Architecture — Splitwise Offline

> How do you build a Splitwise-like app with **no server infrastructure**?
> This document is the long-form answer; the code is the proof.

---

## The problem

Splitwise is fundamentally **shared mutable state**: a group has members and expenses,
and several people edit the same group from different devices. A normal client/server
architecture solves this with a central database that serializes writes.

Without a server you lose the single source of truth. Two phones both offline, both
editing the same expense, then come back into contact — who wins? A naive
"last write wins by timestamp" loses data. A naive "merge the lists" double-counts.

You need a way for **independently-edited replicas to converge without coordination**.
That is exactly what **CRDTs (Conflict-free Replicated Data Types)** are.

---

## The three pillars

### 1. CRDT data layer (the brain)

Every piece of shared state is a CRDT. The app implements three primitives:

| Primitive | File | Used for |
|---|---|---|
| **Vector clock** | `domain/crdt/VectorClock.kt` | Tracking causality across replicas without a server. `isAfter`, `happenedBefore`, `isConcurrentWith`, `merge`. |
| **LWW field** | `domain/crdt/LWWField.kt` | Last-writer-wins for a single value (an expense's amount, a member's name). Each field carries its own vector clock; conflicts resolve by clock, then by deterministic (timestamp, author) tie-break. |
| **OR-Set** | `domain/crdt/ORSet.kt` | Add/remove sets (group membership). Concurrent add+remove converge: the add wins because the remover never saw that tag. |

On top of these, the domain models (`Group`, `Expense`, `Member`) are themselves CRDTs
with a `merge(other)` operation that is:

- **Total** — always produces a result, never errors.
- **Commutative** — `a.merge(b) == b.merge(a)`.
- **Idempotent** — `a.merge(a) == a`.

These three properties are what let devices sync in any order, any number of times,
and always end up in the same state. **This is the entire reason no server is needed.**

```
          Device A                    Device B
   [Group v=12 edits]            [Group v=20 edits]
            \                         /
             \   P2P sync (any)      /
              \                     /
               v                   v
        A.merge(B) -----> same state <----- B.merge(A)
```

### 2. Local-first persistence (the memory)

`data/` is a Room (SQLite) database that stores each group as a serialized JSON blob
(`GroupEntity.payloadJson`). This makes the on-device replica the single source of
truth: every read comes from Room, every write goes to Room, and the CRDT metadata
travels with the payload so it's always exactly reproducible.

Why blob storage instead of normalized tables? Because the sync unit is the *whole
group CRDT* — exchanging and merging groups is trivial when one row = one CRDT.
Normalized tables would require per-table merge logic and a much heavier sync protocol.

### 3. Pluggable P2P transport (the nervous system)

`sync/SyncTransport.kt` is a small interface:

```kotlin
interface SyncTransport {
    val isConnected: Flow<Boolean>
    val status: Flow<TransportStatus>
    val incoming: Flow<SyncMessage>
    fun startAdvertising(deviceId: String)
    fun startDiscovering(deviceId: String)
    suspend fun broadcast(message: SyncMessage)
    fun stop()
}
```

The default implementation, `NearbyConnectionsTransport`, uses **Google Nearby
Connections** — a serverless P2P mesh over Wi-Fi Direct and BLE. Devices in proximity
discover each other, form a cluster, and exchange length-prefixed JSON envelopes of
`SyncMessage`s. No cloud relay touches the payload.

Because the app depends only on the interface, you can add transports later without
touching the data layer or UI:

- **Google Drive / Dropbox file transport** — serialize the CRDT to a file in a shared
  folder; each device polls for changes. Gives async, non-co-located sync with still
  no server of your own.
- **WebRTC relay** — for sync over the internet without proximity.
- **QR code / NFC** — for one-shot "share this group" without any network.

Each is a new class implementing `SyncTransport` + a one-line `@Binds` change in
`di/SyncModule.kt`.

---

## The sync protocol

Intentionally simple — full-state CRDT exchange (a production version would delta-pack):

```
A connects to B
A -> B:  SyncRequest { hashes: {groupId -> contentHash, ...} }   // "here's what I have"
B:       compares A's hashes to its own
B -> A:  SyncPayload { groups: [groups that differ or A lacks] } // "pull these"
A:       mergeIncoming(each)  // CRDT merge -> converge
A:       if state changed -> A -> B: SyncRequest(...)            // "now you pull from me"
... repeats until both sides report identical hashes -> silent.
```

Because `merge` is idempotent and commutative, re-broadcasting is safe and
self-terminating: once both replicas agree on every hash, no further payloads are
produced. No server, no consensus, no coordinator.

---

## Layering

```
UI (Compose) ──observes──> ViewModels ──calls──> Repository ──reads/writes──> Room
                                  │                     │
                                  │                     └──mergeIncoming──> SyncEngine ──> SyncTransport
                                  └──mutateGroup (bump vector clock, edit, persist)
```

- **Domain layer** (`domain/`) is pure Kotlin — no Android imports. Unit-testable in plain JVM.
- **Data layer** (`data/`) owns Room and serialization.
- **Sync layer** (`sync/`) owns the transport interface + Nearby Connections impl + the engine.
- **UI layer** (`ui/`) is Compose + ViewModels, depends only on the repository and sync engine.

---

## What this POC deliberately does NOT do (yet)

- **Delta sync** — we ship the whole group CRDT. For groups with thousands of expenses
  this is wasteful; a Merkle-tree diff (the `Hashing.kt` helper is a stub for this)
  would let you ship only the changed subtree.
- **Tombstone GC** — deleted expenses and removed members are tombstoned, never pruned.
  A production system needs a GC protocol once all replicas have acked a tombstone.
- **Auth on the P2P link** — Nearby Connections payloads are not authenticated here.
  Pair devices via a shared secret or key exchange before merging.
- **Multi-currency** — the model has a currency field per expense but no conversion.
- **Push sync when not co-located** — add the Drive/Dropbox transport.

Each of these is an additive change to one layer, thanks to the separation above.