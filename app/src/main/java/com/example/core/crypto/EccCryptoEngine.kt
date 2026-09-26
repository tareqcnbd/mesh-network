package com.example.core.crypto

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Standard Elliptic Curve Cryptography engine supporting:
 * - ECDH (secp256r1) key agreement
 * - ECDSA (secp256r1 with SHA-256) signing and verification
 * - AES-256-GCM authenticated encryption with associated data (AEAD)
 * - Compressed EC Point wire encoding/decoding (33 bytes)
 */
object EccCryptoEngine {

    const val CURVE_NAME = "secp256r1"
    private const val ECDSA_ALGORITHM = "SHA256withECDSA"
    private const val KEY_AGREEMENT_ALGORITHM = "ECDH"
    private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_NONCE_LENGTH_BYTES = 12

    private val keyPairGenerator: KeyPairGenerator by lazy {
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec(CURVE_NAME), SecureRandom())
        }
    }

    private val ecKeyFactory: KeyFactory by lazy {
        KeyFactory.getInstance("EC")
    }

    /**
     * Generates a fresh ephemeral or identity secp256r1 EC KeyPair.
     */
    fun generateKeyPair(): KeyPair {
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Performs ECDH shared secret derivation between our private key and peer's public key.
     */
    fun performEcdh(ourPrivateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM)
        keyAgreement.init(ourPrivateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        return keyAgreement.generateSecret()
    }

    /**
     * Signs data with ECDSA SHA256withECDSA.
     */
    fun sign(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val signer = Signature.getInstance(ECDSA_ALGORITHM)
        signer.initSign(privateKey)
        signer.update(data)
        return signer.sign()
    }

    /**
     * Verifies ECDSA signature against public key.
     */
    fun verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val verifier = Signature.getInstance(ECDSA_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(data)
            verifier.verify(signature)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encrypts plaintext using AES-256-GCM with authenticated associated data (AAD).
     * Returns: Nonce (12 bytes) + Ciphertext + Tag (16 bytes).
     */
    fun encryptAesGcm(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray? = null): ByteArray {
        require(key.size == 32) { "AES-256 requires 32-byte key" }
        val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
        SecureRandom().nextBytes(nonce)

        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        associatedData?.let { cipher.updateAAD(it) }
        val ciphertextWithTag = cipher.doFinal(plaintext)

        val result = ByteArray(nonce.size + ciphertextWithTag.size)
        System.arraycopy(nonce, 0, result, 0, nonce.size)
        System.arraycopy(ciphertextWithTag, 0, result, nonce.size, ciphertextWithTag.size)
        return result
    }

    /**
     * Decrypts ciphertext using AES-256-GCM.
     * Expects input format: Nonce (12 bytes) + Ciphertext + Tag (16 bytes).
     */
    fun decryptAesGcm(key: ByteArray, combinedCiphertext: ByteArray, associatedData: ByteArray? = null): ByteArray {
        require(key.size == 32) { "AES-256 requires 32-byte key" }
        require(combinedCiphertext.size >= GCM_NONCE_LENGTH_BYTES + 16) { "Ciphertext too short" }

        val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
        System.arraycopy(combinedCiphertext, 0, nonce, 0, GCM_NONCE_LENGTH_BYTES)

        val ciphertextOffset = GCM_NONCE_LENGTH_BYTES
        val ciphertextLength = combinedCiphertext.size - GCM_NONCE_LENGTH_BYTES

        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        associatedData?.let { cipher.updateAAD(it) }
        return cipher.doFinal(combinedCiphertext, ciphertextOffset, ciphertextLength)
    }

    /**
     * Encodes secp256r1 ECPublicKey into standard 33-byte compressed format:
     * 0x02 / 0x03 prefix + 32-byte X coordinate.
     */
    fun encodePublicKeyCompressed(publicKey: PublicKey): ByteArray {
        val ecKey = publicKey as? ECPublicKey
            ?: throw IllegalArgumentException("Key must be ECPublicKey")
        val x = ecKey.w.affineX.toByteArray().stripLeadingZero(32)
        val y = ecKey.w.affineY
        val prefix: Byte = if (y.testBit(0)) 0x03 else 0x02
        return byteArrayOf(prefix) + x
    }

    /**
     * Decodes 33-byte compressed EC point back to ECPublicKey on secp256r1 curve.
     */
    fun decodePublicKeyCompressed(compressed: ByteArray): PublicKey {
        require(compressed.size == 33) { "Compressed EC key must be 33 bytes" }
        val prefix = compressed[0]
        require(prefix == 0x02.toByte() || prefix == 0x03.toByte()) { "Invalid prefix: $prefix" }

        val xBytes = compressed.copyOfRange(1, 33)
        val x = BigInteger(1, xBytes)

        // secp256r1 field prime p: 2^256 - 2^224 + 2^192 + 2^96 - 1
        val p = BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16)
        // a = -3 mod p
        val a = p.subtract(BigInteger.valueOf(3))
        // b = 0x5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B
        val b = BigInteger("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B", 16)

        // y^2 = x^3 + ax + b (mod p)
        val alpha = x.pow(3).add(a.multiply(x)).add(b).mod(p)

        // Square root modulo p (p = 3 mod 4, so sqrt = alpha^((p+1)/4) mod p)
        val exp = p.add(BigInteger.ONE).divide(BigInteger.valueOf(4))
        var beta = alpha.modPow(exp, p)

        if (beta.pow(2).mod(p) != alpha) {
            throw IllegalArgumentException("Point is not on curve")
        }

        val betaBit = beta.testBit(0)
        val expectedBit = (prefix == 0x03.toByte())
        if (betaBit != expectedBit) {
            beta = p.subtract(beta)
        }

        val point = ECPoint(x, beta)
        val params = getSecp256r1Parameters()
        val pubSpec = ECPublicKeySpec(point, params)
        return ecKeyFactory.generatePublic(pubSpec)
    }

    /**
     * Compute a human-scannable Fingerprint / Node ID from the identity public key.
     * Generates a 16-character hexadecimal fingerprint (e.g., "A1B2:C3D4:E5F6:0789").
     */
    fun computeNodeFingerprint(identityPublicKey: PublicKey): String {
        val encoded = encodePublicKeyCompressed(identityPublicKey)
        val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
        val hex = digest.take(8).joinToString("") { "%02X".format(it) }
        return hex.chunked(4).joinToString(":")
    }

    /**
     * Safety Pad / Strip for 32-byte coordinate arrays.
     */
    private fun ByteArray.stripLeadingZero(expectedSize: Int): ByteArray {
        if (this.size == expectedSize) return this
        if (this.size > expectedSize && this[0] == 0.toByte()) {
            return this.copyOfRange(this.size - expectedSize, this.size)
        }
        if (this.size < expectedSize) {
            val padded = ByteArray(expectedSize)
            System.arraycopy(this, 0, padded, expectedSize - this.size, this.size)
            return padded
        }
        return this
    }

    private fun getSecp256r1Parameters(): ECParameterSpec {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(CURVE_NAME))
        val kp = kpg.generateKeyPair()
        return (kp.public as ECPublicKey).params
    }
}
