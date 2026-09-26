package com.example.core.dtn.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.dtn.model.DeliveryAckEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryAckDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAck(ack: DeliveryAckEntity): Long

    @Query("SELECT * FROM delivery_acknowledgments WHERE bundleId = :bundleId LIMIT 1")
    suspend fun getAckForBundle(bundleId: String): DeliveryAckEntity?

    @Query("SELECT bundleId FROM delivery_acknowledgments")
    suspend fun getAllAckBundleIds(): List<String>

    @Query("SELECT * FROM delivery_acknowledgments ORDER BY receivedTimestampEpochMs DESC")
    fun observeAllAcks(): Flow<List<DeliveryAckEntity>>

    @Query("SELECT COUNT(*) FROM delivery_acknowledgments")
    fun observeAckCount(): Flow<Int>
}
