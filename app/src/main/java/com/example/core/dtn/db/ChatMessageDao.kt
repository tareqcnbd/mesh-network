package com.example.core.dtn.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.dtn.model.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE peerNodeId = :peerNodeId ORDER BY createdAtEpochMs ASC")
    fun observeConversation(peerNodeId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY createdAtEpochMs DESC")
    fun observeAllMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY createdAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 8): Flow<List<ChatMessageEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM chat_messages WHERE bundleId = :bundleId)")
    suspend fun existsForBundle(bundleId: String): Boolean

    @Query("UPDATE chat_messages SET deliveryCaption = :caption WHERE bundleId = :bundleId")
    suspend fun updateCaption(bundleId: String, caption: String)
}
