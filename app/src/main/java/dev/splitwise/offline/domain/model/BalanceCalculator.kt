package dev.splitwise.offline.domain.model

/**
 * Pure-Kotlin balance + settlement math.
 *
 * For each member we compute a net balance:
 *   net = (what they paid across all expenses) - (what they owe across all splits)
 * net > 0 → they are owed money. net < 0 → they owe money.
 *
 * [suggestSettlements] produces a minimal list of "A pays B $X" transactions that
 * brings every balance to zero (greedy largest-debtor → largest-creditor).
 *
 * All amounts are minor units (cents) to avoid floating-point drift.
 */
object BalanceCalculator {

    /** memberId -> net balance in minor units (positive = owed). */
    fun balances(group: Group): Map<String, Long> {
        val net = group.memberIds.associateWith { 0L }.toMutableMap()
        for (e in group.activeExpenses) {
            val payer = e.paidBy.value
            net[payer] = (net[payer] ?: 0L) + e.amount.value
            for ((memberId, share) in e.splits.value) {
                net[memberId] = (net[memberId] ?: 0L) - share
            }
        }
        return net
    }

    data class Settlement(val from: String, val to: String, val amount: Long) {
        init { require(amount > 0) { "settlement amount must be positive" } }
    }

    fun suggestSettlements(group: Group): List<Settlement> {
        val net = balances(group).toMutableMap()
        // Round away dust (< 1 cent) that arises from integer division of equal splits.
        val dust = net.values.sum()
        if (dust != 0L && net.isNotEmpty()) {
            // The dust should be the payer's residue; distribute to the largest creditor
            // for determinism. For the POC we just zero it onto an arbitrary stable member.
            val fixTarget = net.entries.first().key
            net[fixTarget] = net[fixTarget]!! - dust
        }

        val creditors = net.filter { it.value > 0 }.map { it.key to it.value }.sortedByDescending { it.second }.toMutableList()
        val debtors = net.filter { it.value < 0 }.map { it.key to -it.value }.sortedByDescending { it.second }.toMutableList()

        val out = mutableListOf<Settlement>()
        var ci = 0; var di = 0
        while (ci < creditors.size && di < debtors.size) {
            val (creditor, cAmt) = creditors[ci]
            val (debtor, dAmt) = debtors[di]
            val pay = minOf(cAmt, dAmt)
            if (pay > 0) out += Settlement(from = debtor, to = creditor, amount = pay)
            creditors[ci] = creditor to (cAmt - pay)
            debtors[di] = debtor to (dAmt - pay)
            if (creditors[ci].second == 0L) ci++
            if (debtors[di].second == 0L) di++
        }
        return out
    }

    /** Total amount of money flowing through the group (sum of active expense amounts). */
    fun totalSpent(group: Group): Long = group.activeExpenses.sumOf { it.amount.value }
}