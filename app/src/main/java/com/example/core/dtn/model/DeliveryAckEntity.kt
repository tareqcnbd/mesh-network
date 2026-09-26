package com.example.core.dtn.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cryptographically signed Delivery Acknowledgment (DelAck).
 * Propagated through epidemic flooding to prune carried bundles across all intermediate nodes.
 */
@Entity(
    tableName = "delivery_acknowledgments",
    indices = [
        Index(value = ["bundleId"], unique = true),
        Index(value = ["destinationNodeId"])
    ]
)
data class DeliveryAckEntity(
    @PrimaryKey
    val bundleId: String,
    val destinationNodeId: String,
    val receivedTimestampEpochMs: Long = System.currentTimeMillis(),
    val recipientSignatureHex: String
)
