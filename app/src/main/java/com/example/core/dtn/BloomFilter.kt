package com.example.core.dtn

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.BitSet

/**
 * Standard Bloom Filter implementation for anti-entropy set reconciliation
 * in Delay-Tolerant epidemic routing.
 *
 * Employs Murmur-style dual-hash projection derived from SHA-256 to minimize collision rates
 * without requiring arbitrary hash algorithms.
 */
class BloomFilter(
    val bitSize: Int = DEFAULT_BIT_SIZE,
    val numHashFunctions: Int = DEFAULT_HASH_FUNCTIONS
) {
    companion object {
        const val DEFAULT_BIT_SIZE = 4096 // 512 bytes (fits within a single Wi-Fi Aware / BLE frame)
        const val DEFAULT_HASH_FUNCTIONS = 5

        fun fromByteArray(bytes: ByteArray, numHashFunctions: Int = DEFAULT_HASH_FUNCTIONS): BloomFilter {
            val filter = BloomFilter(bytes.size * 8, numHashFunctions)
            for (i in bytes.indices) {
                for (bit in 0..7) {
                    if ((bytes[i].toInt() and (1 shl bit)) != 0) {
                        filter.bitSet.set(i * 8 + bit)
                    }
                }
            }
            return filter
        }
    }

    val bitSet = BitSet(bitSize)

    /**
     * Inserts an item (e.g. bundleId or message SHA-256 hash) into the filter.
     */
    fun add(data: String) {
        add(data.toByteArray(Charsets.UTF_8))
    }

    fun add(data: ByteArray) {
        val hashes = computeDualHashes(data)
        val h1 = hashes.first
        val h2 = hashes.second

        for (i in 0 until numHashFunctions) {
            val combinedHash = (h1 + i.toLong() * h2) and 0x7FFFFFFFFFFFFFFFL
            val bitIndex = (combinedHash % bitSize).toInt()
            bitSet.set(bitIndex)
        }
    }

    /**
     * Checks whether an item might be present in the filter.
     * Guaranteed no false negatives; false positives are bounded by filter parameters.
     */
    fun mightContain(data: String): Boolean {
        return mightContain(data.toByteArray(Charsets.UTF_8))
    }

    fun mightContain(data: ByteArray): Boolean {
        val hashes = computeDualHashes(data)
        val h1 = hashes.first
        val h2 = hashes.second

        for (i in 0 until numHashFunctions) {
            val combinedHash = (h1 + i.toLong() * h2) and 0x7FFFFFFFFFFFFFFFL
            val bitIndex = (combinedHash % bitSize).toInt()
            if (!bitSet.get(bitIndex)) {
                return false
            }
        }
        return true
    }

    /**
     * Serializes the bitset to a compact byte array for radio transmission.
     */
    fun toByteArray(): ByteArray {
        val byteCount = (bitSize + 7) / 8
        val bytes = ByteArray(byteCount)
        for (i in 0 until bitSize) {
            if (bitSet.get(i)) {
                bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (i % 8))).toByte()
            }
        }
        return bytes
    }

    /**
     * Merges another Bloom Filter into this one (bitwise OR).
     */
    fun union(other: BloomFilter) {
        require(this.bitSize == other.bitSize) { "Bloom filter sizes must match for union" }
        this.bitSet.or(other.bitSet)
    }

    private fun computeDualHashes(data: ByteArray): Pair<Long, Long> {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        val buffer = ByteBuffer.wrap(digest)
        val h1 = buffer.long
        val h2 = buffer.long
        return Pair(h1, h2)
    }
}
