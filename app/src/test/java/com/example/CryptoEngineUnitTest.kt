package com.example

import com.example.core.crypto.EccCryptoEngine
import com.example.core.crypto.Hkdf
import com.example.core.crypto.ratchet.DoubleRatchetSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoEngineUnitTest {

    @Test
    fun testHkdfExtractAndExpand() {
        val ikm = "SecretInputKeyMaterialForTestingMesh".toByteArray(Charsets.UTF_8)
        val salt = "FixedSalt12345678".toByteArray(Charsets.UTF_8)
        val info = "TestInfoVector".toByteArray(Charsets.UTF_8)

        val prk = Hkdf.extract(salt, ikm)
        assertEquals(32, prk.size)

        val okm64 = Hkdf.expand(prk, info, 64)
        assertEquals(64, okm64.size)

        val derived = Hkdf.deriveKey(salt, ikm, info, 32)
        assertEquals(32, derived.size)
        // Verify deterministic behavior
        val derivedAgain = Hkdf.deriveKey(salt, ikm, info, 32)
        assertTrue(derived.contentEquals(derivedAgain))
    }

    @Test
    fun testEccKeyPairGenerationAndPointCompression() {
        val kp = EccCryptoEngine.generateKeyPair()
        val compressed = EccCryptoEngine.encodePublicKeyCompressed(kp.public)
        // secp256r1 compressed EC point is always 33 bytes
        assertEquals(33, compressed.size)
        assertTrue(compressed[0] == 0x02.toByte() || compressed[0] == 0x03.toByte())

        val decompressedPub = EccCryptoEngine.decodePublicKeyCompressed(compressed)
        val recompressed = EccCryptoEngine.encodePublicKeyCompressed(decompressedPub)
        assertTrue(compressed.contentEquals(recompressed))
    }

    @Test
    fun testEcdhSharedSecretAgreement() {
        val aliceKp = EccCryptoEngine.generateKeyPair()
        val bobKp = EccCryptoEngine.generateKeyPair()

        // Alice computes ECDH with Bob's public key
        val aliceShared = EccCryptoEngine.performEcdh(aliceKp.private, bobKp.public)
        // Bob computes ECDH with Alice's public key
        val bobShared = EccCryptoEngine.performEcdh(bobKp.private, aliceKp.public)

        // Both shared secrets must match exactly
        assertTrue(aliceShared.contentEquals(bobShared))
    }

    @Test
    fun testEcdsaSigningAndVerification() {
        val kp = EccCryptoEngine.generateKeyPair()
        val data = "High-priority mesh tactical alert".toByteArray(Charsets.UTF_8)

        val signature = EccCryptoEngine.sign(kp.private, data)
        val valid = EccCryptoEngine.verify(kp.public, data, signature)
        assertTrue(valid)

        // Tamper test
        val tamperedData = "Tampered priority message".toByteArray(Charsets.UTF_8)
        val invalid = EccCryptoEngine.verify(kp.public, tamperedData, signature)
        assertFalse(invalid)
    }

    @Test
    fun testAesGcmAuthenticatedEncryption() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "Encrypted DTN payload block".toByteArray(Charsets.UTF_8)
        val aad = "Source:NodeA|Dest:NodeB".toByteArray(Charsets.UTF_8)

        val combinedCiphertext = EccCryptoEngine.encryptAesGcm(key, plaintext, aad)
        val decrypted = EccCryptoEngine.decryptAesGcm(key, combinedCiphertext, aad)

        assertEquals("Encrypted DTN payload block", String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun testDoubleRatchetForwardSecrecyPingPong() {
        val bobPrekeyKp = EccCryptoEngine.generateKeyPair()
        val masterSecret = ByteArray(32) { (it * 3).toByte() }

        val aliceSession = DoubleRatchetSession.initializeInitiator(
            peerNodeId = "bob_node",
            sharedMasterKey = masterSecret,
            peerRemoteDhPublicKey = bobPrekeyKp.public
        )

        val bobSession = DoubleRatchetSession.initializeReceiver(
            peerNodeId = "alice_node",
            sharedMasterKey = masterSecret,
            ourPreSharedKeyPair = bobPrekeyKp
        )

        // Alice sends message 1
        val msg1 = aliceSession.encrypt("Alice -> Bob message 1".toByteArray(Charsets.UTF_8))
        val plain1 = bobSession.decrypt(msg1)
        assertEquals("Alice -> Bob message 1", String(plain1, Charsets.UTF_8))

        // Bob responds to Alice (triggers asymmetric DH ratchet step)
        val msg2 = bobSession.encrypt("Bob -> Alice reply 2".toByteArray(Charsets.UTF_8))
        val plain2 = aliceSession.decrypt(msg2)
        assertEquals("Bob -> Alice reply 2", String(plain2, Charsets.UTF_8))

        // Alice responds back (second asymmetric DH ratchet step)
        val msg3 = aliceSession.encrypt("Alice -> Bob round 3".toByteArray(Charsets.UTF_8))
        val plain3 = bobSession.decrypt(msg3)
        assertEquals("Alice -> Bob round 3", String(plain3, Charsets.UTF_8))

        // Multiple sequential messages from Alice without Bob response (symmetric ratchet progression)
        val msg4 = aliceSession.encrypt("Alice -> Bob burst 4".toByteArray(Charsets.UTF_8))
        val msg5 = aliceSession.encrypt("Alice -> Bob burst 5".toByteArray(Charsets.UTF_8))

        // Decrypt in order
        assertEquals("Alice -> Bob burst 4", String(bobSession.decrypt(msg4), Charsets.UTF_8))
        assertEquals("Alice -> Bob burst 5", String(bobSession.decrypt(msg5), Charsets.UTF_8))
    }

    @Test
    fun testDoubleRatchetOutOfOrderHandling() {
        val bobPrekeyKp = EccCryptoEngine.generateKeyPair()
        val masterSecret = ByteArray(32) { (it + 7).toByte() }

        val aliceSession = DoubleRatchetSession.initializeInitiator(
            peerNodeId = "bob_node",
            sharedMasterKey = masterSecret,
            peerRemoteDhPublicKey = bobPrekeyKp.public
        )

        val bobSession = DoubleRatchetSession.initializeReceiver(
            peerNodeId = "alice_node",
            sharedMasterKey = masterSecret,
            ourPreSharedKeyPair = bobPrekeyKp
        )

        // Alice sends 3 messages in burst
        val msg0 = aliceSession.encrypt("Packet 0".toByteArray(Charsets.UTF_8))
        val msg1 = aliceSession.encrypt("Packet 1".toByteArray(Charsets.UTF_8))
        val msg2 = aliceSession.encrypt("Packet 2".toByteArray(Charsets.UTF_8))

        // Delivery arrives out-of-order over DTN: msg2 arrives first!
        val plain2 = bobSession.decrypt(msg2)
        assertEquals("Packet 2", String(plain2, Charsets.UTF_8))

        // Then msg0 arrives late from skipped key cache
        val plain0 = bobSession.decrypt(msg0)
        assertEquals("Packet 0", String(plain0, Charsets.UTF_8))

        // Then msg1 arrives late
        val plain1 = bobSession.decrypt(msg1)
        assertEquals("Packet 1", String(plain1, Charsets.UTF_8))
    }
}
