package dev.splitwise.offline.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupCrdtTest {

    private val A = "device-A"
    private val B = "device-B"

    @Test fun `two offline edits converge after merge`() {
        // A creates a group and adds an expense; B independently adds a member.
        // Neither has seen the other. After exchanging + merging, both have everything.
        val base = Group.new("g1", "Trip", "USD", A, ts = 1000)

        // A's offline branch: add an expense (mutateGroup bumps clock to A:2).
        val aBranch = base.copy(
            clock = base.clock.increment(A),
            updatedAt = 2000,
            expenses = mapOf(
                "e1" to Expense.new("e1", "Dinner", 6000, "USD", "m1",
                    mapOf("m1" to 3000L, "m2" to 3000L), A, base.clock.increment(A), 2000)
            )
        )

        // B's offline branch: add a member (B's clock B:1, never saw A:1).
        val bBranch = base.copy(
            clock = base.clock.increment(B),
            updatedAt = 1500,
            members = mapOf("m2" to Member.new("m2", "Bob", B, base.clock.increment(B), 1500)),
            memberTags = base.memberTags.add("m2", "B:1"),
        )

        // They meet and sync.
        val mergedOnA = aBranch.merge(bBranch)
        val mergedOnB = bBranch.merge(aBranch)

        // Commutativity: both devices end up in the exact same state.
        assertEquals(mergedOnA, mergedOnB)
        // Convergence: both have the expense AND the member.
        assertTrue(mergedOnA.expenses.containsKey("e1"))
        assertTrue(mergedOnA.members.containsKey("m2"))
        assertEquals(2L, mergedOnA.clock[A])
        assertEquals(1L, mergedOnA.clock[B])
    }

    @Test fun `idempotent merge does not duplicate`() {
        val g = Group.new("g1", "Trip", "USD", A, 1000)
        val merged = g.merge(g)
        assertEquals(g, merged)
    }

    @Test fun `settlement suggestions zero out balances`() {
        val base = Group.new("g1", "Trip", "USD", A, 1000)
        val g = base.copy(
            members = mapOf(
                "m1" to Member.new("m1", "Alice", A, base.clock, 1000),
                "m2" to Member.new("m2", "Bob", A, base.clock, 1000),
            ),
            memberTags = base.memberTags.add("m1", "A:1").add("m2", "A:2"),
        )
        // Alice paid 10000 (100.00) split equally -> Alice is owed 50, Bob owes 50.
        val withExpense = g.copy(
            expenses = mapOf(
                "e1" to Expense.new("e1", "Dinner", 10000, "USD", "m1",
                    mapOf("m1" to 5000L, "m2" to 5000L), A, g.clock, 2000)
            )
        )
        val balances = BalanceCalculator.balances(withExpense)
        assertEquals(5000L, balances["m1"])
        assertEquals(-5000L, balances["m2"])

        val settlements = BalanceCalculator.suggestSettlements(withExpense)
        assertEquals(1, settlements.size)
        assertEquals("m2", settlements[0].from)
        assertEquals("m1", settlements[0].to)
        assertEquals(5000L, settlements[0].amount)
    }
}