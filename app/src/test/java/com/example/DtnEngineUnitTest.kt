package com.example

import com.example.core.dtn.BloomFilter
import com.example.core.dtn.model.BundlePriority
import com.example.core.dtn.model.BundleStatus
import com.example.core.dtn.model.DtnBundleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DtnEngineUnitTest {

    @Test
    fun testBloomFilterInsertionAndMembership() {
        val filter = BloomFilter(bitSize = 1024, numHashFunctions = 4)
        val testBundleId1 = "bundle-uuid-123456"
        val testBundleId2 = "bundle-uuid-abcdef"
        val testBundleId3 = "bundle-uuid-999999"

        filter.add(testBundleId1)
        filter.add(testBundleId2)

        assertTrue(filter.mightContain(testBundleId1))
        assertTrue(filter.mightContain(testBundleId2))
        assertFalse(filter.mightContain(testBundleId3))
    }

    @Test
    fun testBloomFilterSerializationRoundTrip() {
        val original = BloomFilter(bitSize = 1024, numHashFunctions = 4)
        original.add("packet-1")
        original.add("packet-2")

        val serializedBytes = original.toByteArray()
        val deserialized = BloomFilter.fromByteArray(serializedBytes, numHashFunctions = 4)

        assertTrue(deserialized.mightContain("packet-1"))
        assertTrue(deserialized.mightContain("packet-2"))
        assertFalse(deserialized.mightContain("packet-3"))
    }

    @Test
    fun testBundleTtlDecay() {
        val now = System.currentTimeMillis()
        val activeBundle = DtnBundleEntity(
            bundleId = "active-1",
            sourceNodeId = "nodeA",
            destinationNodeId = "nodeB",
            expiresAtEpochMs = now + 10_000,
            payloadSizeBytes = 128,
            encryptedPayloadHex = "aabbcc",
            senderSignatureHex = "112233"
        )
        assertFalse(activeBundle.isExpired)

        val expiredBundle = DtnBundleEntity(
            bundleId = "expired-1",
            sourceNodeId = "nodeA",
            destinationNodeId = "nodeB",
            expiresAtEpochMs = now - 1000,
            payloadSizeBytes = 128,
            encryptedPayloadHex = "aabbcc",
            senderSignatureHex = "112233"
        )
        assertTrue(expiredBundle.isExpired)
    }

    @Test
    fun testBundlePriorityWeights() {
        assertTrue(BundlePriority.EMERGENCY.weight > BundlePriority.HIGH.weight)
        assertTrue(BundlePriority.HIGH.weight > BundlePriority.NORMAL.weight)
        assertTrue(BundlePriority.NORMAL.weight > BundlePriority.LOW.weight)
    }
}
