package com.example.core.media.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaTransferDao {

    @Query("SELECT * FROM media_transfers ORDER BY createdAtMs DESC")
    fun getAllTransfersFlow(): Flow<List<MediaTransferEntity>>

    @Query("SELECT * FROM media_transfers WHERE transferId = :transferId LIMIT 1")
    suspend fun getTransferById(transferId: String): MediaTransferEntity?

    @Query("SELECT * FROM media_transfers WHERE status IN ('TRANSFERRING', 'PENDING') ORDER BY createdAtMs ASC")
    suspend fun getActiveTransfers(): List<MediaTransferEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTransfer(transfer: MediaTransferEntity)

    @Query("""
        UPDATE media_transfers 
        SET verifiedChunksCount = :verifiedCount, 
            transferredSizeBytes = :transferredBytes, 
            bitfieldHex = :bitfieldHex, 
            status = :status,
            completedAtMs = :completedAtMs
        WHERE transferId = :transferId
    """)
    suspend fun updateTransferProgress(
        transferId: String,
        verifiedCount: Int,
        transferredBytes: Long,
        bitfieldHex: String,
        status: String,
        completedAtMs: Long? = null
    )

    @Query("UPDATE media_transfers SET status = :status WHERE transferId = :transferId")
    suspend fun updateTransferStatus(transferId: String, status: String)

    @Query("DELETE FROM media_transfers WHERE transferId = :transferId")
    suspend fun deleteTransferById(transferId: String)

    @Query("SELECT * FROM media_chunks WHERE transferId = :transferId ORDER BY chunkIndex ASC")
    fun getChunksFlowForTransfer(transferId: String): Flow<List<MediaChunkEntity>>

    @Query("SELECT * FROM media_chunks WHERE transferId = :transferId ORDER BY chunkIndex ASC")
    suspend fun getChunksForTransfer(transferId: String): List<MediaChunkEntity>

    @Query("SELECT * FROM media_chunks WHERE transferId = :transferId AND chunkIndex = :chunkIndex LIMIT 1")
    suspend fun getChunk(transferId: String, chunkIndex: Int): MediaChunkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateChunk(chunk: MediaChunkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllChunks(chunks: List<MediaChunkEntity>)

    @Query("DELETE FROM media_chunks WHERE transferId = :transferId")
    suspend fun deleteChunksForTransfer(transferId: String)

    @Transaction
    suspend fun deleteTransferAndChunks(transferId: String) {
        deleteChunksForTransfer(transferId)
        deleteTransferById(transferId)
    }
}
