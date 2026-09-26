package com.example.core.crypto

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 5869 HMAC-based Extract-and-Expand Key Derivation Function (HKDF)
 * implemented natively using HmacSHA256.
 */
object Hkdf {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val HASH_OUTPUT_SIZE = 32 // SHA-256 outputs 32 bytes

    /**
     * HKDF-Extract(salt, IKM) -> PRK
     */
    fun extract(salt: ByteArray?, ikm: ByteArray): ByteArray {
        val actualSalt = if (salt == null || salt.isEmpty()) ByteArray(HASH_OUTPUT_SIZE) else salt
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(actualSalt, HMAC_ALGORITHM))
        return mac.doFinal(ikm)
    }

    /**
     * HKDF-Expand(PRK, info, L) -> OKM
     */
    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length <= 255 * HASH_OUTPUT_SIZE) { "Requested HKDF length exceeds maximum allowed" }

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(prk, HMAC_ALGORITHM))

        val okm = ByteArray(length)
        var t = ByteArray(0)
        var generatedBytes = 0
        var iteration: Byte = 1

        while (generatedBytes < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(iteration)
            t = mac.doFinal()

            val bytesToCopy = minOf(t.size, length - generatedBytes)
            System.arraycopy(t, 0, okm, generatedBytes, bytesToCopy)
            generatedBytes += bytesToCopy
            iteration++
        }

        return okm
    }

    /**
     * Complete HKDF: Extract then Expand.
     */
    fun deriveKey(salt: ByteArray?, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = extract(salt, ikm)
        return expand(prk, info, length)
    }

    /**
     * Compute HMAC-SHA256 for symmetric ratchet step.
     */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return mac.doFinal(data)
    }

    /**
     * SHA-256 digest helper.
     */
    fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }
}
