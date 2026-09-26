package com.example.core.dtn.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a store-carry-and-forward bundle in encrypted local persistence.
 */
@Entity(
    tableName = "dtn_bundles",
    indices = [
        Index(value = ["bundleId"], unique = true),
        Index(value = ["destinationNodeId"]),
        Index(value = ["status"]),
        Index(value = ["expiresAtEpochMs"])
    ]
)
data class DtnBundleEntity(
    @PrimaryKey
    val bundleId: String,
    val sourceNodeId: String,
    val destinationNodeId: String,
    val isBroadcast: Boolean = false,
    val priority: BundlePriority = BundlePriority.NORMAL,
    val status: BundleStatus = BundleStatus.PENDING_CARRIED,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val expiresAtEpochMs: Long,
    val hopCount: Int = 0,
    val maxHops: Int = 16,
    val payloadSizeBytes: Long,
    val encryptedPayloadHex: String,
    val senderSignatureHex: String,
    val ephemeralRoutingHeaderHex: String? = null
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAtEpochMs
}
