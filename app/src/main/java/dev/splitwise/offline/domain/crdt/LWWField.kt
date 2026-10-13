package dev.splitwise.offline.domain.crdt

import kotlinx.serialization.Serializable

/**
 * A Last-Writer-Wins map field — the building block for CRDT records.
 *
 * Each field carries its own vector clock + wall-clock timestamp + author id.
 * Merge rule:
 *   1. If one side's vector clock is causally after the other → it wins (deterministic).
 *   2. If concurrent → larger (timestamp, author) tie-break breaks the tie deterministically,
 *      so every replica picks the same winner without coordination.
 *
 * `T` is unbounded so we can wrap non-comparable values like `Map<String, Long>` (expense
 * splits). The tie-break relies only on timestamp + author, which is always deterministic.
 * Use a [kotlinx.serialization.Serializable] type for `T` whenever this travels over the wire.
 */
@Serializable
data class LWWField<T>(
    val value: T,
    val clock: VectorClock,
    val timestamp: Long,        // wall-clock ms at the editing device (tie-break only)
    val authorDeviceId: String, // stable device id (tie-break only)
) {
    fun merge(other: LWWField<T>): LWWField<T> = when {
        clock.isAfter(other.clock) -> this
        other.clock.isAfter(this.clock) -> other
        // Causally equal → identical; pick either (they're equal by construction).
        clock == other.clock -> this
        // Concurrent: deterministic tie-break on (timestamp, author).
        else -> {
            val cmp = timestamp.compareTo(other.timestamp)
            when {
                cmp != 0 -> if (cmp > 0) this else other
                else -> if (authorDeviceId >= other.authorDeviceId) this else other
            }
        }
    }
}