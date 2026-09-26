package com.example

import com.example.core.crypto.EccCryptoEngine
import com.example.core.crypto.MeshSessionCrypto
import com.example.core.dtn.model.BundlePriority
import com.example.core.dtn.model.DtnBundleEntity
import com.example.core.transport.ble.BleFrameFramer
import com.example.core.transport.ble.BleInitiatorElection
import com.example.core.transport.ble.BleReassemblyBuffer
import com.example.core.transport.packet.BundleCodec
import com.example.core.transport.packet.BundlePayloadRouter
import com.example.core.transport.packet.BundleSignatureVerifier
import com.example.core.transport.packet.ChatPayload
import com.example.core.transport.packet.HandshakePayload
import com.example.core.transport.packet.TransportFrame
import com.example.core.transport.packet.TransportOpcode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshLinkProtocolUnitTest {

    @Test
    fun handshakePayloadRoundTripAndSignatureBytes() {
        val identity = EccCryptoEngine.generateKeyPair()
        val ephemeral = EccCryptoEngine.generateKeyPair()
        val identityPub = EccCryptoEngine.encodePublicKeyCompressed(identity.public)
        val ephPub = EccCryptoEngine.encodePublicKeyCompressed(ephemeral.public)
        val toSign = HandshakePayload.signedBytes(
            nodeId = "ABCD:1234:5678:90EF",
            alias = "Field Alpha",
            identityPublicKey = identityPub,
            ephemeralPublicKey = ephPub
        )
        val signature = EccCryptoEngine.sign(identity.private, toSign)
        val payload = HandshakePayload(
            nodeId = "ABCD:1234:5678:90EF",
            alias = "Field Alpha",
            identityPublicKey = identityPub,
            ephemeralPublicKey = ephPub,
            signature = signature
        )

        val decoded = HandshakePayload.fromByteArray(payload.toByteArray())
        assertNotNull(decoded)
        assertEquals(payload, decoded)
        assertTrue(EccCryptoEngine.verify(identity.public, decoded!!.signedBytes(), decoded.signature))
    }

    @Test
    fun handshakeRejectsUnknownVersion() {
        val bytes = byteArrayOf(99, 0, 0)
        assertNull(HandshakePayload.fromByteArray(bytes))
    }

    @Test
    fun bundleCodecRoundTrip() {
        val original = DtnBundleEntity(
            bundleId = "bundle-42",
            sourceNodeId = "AA:BB:CC:DD",
            destinationNodeId = "*",
            isBroadcast = true,
            priority = BundlePriority.HIGH,
            hopCount = 2,
            maxHops = 8,
            createdAtEpochMs = 1_700_000_000_000L,
            expiresAtEpochMs = 1_700_086_400_000L,
            payloadSizeBytes = 4,
            encryptedPayloadHex = "cafebabe",
            senderSignatureHex = "deadbeef",
            ephemeralRoutingHeaderHex = "01ff"
        )
        val decoded = BundleCodec.decode(BundleCodec.encode(original))
        assertNotNull(decoded)
        assertEquals(original.bundleId, decoded!!.bundleId)
        assertEquals(original.sourceNodeId, decoded.sourceNodeId)
        assertEquals(original.destinationNodeId, decoded.destinationNodeId)
        assertTrue(decoded.isBroadcast)
        assertEquals(BundlePriority.HIGH, decoded.priority)
        assertEquals(2, decoded.hopCount)
        assertEquals(8, decoded.maxHops)
        assertEquals("cafebabe", decoded.encryptedPayloadHex)
        assertEquals("deadbeef", decoded.senderSignatureHex)
        assertEquals("01ff", decoded.ephemeralRoutingHeaderHex)
    }

    @Test
    fun bleChunksReassembleOutOfOrder() {
        val frame = TransportFrame.serialize(
            TransportFrame(
                opcode = TransportOpcode.OP_BUNDLE_COMPLETE,
                sequenceNumber = 7,
                payload = ByteArray(400) { it.toByte() }
            )
        )
        val chunks = BleFrameFramer.split(frame, maxChunkBytes = 40)
        assertTrue(chunks.size > 3)
        chunks.forEach { assertTrue(BleFrameFramer.isChunk(it)) }

        val buffer = BleReassemblyBuffer()
        var assembled: ByteArray? = null
        chunks.reversed().forEach { chunk ->
            assembled = buffer.offer(chunk) ?: assembled
        }
        assertNotNull(assembled)
        assertArrayEquals(frame, assembled)
    }

    @Test
    fun bleNonChunkedPayloadPassesThrough() {
        val raw = byteArrayOf(0x4D, 0x53, 0x01)
        val complete = BleReassemblyBuffer().offer(raw)
        assertArrayEquals(raw, complete)
    }

    @Test
    fun initiatorElectionIsAntisymmetric() {
        val hashA = BleInitiatorElection.nodeIdHash("node-aaa")
        val hashB = BleInitiatorElection.nodeIdHash("node-bbb")
        val aInitiates = BleInitiatorElection.shouldInitiate(hashA, hashB)
        val bInitiates = BleInitiatorElection.shouldInitiate(hashB, hashA)
        assertTrue(aInitiates xor bInitiates)
    }

    @Test
    fun chatPayloadRoundTrip() {
        val encoded = ChatPayload.encode("Meet at the gate")
        assertTrue(ChatPayload.isChat(encoded))
        assertEquals("Meet at the gate", ChatPayload.decode(encoded))
        assertFalse(ChatPayload.isChat("not-chat".toByteArray()))
    }

    @Test
    fun chatPayloadSurvivesBundleCodec() {
        val payload = ChatPayload.encode("Meet at the gate")
        val original = DtnBundleEntity(
            bundleId = "chat-1",
            sourceNodeId = "AA:BB",
            destinationNodeId = "CC:DD",
            hopCount = 0,
            maxHops = 8,
            expiresAtEpochMs = 1_700_086_400_000L,
            payloadSizeBytes = payload.size.toLong(),
            encryptedPayloadHex = payload.joinToString("") { "%02x".format(it) },
            senderSignatureHex = "ab"
        )
        val decoded = BundleCodec.decode(BundleCodec.encode(original))
        assertNotNull(decoded)
        val restored = decoded!!.encryptedPayloadHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals("Meet at the gate", ChatPayload.decode(restored))
    }

    @Test
    fun bloomFilterSelectsBundlesPeerDoesNotHave() {
        val peerFilter = com.example.core.dtn.BloomFilter()
        peerFilter.add("have-already")
        val localIds = listOf("have-already", "need-this", "and-this")
        val missing = localIds.filter { !peerFilter.mightContain(it) }
        assertEquals(listOf("need-this", "and-this"), missing)
    }

    @Test
    fun ephemeralSessionKeysMatchOnBothSides() {
        val a = EccCryptoEngine.generateKeyPair()
        val b = EccCryptoEngine.generateKeyPair()
        val keyA = MeshSessionCrypto.deriveSessionKey(a, b.public)
        val keyB = MeshSessionCrypto.deriveSessionKey(b, a.public)
        assertArrayEquals(keyA, keyB)
        assertEquals(32, keyA.size)
    }

    @Test
    fun missingHashDoesNotInitiate() {
        val local = BleInitiatorElection.nodeIdHash("node-a")
        assertFalse(BleInitiatorElection.shouldInitiate(local, null))
        assertFalse(BleInitiatorElection.shouldInitiate(local, ByteArray(0)))
    }

    @Test
    fun manufacturerScanResponseHashIsAntisymmetric() {
        val hashA = BleInitiatorElection.nodeIdHash("node-aaa")
        val hashB = BleInitiatorElection.nodeIdHash("node-bbb")
        val remoteForA = BleInitiatorElection.hashFromAdvertisement(hashB, serviceData = null)
        val remoteForB = BleInitiatorElection.hashFromAdvertisement(hashA, serviceData = null)
        val aInitiates = BleInitiatorElection.shouldInitiate(hashA, remoteForA)
        val bInitiates = BleInitiatorElection.shouldInitiate(hashB, remoteForB)
        assertTrue(aInitiates xor bInitiates)
        assertArrayEquals(hashB, remoteForA)
        assertArrayEquals(hashA, remoteForB)
    }

    @Test
    fun bundleSignatureAcceptsValidAndRejectsTampered() {
        val keyPair = EccCryptoEngine.generateKeyPair()
        val payload = ChatPayload.encode("field check")
        val signature = EccCryptoEngine.sign(keyPair.private, payload)
        val publicHex = EccCryptoEngine.encodePublicKeyCompressed(keyPair.public)
            .joinToString("") { "%02x".format(it) }
        val signatureHex = signature.joinToString("") { "%02x".format(it) }
        assertTrue(BundleSignatureVerifier.verify(payload, signatureHex, publicHex))
        assertFalse(BundleSignatureVerifier.verify(ChatPayload.encode("tampered"), signatureHex, publicHex))
        assertFalse(BundleSignatureVerifier.verify(payload, signatureHex, null))
        assertFalse(BundleSignatureVerifier.verify(payload, "", publicHex))
    }

    @Test
    fun payloadRouterClassifiesChatSosTelemetryAndMarker() {
        assertEquals(
            BundlePayloadRouter.Kind.CHAT,
            BundlePayloadRouter.classify(ChatPayload.encode("Meet at the gate"))
        )
        val sos = """{"beaconId":"sos-1","senderNodeId":"n1","senderAlias":"Alpha","distressLevel":"SOS_CRITICAL","distressMessage":"help"}"""
        assertEquals(BundlePayloadRouter.Kind.SOS, BundlePayloadRouter.classify(sos.toByteArray()))
        val telemetry = """{"type":"node_telemetry","nodeId":"n1","alias":"Alpha","lat":37.77,"lon":-122.41}"""
        assertEquals(BundlePayloadRouter.Kind.TELEMETRY, BundlePayloadRouter.classify(telemetry.toByteArray()))
        val marker = """{"markerId":"m1","type":"HAZARD","title":"Downed wire","desc":"","lat":37.77,"lon":-122.41,"nodeId":"n1","alias":"Alpha","created":1,"expires":2,"resolved":false}"""
        assertEquals(BundlePayloadRouter.Kind.MARKER, BundlePayloadRouter.classify(marker.toByteArray()))
        assertEquals(BundlePayloadRouter.Kind.UNKNOWN, BundlePayloadRouter.classify("plain text".toByteArray()))
    }
}
