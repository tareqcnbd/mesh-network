package com.example

import com.example.core.emergency.EmergencyBeacon
import com.example.core.emergency.EmergencyDistressLevel
import com.example.core.transport.packet.TransportOpcode
import com.example.core.voice.JitterBuffer
import com.example.core.voice.VoiceChannel
import com.example.core.voice.VoicePacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAndEmergencyUnitTest {

    @Test
    fun testVoicePacketSerializationRoundTrip() {
        val rawAudio = ByteArray(VoicePacket.FRAME_SIZE_BYTES) { (it % 127).toByte() }
        val original = VoicePacket(
            sequenceNumber = 42L,
            channelId = 1,
            senderNodeId = "node_bravo_1234",
            timestampMs = 1700000000000L,
            rmsEnergy = 0.75f,
            audioData = rawAudio
        )

        val serialized = VoicePacket.serialize(original)
        assertNotNull(serialized)
        assertTrue(serialized.isNotEmpty())

        val deserialized = VoicePacket.deserialize(serialized)
        assertNotNull(deserialized)
        assertEquals(42L, deserialized?.sequenceNumber)
        assertEquals(1, deserialized?.channelId)
        assertEquals("node_bravo_1234", deserialized?.senderNodeId)
        assertEquals(1700000000000L, deserialized?.timestampMs)
        assertEquals(0.75f, deserialized?.rmsEnergy ?: 0f, 0.001f)
        assertArrayEquals(rawAudio, deserialized?.audioData)
    }

    @Test
    fun testJitterBufferReorderingAndBuffering() {
        val buffer = JitterBuffer(targetDepthFrames = 3, maxDepthFrames = 6)
        val dummyAudio = ByteArray(10)

        // Queue packet 2, 0, 1 (out of order)
        val p0 = VoicePacket(0L, 0, "nodeA", 100L, 0.1f, dummyAudio)
        val p1 = VoicePacket(1L, 0, "nodeA", 120L, 0.2f, dummyAudio)
        val p2 = VoicePacket(2L, 0, "nodeA", 140L, 0.3f, dummyAudio)

        buffer.push(p2)
        buffer.push(p0)
        // Depth is 2, target is 3 -> should still be buffering
        assertNull(buffer.poll())

        // Push p1 to hit target depth (3)
        buffer.push(p1)

        // Now buffering completes and frames should emerge strictly in sequence 0, 1, 2
        val first = buffer.poll()
        assertEquals(0L, first?.sequenceNumber)

        val second = buffer.poll()
        assertEquals(1L, second?.sequenceNumber)

        val third = buffer.poll()
        assertEquals(2L, third?.sequenceNumber)

        // Queue is now drained, should return null
        assertNull(buffer.poll())
    }

    @Test
    fun testJitterBufferStalePacketDropping() {
        val buffer = JitterBuffer(targetDepthFrames = 2, maxDepthFrames = 5)
        val dummyAudio = ByteArray(10)

        buffer.push(VoicePacket(10L, 0, "nodeA", 100L, 0.1f, dummyAudio))
        buffer.push(VoicePacket(11L, 0, "nodeA", 120L, 0.1f, dummyAudio))

        val p10 = buffer.poll()
        assertEquals(10L, p10?.sequenceNumber)

        // Arrives too late: frame 9 when we are expecting >= 11
        buffer.push(VoicePacket(9L, 0, "nodeA", 80L, 0.1f, dummyAudio))

        val p11 = buffer.poll()
        assertEquals(11L, p11?.sequenceNumber)

        assertNull(buffer.poll())
    }

    @Test
    fun testVoiceChannelDefinitions() {
        val defaultChannels = VoiceChannel.DEFAULT_CHANNELS
        assertEquals(3, defaultChannels.size)

        val allCall = defaultChannels[0]
        assertEquals(0, allCall.id)
        assertFalse(allCall.isEmergency)

        val emergencyChannel = defaultChannels.find { it.isEmergency }
        assertNotNull(emergencyChannel)
        assertEquals(9, emergencyChannel?.id)
    }

    @Test
    fun testEmergencyBeaconSerializationAndAllClear() {
        val beacon = EmergencyBeacon(
            beaconId = "sos-uuid-123",
            senderNodeId = "node_alfa_9999",
            senderAlias = "Node Alpha",
            distressLevel = EmergencyDistressLevel.SOS_CRITICAL,
            distressMessage = "Medical evacuation required",
            latitude = 37.7749,
            longitude = -122.4194,
            batteryPct = 42,
            isCancelled = false
        )

        val json = beacon.toJson()
        val parsed = EmergencyBeacon.fromJson(json)

        assertNotNull(parsed)
        assertEquals("sos-uuid-123", parsed?.beaconId)
        assertEquals("node_alfa_9999", parsed?.senderNodeId)
        assertEquals("Node Alpha", parsed?.senderAlias)
        assertEquals(EmergencyDistressLevel.SOS_CRITICAL, parsed?.distressLevel)
        assertEquals("Medical evacuation required", parsed?.distressMessage)
        assertEquals(37.7749, parsed?.latitude ?: 0.0, 0.0001)
        assertEquals(-122.4194, parsed?.longitude ?: 0.0, 0.0001)
        assertEquals(42, parsed?.batteryPct)
        assertFalse(parsed?.isCancelled ?: true)

        // Test All-Clear cancellation
        val cancelled = parsed?.copy(
            isCancelled = true,
            distressMessage = "ALL-CLEAR: Threat mitigated"
        )
        val cancelledParsed = EmergencyBeacon.fromJson(cancelled!!.toJson())
        assertTrue(cancelledParsed?.isCancelled ?: false)
        assertEquals("ALL-CLEAR: Threat mitigated", cancelledParsed?.distressMessage)
    }

    @Test
    fun testTransportOpcodesForVoiceAndSos() {
        assertEquals(0x09.toByte(), TransportOpcode.OP_VOICE_STREAM.code)
        assertEquals(0x0A.toByte(), TransportOpcode.OP_EMERGENCY_BEACON.code)

        assertEquals(TransportOpcode.OP_VOICE_STREAM, TransportOpcode.fromCode(0x09))
        assertEquals(TransportOpcode.OP_EMERGENCY_BEACON, TransportOpcode.fromCode(0x0A))
    }
}
