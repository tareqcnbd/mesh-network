package com.example.core.media.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.media.model.ChunkInfo
import com.example.core.media.model.ChunkStatus
import com.example.core.media.model.MediaType
import com.example.core.media.model.TransferDirection
import com.example.core.media.model.TransferStatus

@Entity(
    tableName = "media_transfers",
    indices = [
        Index(value = ["status"]),
        Index(value = ["direction"]),
        Index(value = ["createdAtMs"])
    ]
)
data class MediaTransferEntity(
    @PrimaryKey
    val transferId: String,
    val fileName: String,
    val mediaType: String,
    val direction: String,
    val senderNodeId: String,
    val senderAlias: String,
    val receiverNodeId: String,
    val originalSizeBytes: Long,
    val transferredSizeBytes: Long,
    val isCompressed: Boolean,
    val sha256FileHash: String,
    val chunkSizeBytes: Int,
    val totalChunks: Int,
    val verifiedChunksCount: Int,
    val bitfieldHex: String,
    val status: String,
    val activeTier: String,
    val createdAtMs: Long,
    val completedAtMs: Long? = null
) {
    val transferDirection: TransferDirection
        get() = try { TransferDirection.valueOf(direction) } catch (e: Exception) { TransferDirection.OUTGOING }

    val transferStatus: TransferStatus
        get() = try { TransferStatus.valueOf(status) } catch (e: Exception) { TransferStatus.PENDING }

    val parsedMediaType: MediaType
        get() = try { MediaType.valueOf(mediaType) } catch (e: Exception) { MediaType.DOCUMENT }

    val progressFraction: Float
        get() = if (totalChunks <= 0) 0f else (verifiedChunksCount.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f)

    val progressPercentage: Int
        get() = (progressFraction * 100).toInt()
}

@Entity(
    tableName = "media_chunks",
    indices = [
        Index(value = ["transferId"]),
        Index(value = ["transferId", "chunkIndex"], unique = true)
    ]
)
data class MediaChunkEntity(
    @PrimaryKey
    val chunkId: String, // "$transferId-$chunkIndex"
    val transferId: String,
    val chunkIndex: Int,
    val chunkSizeBytes: Int,
    val sha256Hash: String,
    val isVerified: Boolean,
    val dataHex: String? = null
) {
    fun toChunkInfo(): ChunkInfo {
        return ChunkInfo(
            index = chunkIndex,
            offsetBytes = (chunkIndex.toLong() * chunkSizeBytes),
            sizeBytes = chunkSizeBytes,
            sha256Hash = sha256Hash,
            status = if (isVerified) ChunkStatus.VERIFIED else ChunkStatus.RECEIVED
        )
    }
}
