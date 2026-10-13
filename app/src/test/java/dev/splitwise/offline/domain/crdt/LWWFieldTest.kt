package dev.splitwise.offline.domain.crdt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LWWFieldTest {

    private val c1 = VectorClock(mapOf("A" to 1L))
    private val c2 = VectorClock(mapOf("A" to 2L))

    @Test fun `causally later wins regardless of timestamp`() {
        val old = LWWField("old", c2, timestamp = 100, authorDeviceId = "A")
        val new = LWWField("new", c1, timestamp = 999, authorDeviceId = "A")
        // c2 isAfter c1, so old wins despite older timestamp.
        assertEquals("old", old.merge(new).value)
        assertEquals("old", new.merge(old).value)
    }

    @Test fun `concurrent edits resolve by timestamp then author`() {
        // Two concurrent edits (different authors, neither clock isAfter the other).
        val a = LWWField("from-A", VectorClock(mapOf("A" to 1L)), timestamp = 200, authorDeviceId = "A")
        val b = LWWField("from-B", VectorClock(mapOf("B" to 1L)), timestamp = 200, authorDeviceId = "B")
        // Same timestamp -> higher author wins ("B" > "A").
        assertEquals("from-B", a.merge(b).value)
        assertEquals("from-B", b.merge(a).value)
    }

    @Test fun `merge is commutative`() {
        val a = LWWField(42L, VectorClock(mapOf("A" to 1L)), 100, "A")
        val b = LWWField(7L, VectorClock(mapOf("B" to 1L)), 200, "B")
        assertEquals(a.merge(b).value, b.merge(a).value)
    }

    @Test fun `generic non-comparable T compiles and merges`() {
        // Map is not Comparable; LWWField must still work (tie-break on ts/author only).
        val a = LWWField(mapOf("x" to 1L), VectorClock(mapOf("A" to 1L)), 100, "A")
        val b = LWWField(mapOf("y" to 2L), VectorClock(mapOf("B" to 1L)), 200, "B")
        val merged = a.merge(b)
        assertTrue(merged.value == b.value)
    }
}