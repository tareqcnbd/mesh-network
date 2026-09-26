package com.example.core.transport.packet

import java.nio.ByteBuffer
import java.util.UUID

/**
 * Standard Framing for data transferred over physical radio links (BLE, Wi-Fi Aware, Wi-Fi Direct).
 *
 * Wire format:
 * [Magic: 2 bytes 0x4D 0x53 ("MS")]
 * [Opcode: 1 byte]
 * [Flags: 1 byte]
 * [Sequence / PacketId: 4 bytes]
 * [Payload Length: 2 bytes]
 * [Payload: N bytes]
 * [CRC16: 2 bytes]
 */
enum class TransportOpcode(val code: Byte) {
    OP_HANDSHAKE_IK(0x01),      // Exchange Identity Key compressed & ephemeral prekey
    OP_BLOOM_FILTER(0x02),      // Vector exchange of carried bundle inventory
    OP_BUNDLE_OFFER(0x03),      // List of bundle IDs available for transmission
    OP_BUNDLE_REQUEST(0x04),    // Request specific bundle IDs
    OP_BUNDLE_CHUNK(0x05),      // MTU-sized data fragment for large bundles
    OP_BUNDLE_COMPLETE(0x06),   // Complete bundle payload + ECDSA signature
    OP_DELIVERY_ACK(0x07),      // DelAck receipt
    OP_HEARTBEAT(0x08),         // Keepalive / RSSI probe
    OP_VOICE_STREAM(0x09),      // Real-time Push-to-Talk PCM audio streaming packet
    OP_EMERGENCY_BEACON(0x0A),  // High-priority SOS distress beacon frame
    OP_GEO_MARKER(0x0B),        // Geospatial tactical marker or telemetry frame
    OP_GEO_TRACK(0x0C),         // Breadcrumb position track sync frame
    OP_MEDIA_META(0x0D),        // File transfer metadata & SHA-256 header
    OP_MEDIA_CHUNK(0x0E),       // Verified payload chunk with SHA-256 slice
    OP_MEDIA_CHUNK_ACK(0x0F),   // Chunk receipt confirmation / selective repeat request
    OP_REPUTATION_ATTEST(0x10), // Peer trust score attestation gossip frame
    OP_POW_CHALLENGE(0x11),     // Rate-limiting dynamic PoW puzzle challenge
    OP_POW_TOKEN(0x12),         // Validated proof-of-work solution token
    OP_QUARANTINE_ALERT(0x13);  // Broadcast alert for quarantined spamming/replay peer

    companion object {
        fun fromCode(code: Byte): TransportOpcode? = entries.firstOrNull { it.code == code }
    }
}

data class TransportFrame(
    val opcode: TransportOpcode,
    val sequenceNumber: Int,
    val payload: ByteArray,
    val flags: Byte = 0
) {
    companion object {
        const val MAGIC_BYTE_1: Byte = 0x4D // 'M'
        const val MAGIC_BYTE_2: Byte = 0x53 // 'S'
        const val HEADER_SIZE = 10
        const val MAX_PAYLOAD_SIZE = 65535

        fun serialize(frame: TransportFrame): ByteArray {
            val totalSize = HEADER_SIZE + frame.payload.size + 2 // 2 bytes CRC16
            val buffer = ByteBuffer.allocate(totalSize)
            buffer.put(MAGIC_BYTE_1)
            buffer.put(MAGIC_BYTE_2)
            buffer.put(frame.opcode.code)
            buffer.put(frame.flags)
            buffer.putInt(frame.sequenceNumber)
            buffer.putShort(frame.payload.size.toShort())
            buffer.put(frame.payload)

            // Compute CRC16 over header + payload
            val crc = computeCrc16(buffer.array(), 0, HEADER_SIZE + frame.payload.size)
            buffer.putShort(crc.toShort())

            return buffer.array()
        }

        fun deserialize(bytes: ByteArray): TransportFrame? {
            if (bytes.size < HEADER_SIZE + 2) return null
            val buffer = ByteBuffer.wrap(bytes)
            val m1 = buffer.get()
            val m2 = buffer.get()
            if (m1 != MAGIC_BYTE_1 || m2 != MAGIC_BYTE_2) return null

            val opByte = buffer.get()
            val opcode = TransportOpcode.fromCode(opByte) ?: return null
            val flags = buffer.get()
            val seq = buffer.getInt()
            val payloadLen = buffer.getShort().toInt() and 0xFFFF

            if (bytes.size < HEADER_SIZE + payloadLen + 2) return null

            val payload = ByteArray(payloadLen)
            buffer.get(payload)

            val expectedCrc = buffer.getShort().toInt() and 0xFFFF
            val actualCrc = computeCrc16(bytes, 0, HEADER_SIZE + payloadLen)

            if (expectedCrc != actualCrc) {
                return null // CRC mismatch / corrupted over-the-air frame
            }

            return TransportFrame(
                opcode = opcode,
                sequenceNumber = seq,
                payload = payload,
                flags = flags
            )
        }

        private fun computeCrc16(data: ByteArray, offset: Int, length: Int): Int {
            var crc = 0xFFFF
            for (i in offset until (offset + length)) {
                crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
                for (j in 0 until 8) {
                    crc = if ((crc and 0x8000) != 0) {
                        (crc shl 1) xor 0x1021
                    } else {
                        crc shl 1
                    }
                }
            }
            return crc and 0xFFFF
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransportFrame

        if (opcode != other.opcode) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (!payload.contentEquals(other.payload)) return false
        if (flags != other.flags) return false

        return true
    }

    override fun hashCode(): Int {
        var result = opcode.hashCode()
        result = 31 * result + sequenceNumber
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + flags.hashCode()
        return result
    }
}
