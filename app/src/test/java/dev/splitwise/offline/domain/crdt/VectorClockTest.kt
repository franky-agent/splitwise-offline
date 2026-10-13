package dev.splitwise.offline.domain.crdt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorClockTest {

    @Test fun `increment bumps own component`() {
        val a = VectorClock.EMPTY.increment("A")
        assertEquals(1L, a["A"])
        val a2 = a.increment("A")
        assertEquals(2L, a2["A"])
    }

    @Test fun `isAfter reflects causality`() {
        val a1 = VectorClock.EMPTY.increment("A")
        val a2 = a1.increment("A")
        assertTrue(a2.isAfter(a1))
        assertFalse(a1.isAfter(a2))
    }

    @Test fun `concurrent edits are detected`() {
        // A and B both start from empty, each increments independently -> concurrent.
        val a = VectorClock.EMPTY.increment("A")
        val b = VectorClock.EMPTY.increment("B")
        assertFalse(a.isAfter(b))
        assertFalse(b.isAfter(a))
        assertTrue(a.isConcurrentWith(b))
    }

    @Test fun `merge is pointwise max`() {
        val a = VectorClock(mapOf("A" to 2L, "B" to 1L))
        val b = VectorClock(mapOf("A" to 1L, "B" to 3L))
        val m = a.merge(b)
        assertEquals(2L, m["A"])
        assertEquals(3L, m["B"])
    }

    @Test fun `merge is commutative and idempotent`() {
        val a = VectorClock(mapOf("A" to 2L))
        val b = VectorClock(mapOf("B" to 1L))
        assertEquals(a.merge(b), b.merge(a))
        assertEquals(a.merge(a), a)
    }
}