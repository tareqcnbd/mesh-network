package com.example.core.dtn.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Neighbor node contact encounter history and anti-entropy vector.
 */
@Entity(
    tableName = "mesh_peers",
    indices = [
        Index(value = ["nodeId"], unique = true),
        Index(value = ["lastSeenEpochMs"])
    ]
)
data class MeshPeerEntity(
    @PrimaryKey
    val nodeId: String,
    val alias: String,
    val lastSeenEpochMs: Long = System.currentTimeMillis(),
    val rssiDbm: Int = -100,
    val directLinkType: String = "BLE",
    val distanceHops: Int = 1,
    val bundlesTransferredToCount: Int = 0,
    val bundlesReceivedFromCount: Int = 0,
    val isDirectNeighbor: Boolean = true,
    val identityPublicKeyHex: String? = null
)
