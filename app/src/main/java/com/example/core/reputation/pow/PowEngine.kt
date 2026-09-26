package com.example.core.reputation.pow

import com.example.core.reputation.model.PowChallenge
import com.example.core.reputation.model.PowSolutionToken
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * High-performance Hashcash-style Proof-of-Work solver and verifier.
 * Designed for low-power mobile nodes:
 * - Solves lightweight puzzles (8-16 bits) in single-digit to tens of milliseconds.
 * - Verification is instantaneous O(1) single SHA-256 calculation.
 */
object PowEngine {

    private val secureRandom = SecureRandom()

    /**
     * Creates a fresh challenge tailored to the peer's current trust tier.
     * Trusted peers get lightweight puzzle (e.g., 6-8 bits), while suspicious peers get heavier puzzle (e.g., 14-16 bits).
     */
    fun createChallenge(targetPeerId: String, difficultyBits: Int): PowChallenge {
        val nonce = ByteArray(16)
        secureRandom.nextBytes(nonce)
        return PowChallenge(
            challengeId = UUID.randomUUID().toString().substring(0, 8),
            targetPeerId = targetPeerId,
            difficultyLeadingZeroBits = difficultyBits.coerceIn(4, 24),
            challengeNonce = nonce
        )
    }

    /**
     * Solves the Proof-of-Work challenge by incrementing a 64-bit nonce until
     * SHA-256(challengeNonce + solverNodeId + challengeId + nonce) has the required leading zero bits.
     */
    fun solve(challenge: PowChallenge, solverNodeId: String, maxIterations: Long = 2_000_000L): PowSolutionToken? {
        val startTime = System.currentTimeMillis()
        val md = MessageDigest.getInstance("SHA-256")
        val fixedPrefix = ByteBuffer.allocate(challenge.challengeNonce.size + solverNodeId.length + challenge.challengeId.length)
            .put(challenge.challengeNonce)
            .put(solverNodeId.toByteArray(Charsets.UTF_8))
            .put(challenge.challengeId.toByteArray(Charsets.UTF_8))
            .array()

        var nonce = 0L
        val nonceBuffer = ByteBuffer.allocate(8)

        while (nonce < maxIterations) {
            md.reset()
            md.update(fixedPrefix)
            nonceBuffer.clear()
            nonceBuffer.putLong(nonce)
            md.update(nonceBuffer.array())
            val digest = md.digest()

            if (hasLeadingZeroBits(digest, challenge.difficultyLeadingZeroBits)) {
                val computationTime = System.currentTimeMillis() - startTime
                return PowSolutionToken(
                    challengeId = challenge.challengeId,
                    solverNodeId = solverNodeId,
                    solutionNonce = nonce,
                    computedHashHex = digest.joinToString("") { "%02x".format(it) },
                    computationTimeMs = computationTime
                )
            }
            nonce++
        }
        return null // Timeout or exceeded max iterations
    }

    /**
     * Instantaneous O(1) verification of a submitted solution token against a challenge.
     */
    fun verify(challenge: PowChallenge, token: PowSolutionToken): Boolean {
        if (challenge.challengeId != token.challengeId) return false
        if (challenge.isExpired) return false

        val md = MessageDigest.getInstance("SHA-256")
        val fixedPrefix = ByteBuffer.allocate(challenge.challengeNonce.size + token.solverNodeId.length + challenge.challengeId.length)
            .put(challenge.challengeNonce)
            .put(token.solverNodeId.toByteArray(Charsets.UTF_8))
            .put(challenge.challengeId.toByteArray(Charsets.UTF_8))
            .array()

        md.update(fixedPrefix)
        val nonceBuffer = ByteBuffer.allocate(8)
        nonceBuffer.putLong(token.solutionNonce)
        md.update(nonceBuffer.array())
        val digest = md.digest()

        if (!hasLeadingZeroBits(digest, challenge.difficultyLeadingZeroBits)) {
            return false
        }

        val calculatedHex = digest.joinToString("") { "%02x".format(it) }
        return calculatedHex.equals(token.computedHashHex, ignoreCase = true)
    }

    /**
     * Checks if the byte array starts with at least [bits] leading zero bits.
     */
    fun hasLeadingZeroBits(bytes: ByteArray, bits: Int): Boolean {
        var remainingBits = bits
        for (b in bytes) {
            val unsigned = b.toInt() and 0xFF
            if (remainingBits >= 8) {
                if (unsigned != 0) return false
                remainingBits -= 8
            } else if (remainingBits > 0) {
                val mask = (0xFF shl (8 - remainingBits)) and 0xFF
                if ((unsigned and mask) != 0) return false
                remainingBits = 0
                break
            } else {
                break
            }
        }
        return remainingBits == 0
    }
}
