package com.example.core.voice

import java.nio.ByteBuffer

/**
 * Transmission state for Push-To-Talk (PTT) walkie-talkie.
 */
enum class VoiceTransmissionState {
    IDLE,
    TRANSMITTING,
    RECEIVING
}

data class VoiceChannel(
    val id: Int,
    val name: String,
    val description: String,
    val isEmergency: Boolean = false
) {
    companion object {
        val CHANNEL_ALL_CALL = VoiceChannel(0, "Channel 0: All-Call", "Broadcast to all reachable mesh nodes")
        val CHANNEL_TACTICAL_1 = VoiceChannel(1, "Channel 1: Ops / Tactical", "Operational team coordination")
        val CHANNEL_EMERGENCY = VoiceChannel(9, "Channel 9: Emergency SOS", "Priority emergency distress channel", isEmergency = true)

        val DEFAULT_CHANNELS = listOf(
            CHANNEL_ALL_CALL,
            CHANNEL_TACTICAL_1,
            CHANNEL_EMERGENCY
        )
    }
}

/**
 * Wire-format voice packet for low-latency peer-to-peer audio streaming.
 * Audio is sampled at 16,000 Hz, 16-bit signed PCM mono.
 * 20ms frames = 320 samples = 640 bytes raw PCM.
 */
data class VoicePacket(
    val sequenceNumber: Long,
    val channelId: Int,
    val senderNodeId: String,
    val timestampMs: Long,
    val rmsEnergy: Float, // Normalized 0.0 .. 1.0 for waveform visualization
    val audioData: ByteArray
) {
    companion object {
        const val SAMPLE_RATE_HZ = 16000
        const val FRAME_DURATION_MS = 20
        const val SAMPLES_PER_FRAME = SAMPLE_RATE_HZ * FRAME_DURATION_MS / 1000 // 320 samples
        const val BYTES_PER_SAMPLE = 2 // 16-bit PCM
        const val FRAME_SIZE_BYTES = SAMPLES_PER_FRAME * BYTES_PER_SAMPLE // 640 bytes

        fun serialize(packet: VoicePacket): ByteArray {
            val senderBytes = packet.senderNodeId.toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.allocate(
                8 + // sequenceNumber (Long)
                4 + // channelId (Int)
                8 + // timestampMs (Long)
                4 + // rmsEnergy (Float)
                2 + senderBytes.size + // sender length + bytes
                4 + packet.audioData.size // audio length + bytes
            )

            buffer.putLong(packet.sequenceNumber)
            buffer.putInt(packet.channelId)
            buffer.putLong(packet.timestampMs)
            buffer.putFloat(packet.rmsEnergy)
            buffer.putShort(senderBytes.size.toShort())
            buffer.put(senderBytes)
            buffer.putInt(packet.audioData.size)
            buffer.put(packet.audioData)

            return buffer.array()
        }

        fun deserialize(bytes: ByteArray): VoicePacket? {
            return try {
                val buffer = ByteBuffer.wrap(bytes)
                val seq = buffer.getLong()
                val channelId = buffer.getInt()
                val ts = buffer.getLong()
                val rms = buffer.getFloat()
                val senderLen = buffer.getShort().toInt() and 0xFFFF
                val senderBytes = ByteArray(senderLen)
                buffer.get(senderBytes)
                val sender = String(senderBytes, Charsets.UTF_8)

                val audioLen = buffer.getInt()
                val audio = ByteArray(audioLen)
                buffer.get(audio)

                VoicePacket(
                    sequenceNumber = seq,
                    channelId = channelId,
                    senderNodeId = sender,
                    timestampMs = ts,
                    rmsEnergy = rms,
                    audioData = audio
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VoicePacket
        return sequenceNumber == other.sequenceNumber &&
                channelId == other.channelId &&
                senderNodeId == other.senderNodeId &&
                timestampMs == other.timestampMs &&
                audioData.contentEquals(other.audioData)
    }

    override fun hashCode(): Int {
        var result = sequenceNumber.hashCode()
        result = 31 * result + channelId
        result = 31 * result + senderNodeId.hashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + audioData.contentHashCode()
        return result
    }
}
