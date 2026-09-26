package com.example.core.dtn.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.dtn.model.MeshPeerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeshPeerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePeer(peer: MeshPeerEntity)

    @Update
    suspend fun updatePeer(peer: MeshPeerEntity)

    @Query("SELECT * FROM mesh_peers WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getPeerByNodeId(nodeId: String): MeshPeerEntity?

    @Query("SELECT * FROM mesh_peers ORDER BY lastSeenEpochMs DESC")
    fun observeAllPeers(): Flow<List<MeshPeerEntity>>

    @Query("SELECT * FROM mesh_peers WHERE isDirectNeighbor = 1 ORDER BY lastSeenEpochMs DESC")
    fun observeDirectNeighbors(): Flow<List<MeshPeerEntity>>

    @Query("UPDATE mesh_peers SET isDirectNeighbor = 0 WHERE lastSeenEpochMs < :cutoffTime")
    suspend fun markStaleNeighbors(cutoffTime: Long): Int

    @Query("SELECT COUNT(*) FROM mesh_peers WHERE isDirectNeighbor = 1")
    fun observeDirectNeighborCount(): Flow<Int>
}
