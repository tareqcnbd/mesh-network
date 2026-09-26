package com.example.core.transport.ble

/**
 * Length-aware BLE chunking so a [com.example.core.transport.packet.TransportFrame]
 * larger than the negotiated ATT MTU can still be delivered.
 *
 * Chunk layout:
 * [0xBF][msgId:2][index:1][count:1][payload...]
 */
object BleFrameFramer {
    const val MAGIC: Byte = 0xBF.toByte()
    const val HEADER_SIZE = 5
    const val MIN_CHUNK_PAYLOAD = 1

    fun split(frame: ByteArray, maxChunkBytes: Int): List<ByteArray> {
        val usable = (maxChunkBytes - HEADER_SIZE).coerceAtLeast(MIN_CHUNK_PAYLOAD)
        if (frame.isEmpty()) {
            return listOf(header(msgId = 0, index = 0, count = 1) + frame)
        }
        val count = (frame.size + usable - 1) / usable
        val msgId = (frame.contentHashCode() and 0xFFFF)
        val chunks = ArrayList<ByteArray>(count)
        var offset = 0
        var index = 0
        while (offset < frame.size) {
            val end = (offset + usable).coerceAtMost(frame.size)
            val payload = frame.copyOfRange(offset, end)
            chunks.add(header(msgId, index, count) + payload)
            offset = end
            index++
        }
        return chunks
    }

    fun isChunk(data: ByteArray): Boolean {
        return data.size >= HEADER_SIZE && data[0] == MAGIC
    }

    private fun header(msgId: Int, index: Int, count: Int): ByteArray {
        return byteArrayOf(
            MAGIC,
            ((msgId shr 8) and 0xFF).toByte(),
            (msgId and 0xFF).toByte(),
            (index and 0xFF).toByte(),
            (count and 0xFF).toByte()
        )
    }

    data class Chunk(
        val msgId: Int,
        val index: Int,
        val count: Int,
        val payload: ByteArray
    )

    fun parseChunk(data: ByteArray): Chunk? {
        if (!isChunk(data)) return null
        val msgId = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
        val index = data[3].toInt() and 0xFF
        val count = data[4].toInt() and 0xFF
        if (count <= 0 || index >= count) return null
        return Chunk(msgId, index, count, data.copyOfRange(HEADER_SIZE, data.size))
    }
}

/**
 * Per-peer reassembly of [BleFrameFramer] chunks into a complete transport frame.
 */
class BleReassemblyBuffer {
    private data class Pending(
        val count: Int,
        val parts: Array<ByteArray?>,
        val startedAtMs: Long = System.currentTimeMillis()
    )

    private val pendingByMsgId = HashMap<Int, Pending>()

    fun offer(data: ByteArray): ByteArray? {
        val chunk = BleFrameFramer.parseChunk(data) ?: return if (BleFrameFramer.isChunk(data)) null else data
        val pending = pendingByMsgId.getOrPut(chunk.msgId) {
            Pending(chunk.count, arrayOfNulls(chunk.count))
        }
        if (pending.count != chunk.count) {
            pendingByMsgId.remove(chunk.msgId)
            return offer(data)
        }
        pending.parts[chunk.index] = chunk.payload
        if (pending.parts.any { it == null }) return null
        pendingByMsgId.remove(chunk.msgId)
        val total = pending.parts.sumOf { it!!.size }
        val assembled = ByteArray(total)
        var offset = 0
        for (part in pending.parts) {
            val bytes = part!!
            System.arraycopy(bytes, 0, assembled, offset, bytes.size)
            offset += bytes.size
        }
        return assembled
    }

    fun clear() {
        pendingByMsgId.clear()
    }
}
