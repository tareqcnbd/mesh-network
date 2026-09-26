package com.example.core.crypto

import java.security.KeyPair
import java.security.PublicKey

/**
 * Derives a per-neighbor AES-256 session key from ephemeral ECDH after identity handshake.
 * Long-term Android Keystore identity keys are SIGN-only and cannot perform ECDH.
 */
object MeshSessionCrypto {
    private val SALT = "MonrMeshHandshakeV1".toByteArray(Charsets.UTF_8)
    private val INFO = "MonrMeshSessionKey".toByteArray(Charsets.UTF_8)

    fun deriveSessionKey(ourEphemeral: KeyPair, peerEphemeralPublic: PublicKey): ByteArray {
        val shared = EccCryptoEngine.performEcdh(ourEphemeral.private, peerEphemeralPublic)
        return Hkdf.deriveKey(salt = SALT, ikm = shared, info = INFO, length = 32)
    }
}
