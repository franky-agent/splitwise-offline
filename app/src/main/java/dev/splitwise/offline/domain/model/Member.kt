package dev.splitwise.offline.domain.model

import dev.splitwise.offline.domain.crdt.LWWField
import dev.splitwise.offline.domain.crdt.VectorClock
import kotlinx.serialization.Serializable

/**
 * A participant in a group. Identified by a stable device pubkey/id;
 * [name] is just a human label (LWW so two devices renaming offline converge).
 */
@Serializable
data class Member(
    val id: String,             // stable identity (device pubkey or user-chosen id)
    val name: LWWField<String>, // display name, CRDT-merged
) {
    val displayName get() = name.value

    fun merge(other: Member): Member {
        require(id == other.id) { "cannot merge different members" }
        return copy(name = name.merge(other.name))
    }

    companion object {
        fun new(id: String, name: String, deviceId: String, clock: VectorClock, ts: Long) =
            Member(id, LWWField(name, clock, ts, deviceId))
    }
}