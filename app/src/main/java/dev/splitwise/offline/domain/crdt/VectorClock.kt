package dev.splitwise.offline.domain.crdt

import kotlinx.serialization.Serializable

/**
 * A simple vector clock: maps a device/replica id to a monotonically-increasing counter.
 *
 * Vector clocks let us reason about causality without a central server:
 *  - [isAfter]/[happenedBefore] tell us whether one state causally dominates another.
 *  - [merge] produces a clock that reflects "we've seen both sides".
 *  - When two updates are *concurrent* (neither happened-before the other), we have a
 *    real conflict and resolve it with a deterministic tie-breaker in the CRDT merge.
 *
 * This is the foundation of every CRDT in the app.
 */
@Serializable
data class VectorClock(val counters: Map<String, Long> = emptyMap()) {

    /** Increment this replica's component, returning a new clock. */
    fun increment(deviceId: String): VectorClock =
        copy(counters = counters + (deviceId to (counters[deviceId] ?: 0L) + 1L))

    /** Pointwise-max merge — the "we've now seen both histories" clock. */
    fun merge(other: VectorClock): VectorClock {
        val keys = counters.keys + other.counters.keys
        return VectorClock(buildMap(keys.size) {
            keys.forEach { k -> put(k, maxOf(counters[k] ?: 0L, other.counters[k] ?: 0L)) }
        })
    }

    /** True if `this` happened strictly after `other` (every component >= and at least one >). */
    fun isAfter(other: VectorClock): Boolean {
        val keys = counters.keys + other.counters.keys
        var strictlyGreater = false
        for (k in keys) {
            val a = counters[k] ?: 0L
            val b = other.counters[k] ?: 0L
            if (a < b) return false
            if (a > b) strictlyGreater = true
        }
        return strictlyGreater
    }

    /** `this` happened-before `other` iff `other` isAfter `this`. */
    fun happenedBefore(other: VectorClock): Boolean = other.isAfter(this)

    /** Neither happened-before the other → concurrent edits → a real conflict to resolve. */
    fun isConcurrentWith(other: VectorClock): Boolean =
        !isAfter(other) && !other.isAfter(this) && this != other

    operator fun get(deviceId: String): Long = counters[deviceId] ?: 0L

    companion object {
        val EMPTY = VectorClock()
    }
}