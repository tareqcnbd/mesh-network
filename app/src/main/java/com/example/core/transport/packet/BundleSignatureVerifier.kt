package com.example.core.transport.packet

import com.example.core.crypto.EccCryptoEngine

/**
 * Verifies a DTN bundle payload against the sender's identity public key.
 */
object BundleSignatureVerifier {
    fun verify(
        payload: ByteArray,
        signatureHex: String,
        identityPublicKeyHex: String?
    ): Boolean {
        if (identityPublicKeyHex.isNullOrBlank() || signatureHex.isBlank()) {
            return false
        }
        return try {
            val publicKey = EccCryptoEngine.decodePublicKeyCompressed(identityPublicKeyHex.hexToByteArray())
            val signature = signatureHex.hexToByteArray()
            EccCryptoEngine.verify(publicKey, payload, signature)
        } catch (_: Exception) {
            false
        }
    }
}
