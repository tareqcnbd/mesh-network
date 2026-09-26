package com.example.core.reputation.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.reputation.model.TrustTier

/**
 * Persisted reputation record for each observed mesh node.
 */
@Entity(
    tableName = "peer_reputations",
    indices = [
        Index(value = ["peerNodeId"], unique = true),
        Index(value = ["reputationScore"]),
        Index(value = ["isQuarantined"])
    ]
)
data class PeerReputationEntity(
    @PrimaryKey
    val peerNodeId: String,
    val peerAlias: String,
    val reputationScore: Int = 50, // Default 50 (Neutral)
    val trustTier: String = TrustTier.NEUTRAL_VERIFIED.name,
    val validDeliveriesCount: Int = 0,
    val verifiedDelAcksCount: Int = 0,
    val verifiedPowTokensCount: Int = 0,
    val rateLimitViolationsCount: Int = 0,
    val replayFloodViolationsCount: Int = 0,
    val invalidSignatureCount: Int = 0,
    val isQuarantined: Boolean = false,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis(),
    val quarantineReason: String? = null
)

/**
 * Security audit trail entry for peer score changes and attack detections.
 */
@Entity(
    tableName = "reputation_audit_logs",
    indices = [
        Index(value = ["targetPeerId"]),
        Index(value = ["timestampEpochMs"])
    ]
)
data class ReputationAuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val logId: Long = 0,
    val targetPeerId: String,
    val eventType: String,
    val scoreDelta: Int,
    val newScore: Int,
    val reasonDescription: String,
    val timestampEpochMs: Long = System.currentTimeMillis()
)
