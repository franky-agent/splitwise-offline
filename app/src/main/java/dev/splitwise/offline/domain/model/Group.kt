package dev.splitwise.offline.domain.model

import dev.splitwise.offline.domain.crdt.LWWField
import dev.splitwise.offline.domain.crdt.ORSet
import dev.splitwise.offline.domain.crdt.VectorClock
import kotlinx.serialization.Serializable

/**
 * A group is the CRDT root: a collection of members + expenses, plus a vector clock
 * tracking every edit across every device that has touched it.
 *
 * Merge semantics (see [merge]):
 *  - members: union with per-member LWW merge (so renames converge).
 *  - expenses: union by id with per-expense LWW merge.
 *  - group metadata (name): LWW.
 *  - clock: pointwise-max.
 *
 * Because every piece is a CRDT, merging two groups is total, commutative and
 * idempotent — the definition of "no server needed".
 */
@Serializable
data class Group(
    val id: String,
    val name: LWWField<String>,
    val currency: LWWField<String>,
    val members: Map<String, Member>,            // memberId -> Member
    val expenses: Map<String, Expense>,          // expenseId -> Expense
    val clock: VectorClock,
    val memberTags: ORSet<String>,               // live member ids (OR-set for add/remove)
    val updatedAt: Long,
) {
    val displayName get() = name.value
    val activeExpenses get() = expenses.values.filter { !it.isDeleted }
    val memberIds get() = memberTags.values().sorted()

    fun merge(other: Group): Group {
        require(id == other.id) { "cannot merge different groups" }
        val mergedMembers = (members.keys + other.members.keys).associateWith { id ->
            val a = members[id]; val b = other.members[id]
            when { a != null && b != null -> a.merge(b); a != null -> a; else -> b!! }
        }
        val mergedExpenses = (expenses.keys + other.expenses.keys).associateWith { id ->
            val a = expenses[id]; val b = other.expenses[id]
            when { a != null && b != null -> a.merge(b); a != null -> a; else -> b!! }
        }
        return copy(
            name = name.merge(other.name),
            currency = currency.merge(other.currency),
            members = mergedMembers,
            expenses = mergedExpenses,
            clock = clock.merge(other.clock),
            memberTags = memberTags.merge(other.memberTags),
            updatedAt = maxOf(updatedAt, other.updatedAt),
        )
    }

    /** Content hash for cheap "do we agree?" checks before exchanging payload. */
    fun contentHash(): String {
        val parts = mutableListOf(id, displayName, clock.counters.entries.joinToString { "${it.key}=${it.value}" })
        parts += memberIds
        parts += activeExpenses.map { it.id }
        return dev.splitwise.offline.domain.crdt.Hashing.sha256(parts.joinToString("|"))
    }

    companion object {
        fun new(
            id: String,
            name: String,
            currency: String,
            deviceId: String,
            ts: Long,
        ): Group {
            val clock = VectorClock.EMPTY.increment(deviceId)
            return Group(
                id = id,
                name = LWWField(name, clock, ts, deviceId),
                currency = LWWField(currency, clock, ts, deviceId),
                members = emptyMap(),
                expenses = emptyMap(),
                clock = clock,
                memberTags = ORSet(),
                updatedAt = ts,
            )
        }
    }
}