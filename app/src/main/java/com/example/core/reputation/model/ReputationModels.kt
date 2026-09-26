package com.example.core.reputation.model

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Trust Tier levels calculated from multidimensional peer interactions:
 * - Direct valid bundle deliveries
 * - Verified DelAcks
 * - Cryptographic signature verifications
 * - Rate limit violations and replay attacks
 */
enum class TrustTier(val minScore: Int, val label: String) {
    TRUSTED_CORE(80, "Trusted Core"),
    NEUTRAL_VERIFIED(50, "Neutral Verified"),
    SUSPICIOUS(20, "Suspicious"),
    QUARANTINED(0, "Quarantined (Blacklisted)");

    companion object {
        fun fromScore(score: Int): TrustTier {
            val clamped = score.coerceIn(0, 100)
            return when {
                clamped >= TRUSTED_CORE.minScore -> TRUSTED_CORE
                clamped >= NEUTRAL_VERIFIED.minScore -> NEUTRAL_VERIFIED
                clamped >= SUSPICIOUS.minScore -> SUSPICIOUS
                else -> QUARANTINED
            }
        }
    }
}

/**
 * Reason codes for reputation score adjustments and security audit trails.
 */
enum class ReputationEvent(val scoreDelta: Int, val description: String) {
    BUNDLE_DELIVERED_VALID(+5, "Valid bundle relayed & accepted"),
    DELACK_CONFIRMED(+8, "Signed delivery acknowledgment verified"),
    POW_TOKEN_VERIFIED(+3, "Valid rate-limiting PoW token submitted"),
    ATTESTATION_POSITIVE(+2, "Neighbor peer positive gossip attestation"),

    // Penalties
    RATE_LIMIT_EXCEEDED(-10, "Transmission burst exceeded token bucket"),
    DUPLICATE_FLOOD_ATTEMPT(-15, "Replay / duplicate bundle flooding detected"),
    INVALID_SIGNATURE(-25, "Cryptographic signature validation failure"),
    MALFORMED_CHUNK(-20, "Corrupted SHA-256 chunk hash or framing breach"),
    QUARANTINE_OVERRIDE(-50, "Direct node blacklisting / manual isolation");
}

/**
 * Dynamic Proof-of-Work puzzle challenge and verification token.
 * Uses Hashcash-style leading zero nibbles / bits over SHA-256(challengeNonce + peerNodeId + timestamp + solutionNonce).
 */
data class PowChallenge(
    val challengeId: String,
    val targetPeerId: String,
    val difficultyLeadingZeroBits: Int, // e.g. 8 to 16 bits (adaptive based on trust tier)
    val challengeNonce: ByteArray,
    val issuedEpochMs: Long = System.currentTimeMillis(),
    val expiryEpochMs: Long = System.currentTimeMillis() + 60_000L // 60s validity window
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiryEpochMs

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PowChallenge
        if (challengeId != other.challengeId) return false
        if (targetPeerId != other.targetPeerId) return false
        if (difficultyLeadingZeroBits != other.difficultyLeadingZeroBits) return false
        if (!challengeNonce.contentEquals(other.challengeNonce)) return false
        if (issuedEpochMs != other.issuedEpochMs) return false
        if (expiryEpochMs != other.expiryEpochMs) return false
        return true
    }

    override fun hashCode(): Int {
        var result = challengeId.hashCode()
        result = 31 * result + targetPeerId.hashCode()
        result = 31 * result + difficultyLeadingZeroBits
        result = 31 * result + challengeNonce.contentHashCode()
        result = 31 * result + issuedEpochMs.hashCode()
        result = 31 * result + expiryEpochMs.hashCode()
        return result
    }
}

/**
 * Proof-of-Work Solution Token submitted by a sender to satisfy anti-spam rate limiting.
 */
data class PowSolutionToken(
    val challengeId: String,
    val solverNodeId: String,
    val solutionNonce: Long,
    val computedHashHex: String,
    val computationTimeMs: Long
)

/**
 * Gossip Attestation: A signed peer rating shared over the mesh to reach decentralized consensus on bad actors.
 */
data class ReputationAttestation(
    val reporterNodeId: String,
    val targetNodeId: String,
    val scoreDelta: Int,
    val reason: String,
    val timestampEpochMs: Long,
    val signatureHex: String = ""
) {
    fun toSigningPayload(): ByteArray {
        return "$reporterNodeId:$targetNodeId:$scoreDelta:$reason:$timestampEpochMs".toByteArray(Charsets.UTF_8)
    }
}
