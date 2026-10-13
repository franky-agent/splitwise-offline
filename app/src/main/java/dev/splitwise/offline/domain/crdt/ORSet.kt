package dev.splitwise.offline.domain.crdt

import kotlinx.serialization.Serializable

/**
 * An OR-Set (Observed-Remove Set) — a CRDT set supporting add/remove.
 *
 * Add tags the element with a unique tag (here: "deviceId:counter"); remove only
 * removes *observed* tags. Concurrent add+remove converge: the add wins because the
 * remover never saw that tag. This is what lets two offline devices add and remove
 * members/expenses without a server and still converge.
 *
 * For simplicity in this POC we tombstone removed tags rather than GC them.
 */
@Serializable
class ORSet<T>(
    // element -> set of live add-tags currently pointing at it
    val elements: Map<T, Set<String>> = emptyMap(),
    // tombstoned add-tags (removed). In production, GC these after all replicas ack.
    private val tombstones: Set<String> = emptySet(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ORSet<*>) return false
        return elements == other.elements && tombstones == other.tombstones
    }

    override fun hashCode(): Int = 31 * elements.hashCode() + tombstones.hashCode()
    fun add(element: T, tag: String): ORSet<T> {
        if (tag in tombstones) return this // already removed — keep tombstoned
        val tags = elements[element].orEmpty() + tag
        return copy(elements = elements + (element to tags))
    }

    fun remove(element: T): ORSet<T> {
        val tags = elements[element] ?: return this
        return copy(
            elements = elements - element,
            tombstones = tombstones + tags,
        )
    }

    fun contains(element: T): Boolean = elements.containsKey(element)

    fun values(): Set<T> = elements.keys

    fun merge(other: ORSet<T>): ORSet<T> {
        val mergedTomb = tombstones + other.tombstones
        val keys = elements.keys + other.elements.keys
        val merged = buildMap(keys.size) {
            for (k in keys) {
                val a = (elements[k] ?: emptySet()) - mergedTomb
                val b = (other.elements[k] ?: emptySet()) - mergedTomb
                val live = a + b
                if (live.isNotEmpty()) put(k, live)
            }
        }
        return ORSet(merged, mergedTomb)
    }

    private fun copy(elements: Map<T, Set<String>> = this.elements, tombstones: Set<String> = this.tombstones) =
        ORSet(elements, tombstones)
}