package com.example.core.media.compression

import com.example.core.media.model.MediaType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class CompressionResult(
    val data: ByteArray,
    val isCompressed: Boolean,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val compressionRatio: Float // e.g., 0.42 = 42% of original size
)

object CompressionEngine {

    /**
     * Compresses byte array using standard GZIP.
     */
    fun compressGzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(data)
        }
        return bos.toByteArray()
    }

    /**
     * Decompresses GZIP-compressed byte array.
     */
    fun decompressGzip(compressed: ByteArray): ByteArray {
        val bis = ByteArrayInputStream(compressed)
        val bos = ByteArrayOutputStream()
        GZIPInputStream(bis).use { gzip ->
            val buffer = ByteArray(4096)
            var len: Int
            while (gzip.read(buffer).also { len = it } > 0) {
                bos.write(buffer, 0, len)
            }
        }
        return bos.toByteArray()
    }

    /**
     * Analyzes and adaptively compresses payload if compression yields a space reduction.
     */
    fun adaptiveCompress(raw: ByteArray): CompressionResult {
        if (raw.size < 64) {
            // Very small payloads often grow when gzipped due to headers
            return CompressionResult(
                data = raw,
                isCompressed = false,
                originalSizeBytes = raw.size.toLong(),
                compressedSizeBytes = raw.size.toLong(),
                compressionRatio = 1.0f
            )
        }

        try {
            val compressed = compressGzip(raw)
            if (compressed.size < raw.size * 0.95) { // At least 5% reduction
                val ratio = compressed.size.toFloat() / raw.size.toFloat()
                return CompressionResult(
                    data = compressed,
                    isCompressed = true,
                    originalSizeBytes = raw.size.toLong(),
                    compressedSizeBytes = compressed.size.toLong(),
                    compressionRatio = ratio
                )
            }
        } catch (e: Exception) {
            // Fall back to uncompressed
        }

        return CompressionResult(
            data = raw,
            isCompressed = false,
            originalSizeBytes = raw.size.toLong(),
            compressedSizeBytes = raw.size.toLong(),
            compressionRatio = 1.0f
        )
    }

    /**
     * Generates realistic tactical sample payloads for demonstrations and automated offline mesh testing.
     */
    fun generatePresetSample(type: MediaType): Pair<String, ByteArray> {
        return when (type) {
            MediaType.IMAGE -> {
                val filename = "recon_sector_9_overhead.jpg"
                // Generate synthetic imagery payload (with valid JPEG SOI/EOI markers and payload)
                val buffer = ByteArrayOutputStream()
                buffer.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())) // JPEG SOI + APP0
                val metadata = "JFIF\u0000\u0001\u0001\u0000\u0000\u0001\u0000\u0001\u0000\u0000".toByteArray(Charsets.ISO_8859_1)
                buffer.write(metadata)
                // Append 28 KB of synthetic raster texture blocks
                val dummyPixelRow = "TAC_RECON_GRID_ELEVATION_850M_BEARING_045_CONFIDENTIAL_MESH_DISTRIBUTION_".repeat(380)
                buffer.write(dummyPixelRow.toByteArray(Charsets.UTF_8))
                buffer.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte())) // JPEG EOI
                filename to buffer.toByteArray()
            }

            MediaType.DOCUMENT -> {
                val filename = "situational_report_sop_24.txt"
                val doc = buildString {
                    appendLine("================================================================")
                    appendLine("TACTICAL MESH NETWORK INCIDENT SITUATION REPORT (SITREP)")
                    appendLine("INCIDENT ID: INC-2026-GRID-FAIL | CLEARANCE: RESTRICTED")
                    appendLine("================================================================")
                    appendLine("1. PRIMARY SITUATION SUMMARY:")
                    appendLine("   - Grid power loss confirmed across 4 major metropolitan sectors.")
                    appendLine("   - Cellular base stations on battery backup (estimated 2h runtime).")
                    appendLine("   - Primary P2P delay-tolerant mesh network established over BLE & Wi-Fi Aware.")
                    appendLine("2. LOGISTICS & EVACUATION ROUTES:")
                    appendLine("   - Route Baker is open for emergency medical transports.")
                    appendLine("   - High-voltage power lines down at Waypoint Delta-3 (37.7787, -122.4172).")
                    appendLine("   - Water & medical supply caches established at Alpha Assembly Zone.")
                    appendLine("3. COMMUNICATIONS & CRYPTO DIRECTIVES:")
                    appendLine("   - Double-Ratchet sessions re-keyed every 50 ratchet steps.")
                    appendLine("   - All tactical markers synchronized via DTN Epidemic Flooding.")
                    appendLine("   - Voice channels: PTT All-Call (Ch 1) and Ops Tactical (Ch 2).")
                    appendLine("================================================================")
                    appendLine("Generated by Tactical Lead Operator Node.")
                }
                filename to doc.toByteArray(Charsets.UTF_8)
            }

            MediaType.TACTICAL_MAP -> {
                val filename = "tactical_grid_overlay.geojson"
                val geoJson = """
                    {
                      "type": "FeatureCollection",
                      "features": [
                        {
                          "type": "Feature",
                          "properties": { "name": "Assembly Alpha", "type": "RALLY_POINT", "capacity": 200 },
                          "geometry": { "type": "Point", "coordinates": [-122.4179, 37.7769] }
                        },
                        {
                          "type": "Feature",
                          "properties": { "name": "Sector Perimeter", "type": "ZONE_BOUNDARY", "status": "SECURED" },
                          "geometry": {
                            "type": "Polygon",
                            "coordinates": [[
                              [-122.4250, 37.7700],
                              [-122.4100, 37.7700],
                              [-122.4100, 37.7850],
                              [-122.4250, 37.7850],
                              [-122.4250, 37.7700]
                            ]]
                          }
                        }
                      ]
                    }
                """.trimIndent()
                filename to geoJson.toByteArray(Charsets.UTF_8)
            }

            MediaType.AUDIO_RECORDING -> {
                val filename = "tactical_voice_intercept.pcm"
                // 16-bit 16kHz synthetic sound wave
                val samples = ByteArray(32000) // 1 second of audio
                for (i in 0 until 16000) {
                    val angle = 2.0 * Math.PI * i * 440.0 / 16000.0 // 440 Hz tone
                    val sample = (kotlin.math.sin(angle) * 16000).toInt().toShort()
                    samples[i * 2] = (sample.toInt() and 0xFF).toByte()
                    samples[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                }
                filename to samples
            }

            MediaType.SYSTEM_ARCHIVE -> {
                val filename = "mesh_router_audit.log"
                val log = buildString {
                    for (i in 1..80) {
                        appendLine("[NODE-RELAY-0${i % 8}] TS=${1700000000000L + i * 1000} RSSI=-${45 + (i % 35)}dBm LINK=WIFI_AWARE STATUS=FORWARDED CHUNKS=8/8")
                    }
                }
                filename to log.toByteArray(Charsets.UTF_8)
            }
        }
    }
}
