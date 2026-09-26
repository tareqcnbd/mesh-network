package com.example

import com.example.core.radio.DirectLinkType
import com.example.core.transport.PeerConnectionState
import com.example.core.transport.TransportPeerInfo
import com.example.core.transport.packet.TransportFrame
import com.example.core.transport.packet.TransportOpcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportProtocolUnitTest {

    @Test
    fun testTransportFrameSerializationAndDeserialization() {
        val payload = "Hello Mesh Transport Protocol! Testing CRC16 and byte framing.".toByteArray(Charsets.UTF_8)
        val frame = TransportFrame(
            opcode = TransportOpcode.OP_BUNDLE_OFFER,
            sequenceNumber = 42,
            payload = payload,
            flags = 0x01
        )

        val wireBytes = TransportFrame.serialize(frame)
        assertTrue(wireBytes.size > TransportFrame.HEADER_SIZE + payload.size)

        // Validate magic bytes
        assertEquals('M'.code.toByte(), wireBytes[0])
        assertEquals('S'.code.toByte(), wireBytes[1])

        val deserialized = TransportFrame.deserialize(wireBytes)
        assertNotNull(deserialized)
        assertEquals(TransportOpcode.OP_BUNDLE_OFFER, deserialized?.opcode)
        assertEquals(42, deserialized?.sequenceNumber)
        assertEquals(0x01.toByte(), deserialized?.flags)
        assertTrue(payload.contentEquals(deserialized?.payload!!))
    }

    @Test
    fun testTransportFrameCrcCorruptionDetection() {
        val payload = "Important uncorrupted mesh beacon payload".toByteArray(Charsets.UTF_8)
        val frame = TransportFrame(
            opcode = TransportOpcode.OP_HANDSHAKE_IK,
            sequenceNumber = 100,
            payload = payload
        )

        val wireBytes = TransportFrame.serialize(frame)
        
        // Corrupt a byte in the payload
        wireBytes[TransportFrame.HEADER_SIZE + 3] = (wireBytes[TransportFrame.HEADER_SIZE + 3] + 1).toByte()

        // Deserialization must reject corrupted frame due to CRC16 mismatch
        val corruptedResult = TransportFrame.deserialize(wireBytes)
        assertNull("Corrupted wire frames must be rejected by CRC validation", corruptedResult)
    }

    @Test
    fun testAllTransportOpcodesCodeConversion() {
        TransportOpcode.entries.forEach { op ->
            val resolved = TransportOpcode.fromCode(op.code)
            assertEquals(op, resolved)
        }
    }

    @Test
    fun testTransportPeerInfoModel() {
        val peer = TransportPeerInfo(
            peerId = "peer_ble_01",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            transportType = DirectLinkType.BLE_GATT,
            rssi = -65,
            connectionState = PeerConnectionState.CONNECTED,
            linkBandwidthEstimateKbps = 120
        )

        assertEquals("peer_ble_01", peer.peerId)
        assertEquals(DirectLinkType.BLE_GATT, peer.transportType)
        assertEquals(PeerConnectionState.CONNECTED, peer.connectionState)
        assertEquals(120, peer.linkBandwidthEstimateKbps)
    }
}
