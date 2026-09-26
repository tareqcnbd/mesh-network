package com.example.core.transport.packet

import com.example.core.dtn.model.BundlePriority
import com.example.core.dtn.model.BundleStatus
import com.example.core.dtn.model.DtnBundleEntity

/**
 * Compact binary codec for [DtnBundleEntity] over [TransportOpcode.OP_BUNDLE_COMPLETE].
 */
object BundleCodec {
    private const val VERSION: Byte = 1

    fun encode(bundle: DtnBundleEntity): ByteArray {
        val payload = bundle.encryptedPayloadHex.hexToByteArray()
        val signature = bundle.senderSignatureHex.hexToByteArray()
        val routing = bundle.ephemeralRoutingHeaderHex?.hexToByteArray() ?: ByteArray(0)
        val bundleId = bundle.bundleId.toByteArray(Charsets.UTF_8)
        val source = bundle.sourceNodeId.toByteArray(Charsets.UTF_8)
        val dest = bundle.destinationNodeId.toByteArray(Charsets.UTF_8)
        val size = 1 +
            2 + bundleId.size +
            2 + source.size +
            2 + dest.size +
            1 + 1 + 1 +
            8 + 8 +
            2 + 2 +
            2 + payload.size +
            2 + signature.size +
            2 + routing.size
        val buffer = allocateLe(size)
        buffer.put(VERSION)
        buffer.putLengthPrefixed(bundleId)
        buffer.putLengthPrefixed(source)
        buffer.putLengthPrefixed(dest)
        buffer.put(if (bundle.isBroadcast) 1 else 0)
        buffer.put(bundle.priority.ordinal.toByte())
        buffer.put(bundle.hopCount.toByte())
        buffer.putLong(bundle.createdAtEpochMs)
        buffer.putLong(bundle.expiresAtEpochMs)
        buffer.putShort(bundle.maxHops.toShort())
        buffer.putShort(0) // reserved
        buffer.putLengthPrefixed(payload)
        buffer.putLengthPrefixed(signature)
        buffer.putLengthPrefixed(routing)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): DtnBundleEntity? {
        return try {
            if (bytes.isEmpty()) return null
            val buffer = wrapBe(bytes)
            val version = buffer.get()
            if (version != VERSION) return null
            val bundleId = buffer.getLengthPrefixedUtf8()
            val source = buffer.getLengthPrefixedUtf8()
            val dest = buffer.getLengthPrefixedUtf8()
            val isBroadcast = buffer.get().toInt() != 0
            val priorityOrdinal = buffer.get().toInt() and 0xFF
            val hopCount = buffer.get().toInt() and 0xFF
            val created = buffer.long
            val expires = buffer.long
            val maxHops = buffer.short.toInt() and 0xFFFF
            buffer.short // reserved
            val payload = buffer.getLengthPrefixed()
            val signature = buffer.getLengthPrefixed()
            val routing = if (buffer.remaining() >= 2) buffer.getLengthPrefixed() else ByteArray(0)
            val priority = BundlePriority.entries.getOrElse(priorityOrdinal) { BundlePriority.NORMAL }
            DtnBundleEntity(
                bundleId = bundleId,
                sourceNodeId = source,
                destinationNodeId = dest,
                isBroadcast = isBroadcast,
                priority = priority,
                status = BundleStatus.PENDING_CARRIED,
                createdAtEpochMs = created,
                expiresAtEpochMs = expires,
                hopCount = hopCount,
                maxHops = maxHops,
                payloadSizeBytes = payload.size.toLong(),
                encryptedPayloadHex = payload.toHexString(),
                senderSignatureHex = signature.toHexString(),
                ephemeralRoutingHeaderHex = routing.takeIf { it.isNotEmpty() }?.toHexString()
            )
        } catch (_: Exception) {
            null
        }
    }
}
