package com.example.core.crypto.ratchet

/**
 * Message payload carrying ephemeral DH key, counter numbers, and ciphertext.
 */
data class RatchetMessage(
    val ephemeralPublicKeyBytes: ByteArray,
    val sequenceNumber: Int,
    val previousChainLength: Int,
    val ciphertext: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RatchetMessage) return false
        if (!ephemeralPublicKeyBytes.contentEquals(other.ephemeralPublicKeyBytes)) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (previousChainLength != other.previousChainLength) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = ephemeralPublicKeyBytes.contentHashCode()
        result = 31 * result + sequenceNumber
        result = 31 * result + previousChainLength
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}

/**
 * Snapshot of the current ratchet session metrics.
 */
data class RatchetSessionState(
    val peerNodeId: String,
    val sendSequenceNumber: Int,
    val receiveSequenceNumber: Int,
    val skippedKeysCount: Int,
    val rootKeyDigestHex: String,
    val lastRatchetStepEpochMs: Long
)
