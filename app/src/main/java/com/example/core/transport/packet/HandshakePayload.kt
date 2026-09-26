package com.example.core.transport.packet

/**
 * Identity handshake carried in [TransportOpcode.OP_HANDSHAKE_IK].
 *
 * Wire format (big-endian):
 * [version:1][nodeId][alias][identityPub:33][ephemeralPub:33][signature]
 * Strings and the signature are length-prefixed (uint16).
 */
data class HandshakePayload(
    val nodeId: String,
    val alias: String,
    val identityPublicKey: ByteArray,
    val ephemeralPublicKey: ByteArray,
    val signature: ByteArray
) {
    fun toByteArray(): ByteArray {
        require(identityPublicKey.size == 33) { "Identity public key must be 33 compressed bytes" }
        require(ephemeralPublicKey.size == 33) { "Ephemeral public key must be 33 compressed bytes" }
        val nodeIdBytes = nodeId.toByteArray(Charsets.UTF_8)
        val aliasBytes = alias.toByteArray(Charsets.UTF_8)
        val size = 1 + 2 + nodeIdBytes.size + 2 + aliasBytes.size + 33 + 33 + 2 + signature.size
        val buffer = allocateLe(size)
        buffer.put(VERSION)
        buffer.putLengthPrefixed(nodeIdBytes)
        buffer.putLengthPrefixed(aliasBytes)
        buffer.put(identityPublicKey)
        buffer.put(ephemeralPublicKey)
        buffer.putLengthPrefixed(signature)
        return buffer.array()
    }

    /**
     * Canonical bytes that the identity key must sign (everything except the signature itself).
     */
    fun signedBytes(): ByteArray = signedBytes(nodeId, alias, identityPublicKey, ephemeralPublicKey)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HandshakePayload) return false
        return nodeId == other.nodeId &&
            alias == other.alias &&
            identityPublicKey.contentEquals(other.identityPublicKey) &&
            ephemeralPublicKey.contentEquals(other.ephemeralPublicKey) &&
            signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int {
        var result = nodeId.hashCode()
        result = 31 * result + alias.hashCode()
        result = 31 * result + identityPublicKey.contentHashCode()
        result = 31 * result + ephemeralPublicKey.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }

    companion object {
        const val VERSION: Byte = 1

        fun signedBytes(
            nodeId: String,
            alias: String,
            identityPublicKey: ByteArray,
            ephemeralPublicKey: ByteArray
        ): ByteArray {
            val nodeIdBytes = nodeId.toByteArray(Charsets.UTF_8)
            val aliasBytes = alias.toByteArray(Charsets.UTF_8)
            val size = 1 + 2 + nodeIdBytes.size + 2 + aliasBytes.size + 33 + 33
            val buffer = allocateLe(size)
            buffer.put(VERSION)
            buffer.putLengthPrefixed(nodeIdBytes)
            buffer.putLengthPrefixed(aliasBytes)
            buffer.put(identityPublicKey)
            buffer.put(ephemeralPublicKey)
            return buffer.array()
        }

        fun fromByteArray(bytes: ByteArray): HandshakePayload? {
            return try {
                if (bytes.isEmpty()) return null
                val buffer = wrapBe(bytes)
                val version = buffer.get()
                if (version != VERSION) return null
                val nodeId = buffer.getLengthPrefixedUtf8()
                val alias = buffer.getLengthPrefixedUtf8()
                if (buffer.remaining() < 66) return null
                val identity = ByteArray(33)
                buffer.get(identity)
                val ephemeral = ByteArray(33)
                buffer.get(ephemeral)
                val signature = buffer.getLengthPrefixed()
                HandshakePayload(
                    nodeId = nodeId,
                    alias = alias,
                    identityPublicKey = identity,
                    ephemeralPublicKey = ephemeral,
                    signature = signature
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
