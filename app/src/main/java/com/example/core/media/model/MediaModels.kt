package com.example.core.media.model

import com.example.core.radio.TransportTier
import java.security.MessageDigest
import java.util.BitSet
import java.util.UUID

enum class TransferDirection {
    OUTGOING,
    INCOMING
}

enum class TransferStatus(val label: String) {
    PENDING("Queued"),
    TRANSFERRING("Transferring"),
    PAUSED("Paused"),
    COMPLETED("Verified Complete"),
    FAILED("Transfer Failed"),
    CANCELLED("Cancelled")
}

enum class MediaType(val label: String, val mimeType: String) {
    IMAGE("Recon Photo", "image/jpeg"),
    DOCUMENT("Tactical Document", "text/plain"),
    TACTICAL_MAP("Vector Map Layer", "application/json"),
    AUDIO_RECORDING("Voice Log", "audio/pcm"),
    SYSTEM_ARCHIVE("Mesh Diagnostic Log", "application/zip")
}

enum class ChunkStatus {
    MISSING,
    IN_FLIGHT,
    RECEIVED,
    VERIFIED,
    CORRUPTED
}

data class ChunkInfo(
    val index: Int,
    val offsetBytes: Long,
    val sizeBytes: Int,
    val sha256Hash: String,
    val status: ChunkStatus = ChunkStatus.MISSING
)

object ChunkIntegrity {

    fun computeSha256(data: ByteArray, offset: Int = 0, length: Int = data.size): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data, offset, length)
        val hash = digest.digest()
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun verifySha256(data: ByteArray, expectedHex: String): Boolean {
        val actual = computeSha256(data)
        return actual.equals(expectedHex, ignoreCase = true)
    }
}

/**
 * Compact bitfield tracking received & verified chunk indices.
 * Provides fast bitwise checks and serialization into hex for wire and DB storage.
 */
class ChunkBitfield(val totalChunks: Int) {
    private val bitSet: BitSet = BitSet(totalChunks.coerceAtLeast(1))

    fun setComplete(index: Int) {
        if (index in 0 until totalChunks) {
            bitSet.set(index)
        }
    }

    fun isComplete(index: Int): Boolean {
        if (index !in 0 until totalChunks) return false
        return bitSet.get(index)
    }

    fun completedCount(): Int = bitSet.cardinality()

    fun isAllComplete(): Boolean = completedCount() >= totalChunks

    fun completionFraction(): Float {
        if (totalChunks <= 0) return 1f
        return (completedCount().toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f)
    }

    fun getMissingIndices(): List<Int> {
        val missing = mutableListOf<Int>()
        for (i in 0 until totalChunks) {
            if (!bitSet.get(i)) {
                missing.add(i)
            }
        }
        return missing
    }

    fun toHex(): String {
        val bytes = bitSet.toByteArray()
        if (bytes.isEmpty()) return "00"
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun fromHex(hex: String, totalChunks: Int): ChunkBitfield {
            val bitfield = ChunkBitfield(totalChunks)
            if (hex.isBlank()) return bitfield
            try {
                val len = hex.length
                val bytes = ByteArray(len / 2)
                for (i in 0 until len step 2) {
                    bytes[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
                }
                val bs = BitSet.valueOf(bytes)
                for (i in 0 until totalChunks) {
                    if (bs.get(i)) {
                        bitfield.setComplete(i)
                    }
                }
            } catch (e: Exception) {
                // Ignore corrupted hex fallback to blank
            }
            return bitfield
        }
    }
}

/**
 * Dynamic Bandwidth Adaptation Profile.
 * Selects chunk size, throttle delay, and compression levels based on active transport tier.
 */
data class BandwidthProfile(
    val name: String,
    val tier: TransportTier,
    val chunkSizeBytes: Int,
    val throttleDelayMs: Long,
    val aggressiveCompression: Boolean,
    val description: String
) {
    companion object {
        val BLE_NARROW = BandwidthProfile(
            name = "BLE 5.0 (Narrow Link)",
            tier = TransportTier.TIER_3_MESH,
            chunkSizeBytes = 512,
            throttleDelayMs = 120L,
            aggressiveCompression = true,
            description = "512B chunks, 120ms rate-limit, maximum GZIP compression"
        )

        val WIFI_AWARE_BALANCED = BandwidthProfile(
            name = "Wi-Fi Aware (Balanced)",
            tier = TransportTier.TIER_3_MESH,
            chunkSizeBytes = 4096,
            throttleDelayMs = 25L,
            aggressiveCompression = false,
            description = "4KB chunks, 25ms rate-limit, standard compression"
        )

        val WIFI_DIRECT_HIGH_THROUGHPUT = BandwidthProfile(
            name = "Wi-Fi Direct (High Speed)",
            tier = TransportTier.TIER_3_MESH,
            chunkSizeBytes = 16384, // 16 KB
            throttleDelayMs = 5L,
            aggressiveCompression = false,
            description = "16KB chunks, 5ms burst, high-throughput"
        )

        val CLOUD_WAN_ADAPTIVE = BandwidthProfile(
            name = "Cloud WAN Relay",
            tier = TransportTier.TIER_1_INTERNET,
            chunkSizeBytes = 8192, // 8 KB
            throttleDelayMs = 15L,
            aggressiveCompression = false,
            description = "8KB chunks, 15ms pace over WebSocket tunnel"
        )

        val LOCAL_HOTSPOT_MID = BandwidthProfile(
            name = "Local Hotspot (Tier 2)",
            tier = TransportTier.TIER_2_HOTSPOT,
            chunkSizeBytes = 8192,
            throttleDelayMs = 10L,
            aggressiveCompression = false,
            description = "8KB chunks, 10ms pace over 5GHz Local AP"
        )

        fun getProfileForTier(tier: TransportTier, isHighSpeedPreferred: Boolean = false): BandwidthProfile {
            return when (tier) {
                TransportTier.TIER_1_INTERNET -> CLOUD_WAN_ADAPTIVE
                TransportTier.TIER_2_HOTSPOT -> LOCAL_HOTSPOT_MID
                TransportTier.TIER_3_MESH -> {
                    if (isHighSpeedPreferred) WIFI_DIRECT_HIGH_THROUGHPUT else WIFI_AWARE_BALANCED
                }
            }
        }
    }
}
