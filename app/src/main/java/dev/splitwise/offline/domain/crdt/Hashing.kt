package dev.splitwise.offline.domain.crdt

import java.security.MessageDigest

/**
 * Tiny helpers for content-addressing CRDT state.
 * Used to compute Merkle-style hashes so two devices can cheaply figure out
 * "do we already agree on this group?" before exchanging any payload.
 */
object Hashing {
    private val HEX = "0123456789abcdef"

    fun sha256(data: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(HEX[(b.toInt() ushr 4) and 0x0F])
            sb.append(HEX[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    /** Order-independent hash of a collection of strings (combine via sorted digest). */
    fun merkleRoot(items: Collection<String>): String =
        if (items.isEmpty()) sha256("") else sha256(items.sorted().joinToString("|"))
}