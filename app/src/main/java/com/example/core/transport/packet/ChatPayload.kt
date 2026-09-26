package com.example.core.transport.packet

/**
 * Application payload inside a DTN bundle that represents a chat message.
 */
object ChatPayload {
    const val PREFIX = "CHAT\n"

    fun encode(plaintext: String): ByteArray {
        return (PREFIX + plaintext).toByteArray(Charsets.UTF_8)
    }

    fun isChat(bytes: ByteArray): Boolean {
        val prefix = PREFIX.toByteArray(Charsets.UTF_8)
        if (bytes.size < prefix.size) return false
        return bytes.copyOfRange(0, prefix.size).contentEquals(prefix)
    }

    fun decode(bytes: ByteArray): String? {
        if (!isChat(bytes)) return null
        val prefixSize = PREFIX.toByteArray(Charsets.UTF_8).size
        return bytes.decodeToString(startIndex = prefixSize)
    }
}
