package com.example.core.transport.packet

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

fun String.hexToByteArray(): ByteArray {
    val cleaned = trim()
    if (cleaned.isEmpty()) return ByteArray(0)
    val even = if (cleaned.length % 2 == 0) cleaned else "0$cleaned"
    return ByteArray(even.length / 2) { i ->
        even.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

internal fun ByteBuffer.putLengthPrefixed(bytes: ByteArray) {
    putShort(bytes.size.toShort())
    put(bytes)
}

internal fun ByteBuffer.putLengthPrefixedUtf8(value: String) {
    putLengthPrefixed(value.toByteArray(StandardCharsets.UTF_8))
}

internal fun ByteBuffer.getLengthPrefixed(): ByteArray {
    val length = short.toInt() and 0xFFFF
    require(remaining() >= length) { "Truncated length-prefixed field" }
    val bytes = ByteArray(length)
    get(bytes)
    return bytes
}

internal fun ByteBuffer.getLengthPrefixedUtf8(): String {
    return String(getLengthPrefixed(), StandardCharsets.UTF_8)
}

internal fun allocateLe(capacity: Int): ByteBuffer {
    return ByteBuffer.allocate(capacity).order(ByteOrder.BIG_ENDIAN)
}

internal fun wrapBe(bytes: ByteArray): ByteBuffer {
    return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
}
