package dev.splitwise.offline.domain.model

import dev.splitwise.offline.domain.crdt.LWWField
import dev.splitwise.offline.domain.crdt.VectorClock
import kotlinx.serialization.Serializable

/**
 * An expense: "X paid `amount`, split among `splits` (each split = member -> share)".
 *
 * Every mutable field is an [LWWField] so two offline edits to the same expense
 * converge deterministically. The expense [id] is a UUID generated at creation time.
 *
 * Split math invariant: sum(splits.values) == amount. Enforced at creation/merge
 * time in the domain layer, not in the UI.
 */
@Serializable
data class Expense(
    val id: String,
    val description: LWWField<String>,
    val amount: LWWField<Long>,          // stored as minor units (cents) to avoid float drift
    val currency: LWWField<String>,      // ISO 4217, e.g. "USD"
    val paidBy: LWWField<String>,        // member id of payer
    val splits: LWWField<Map<String, Long>>, // memberId -> share in minor units
    val createdAt: Long,
    val deleted: LWWField<Boolean>,      // tombstone (CRDT delete = set deleted=true)
) {
    val isDeleted get() = deleted.value

    fun merge(other: Expense): Expense {
        require(id == other.id) { "cannot merge different expenses" }
        return copy(
            description = description.merge(other.description),
            amount = amount.merge(other.amount),
            currency = currency.merge(other.currency),
            paidBy = paidBy.merge(other.paidBy),
            splits = splits.merge(other.splits),
            deleted = deleted.merge(other.deleted),
        )
    }

    companion object {
        /** Split `amount` equally among [memberIds]. Returns the splits map. */
        fun equalSplits(amount: Long, memberIds: List<String>): Map<String, Long> {
            require(memberIds.isNotEmpty()) { "need at least one member" }
            require(amount >= 0) { "amount must be >= 0" }
            val n = memberIds.size.toLong()
            val base = amount / n
            val remainder = amount % n
            return memberIds.sorted().mapIndexed { i, id ->
                id to (base + if (i < remainder) 1L else 0L)
            }.toMap()
        }

        fun new(
            id: String,
            description: String,
            amount: Long,
            currency: String,
            paidBy: String,
            splits: Map<String, Long>,
            deviceId: String,
            clock: VectorClock,
            ts: Long,
        ) = Expense(
            id = id,
            description = LWWField(description, clock, ts, deviceId),
            amount = LWWField(amount, clock, ts, deviceId),
            currency = LWWField(currency, clock, ts, deviceId),
            paidBy = LWWField(paidBy, clock, ts, deviceId),
            splits = LWWField(splits, clock, ts, deviceId),
            createdAt = ts,
            deleted = LWWField(false, clock, ts, deviceId),
        )
    }
}