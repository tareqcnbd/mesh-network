package com.example.core.reputation.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerReputationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(reputation: PeerReputationEntity)

    @Update
    suspend fun update(reputation: PeerReputationEntity)

    @Query("SELECT * FROM peer_reputations WHERE peerNodeId = :peerId LIMIT 1")
    suspend fun getReputationByPeerId(peerId: String): PeerReputationEntity?

    @Query("SELECT * FROM peer_reputations ORDER BY reputationScore DESC")
    fun observeAllReputations(): Flow<List<PeerReputationEntity>>

    @Query("SELECT * FROM peer_reputations WHERE isQuarantined = 1")
    fun observeQuarantinedPeers(): Flow<List<PeerReputationEntity>>

    @Query("SELECT COUNT(*) FROM peer_reputations WHERE isQuarantined = 1")
    fun observeQuarantinedCount(): Flow<Int>

    @Query("UPDATE peer_reputations SET isQuarantined = :quarantined, quarantineReason = :reason WHERE peerNodeId = :peerId")
    suspend fun setQuarantineStatus(peerId: String, quarantined: Boolean, reason: String?)

    @Insert
    suspend fun insertAuditLog(log: ReputationAuditLogEntity): Long

    @Query("SELECT * FROM reputation_audit_logs ORDER BY timestampEpochMs DESC LIMIT :limit")
    fun observeRecentAuditLogs(limit: Int = 50): Flow<List<ReputationAuditLogEntity>>

    @Query("SELECT * FROM reputation_audit_logs WHERE targetPeerId = :peerId ORDER BY timestampEpochMs DESC LIMIT :limit")
    fun observePeerAuditLogs(peerId: String, limit: Int = 30): Flow<List<ReputationAuditLogEntity>>

    @Query("DELETE FROM reputation_audit_logs")
    suspend fun clearAuditLogs()
}
