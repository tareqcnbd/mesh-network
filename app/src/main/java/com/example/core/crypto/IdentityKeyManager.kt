package com.example.core.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Manages the node's long-term Cryptographic Identity Key (IK).
 * Stored securely inside the Android Keystore hardware (StrongBox backed if available).
 */
class IdentityKeyManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val IDENTITY_KEY_ALIAS = "mesh_identity_secp256r1"
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    /**
     * Retrieves the long-term identity key pair, or generates it within Android Keystore if not present.
     */
    @Synchronized
    fun getOrCreateIdentityKeyPair(): KeyPair {
        if (keyStore.containsAlias(IDENTITY_KEY_ALIAS)) {
            val entry = keyStore.getEntry(IDENTITY_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            if (entry != null) {
                return KeyPair(entry.certificate.publicKey, entry.privateKey)
            }
        }

        return generateHardwareIdentityKeyPair()
    }

    /**
     * Returns the 33-byte compressed identity public key.
     */
    fun getIdentityPublicKeyCompressed(): ByteArray {
        val kp = getOrCreateIdentityKeyPair()
        return EccCryptoEngine.encodePublicKeyCompressed(kp.public)
    }

    /**
     * Returns the formatted fingerprint string (e.g., "ABCD:1234:5678:90EF").
     */
    fun getIdentityFingerprint(): String {
        val kp = getOrCreateIdentityKeyPair()
        return EccCryptoEngine.computeNodeFingerprint(kp.public)
    }

    /**
     * Signs data using the hardware-backed identity private key.
     */
    fun signWithIdentity(data: ByteArray): ByteArray {
        val privateKey = getOrCreateIdentityKeyPair().private
        return EccCryptoEngine.sign(privateKey, data)
    }

    private fun generateHardwareIdentityKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)

        val specBuilder = KeyGenParameterSpec.Builder(
            IDENTITY_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setUserAuthenticationRequired(false)

        try {
            // Attempt to assign StrongBox Hardware Security Module
            specBuilder.setIsStrongBoxBacked(true)
            kpg.initialize(specBuilder.build())
            return kpg.generateKeyPair()
        } catch (e: Exception) {
            // Fall back to standard TEE Android Keystore if StrongBox is unsupported
            specBuilder.setIsStrongBoxBacked(false)
            kpg.initialize(specBuilder.build())
            return kpg.generateKeyPair()
        }
    }
}
