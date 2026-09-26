package com.example

import com.example.core.media.compression.CompressionEngine
import com.example.core.media.model.BandwidthProfile
import com.example.core.media.model.ChunkBitfield
import com.example.core.media.model.ChunkIntegrity
import com.example.core.media.model.MediaType
import com.example.core.radio.TransportTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTransferUnitTest {

    @Test
    fun testSha256ChecksumVerification() {
        val testString = "Mesh Network Offline Chunk Fragment 42"
        val bytes = testString.toByteArray(Charsets.UTF_8)

        val hash = ChunkIntegrity.computeSha256(bytes)
        assertNotNull(hash)
        assertEquals("SHA-256 hex string should be 64 characters", 64, hash.length)

        // Valid verification
        assertTrue("Hash verification should succeed for matching data", ChunkIntegrity.verifySha256(bytes, hash))

        // Mismatched data verification
        val corruptedBytes = testString.replace("42", "43").toByteArray(Charsets.UTF_8)
        assertFalse("Hash verification should fail for tampered/corrupted data", ChunkIntegrity.verifySha256(corruptedBytes, hash))
    }

    @Test
    fun testChunkBitfieldTrackingAndRepair() {
        val totalChunks = 10
        val bitfield = ChunkBitfield(totalChunks)

        assertEquals(0, bitfield.completedCount())
        assertEquals(0.0f, bitfield.completionFraction(), 0.001f)
        assertFalse(bitfield.isAllComplete())
        assertEquals(10, bitfield.getMissingIndices().size)

        // Set chunks 0, 1, 2, 4, 6 complete
        bitfield.setComplete(0)
        bitfield.setComplete(1)
        bitfield.setComplete(2)
        bitfield.setComplete(4)
        bitfield.setComplete(6)

        assertEquals(5, bitfield.completedCount())
        assertEquals(0.5f, bitfield.completionFraction(), 0.001f)
        assertTrue(bitfield.isComplete(0))
        assertTrue(bitfield.isComplete(4))
        assertFalse(bitfield.isComplete(3))
        assertFalse(bitfield.isComplete(5))

        // Verify missing indices for selective repair
        val missing = bitfield.getMissingIndices()
        assertEquals(listOf(3, 5, 7, 8, 9), missing)

        // Test hex serialization round-trip
        val hex = bitfield.toHex()
        val deserializedBitfield = ChunkBitfield.fromHex(hex, totalChunks)
        assertEquals(5, deserializedBitfield.completedCount())
        assertTrue(deserializedBitfield.isComplete(1))
        assertFalse(deserializedBitfield.isComplete(3))

        // Complete remaining chunks
        missing.forEach { bitfield.setComplete(it) }
        assertTrue(bitfield.isAllComplete())
        assertEquals(1.0f, bitfield.completionFraction(), 0.001f)
        assertTrue(bitfield.getMissingIndices().isEmpty())
    }

    @Test
    fun testAdaptiveGzipCompressionRoundTrip() {
        val repetitiveDocument = "CRITICAL SITUATION REPORT: ALL UNITS EVACUATE TO SECTOR 4 IMMEDIATELY.\n".repeat(50)
        val rawBytes = repetitiveDocument.toByteArray(Charsets.UTF_8)

        val result = CompressionEngine.adaptiveCompress(rawBytes)

        // Repetitive text should achieve high compression
        assertTrue("Repetitive tactical text should be compressed", result.isCompressed)
        assertTrue("Compressed size should be substantially smaller", result.compressedSizeBytes < result.originalSizeBytes * 0.4)
        assertTrue("Compression ratio should reflect savings", result.compressionRatio < 0.4f)

        // Decompress and verify lossless identity
        val restoredBytes = CompressionEngine.decompressGzip(result.data)
        val restoredString = String(restoredBytes, Charsets.UTF_8)
        assertEquals("Decompressed text must match original exactly", repetitiveDocument, restoredString)
    }

    @Test
    fun testSmallPayloadAdaptiveCompressionBypass() {
        val tinyPayload = "Hello Mesh".toByteArray(Charsets.UTF_8)
        val result = CompressionEngine.adaptiveCompress(tinyPayload)

        // Very small payloads should bypass compression to avoid GZIP header overhead
        assertFalse("Small payload should not be compressed", result.isCompressed)
        assertEquals(tinyPayload.size.toLong(), result.compressedSizeBytes)
    }

    @Test
    fun testBandwidthProfileTierResolution() {
        val bleProfile = BandwidthProfile.BLE_NARROW
        assertEquals(512, bleProfile.chunkSizeBytes)
        assertEquals(120L, bleProfile.throttleDelayMs)
        assertTrue(bleProfile.aggressiveCompression)

        val wifiAwareProfile = BandwidthProfile.WIFI_AWARE_BALANCED
        assertEquals(4096, wifiAwareProfile.chunkSizeBytes)
        assertEquals(25L, wifiAwareProfile.throttleDelayMs)

        val wifiDirectProfile = BandwidthProfile.WIFI_DIRECT_HIGH_THROUGHPUT
        assertEquals(16384, wifiDirectProfile.chunkSizeBytes)
        assertEquals(5L, wifiDirectProfile.throttleDelayMs)

        // Resolution by tier
        val resolvedWan = BandwidthProfile.getProfileForTier(TransportTier.TIER_1_INTERNET)
        assertEquals(8192, resolvedWan.chunkSizeBytes)

        val resolvedDirect = BandwidthProfile.getProfileForTier(TransportTier.TIER_3_MESH, isHighSpeedPreferred = true)
        assertEquals(16384, resolvedDirect.chunkSizeBytes)
    }

    @Test
    fun testPresetSamplesGeneration() {
        val (imgName, imgBytes) = CompressionEngine.generatePresetSample(MediaType.IMAGE)
        assertTrue(imgName.endsWith(".jpg"))
        assertTrue("Image payload should be substantial", imgBytes.size > 20000)

        val (docName, docBytes) = CompressionEngine.generatePresetSample(MediaType.DOCUMENT)
        assertTrue(docName.endsWith(".txt"))
        val docContent = String(docBytes, Charsets.UTF_8)
        assertTrue(docContent.contains("TACTICAL MESH NETWORK"))

        val (mapName, mapBytes) = CompressionEngine.generatePresetSample(MediaType.TACTICAL_MAP)
        assertTrue(mapName.endsWith(".geojson"))
        val mapJson = String(mapBytes, Charsets.UTF_8)
        assertTrue(mapJson.contains("FeatureCollection"))
    }
}
