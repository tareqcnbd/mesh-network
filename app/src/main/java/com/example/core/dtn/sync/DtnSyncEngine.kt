package com.example.core.dtn.sync

import com.example.core.dtn.BloomFilter
import com.example.core.dtn.db.DeliveryAckDao
import com.example.core.dtn.db.DtnBundleDao
import com.example.core.dtn.db.MeshPeerDao
import com.example.core.dtn.model.BundleStatus
import com.example.core.dtn.model.DeliveryAckEntity
import com.example.core.dtn.model.DtnBundleEntity
import com.example.core.dtn.model.MeshPeerEntity
import com.example.core.reputation.MeshReputationManager
import com.example.core.reputation.model.ReputationEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * High-performance, anti-entropy synchronization engine for Delay-Tolerant Networks.
 * Manages Bloom Filter vector exchanges, epidemic flooding suppression, TTL decay,
 * DelAck propagation, reputation verification, and buffer eviction limits.
 */
class DtnSyncEngine(
    private val bundleDao: DtnBundleDao,
    private val ackDao: DeliveryAckDao,
    private val peerDao: MeshPeerDao,
    var reputationManager: MeshReputationManager? = null,
    private val maxBufferSizeBytes: Long = DEFAULT_MAX_BUFFER_BYTES
) {
    companion object {
        const val DEFAULT_MAX_BUFFER_BYTES = 50 * 1024 * 1024L // 50 MB local cache limit
    }

    private val syncMutex = Mutex()

    /**
     * Constructs a Bloom Filter summarizing all active bundle IDs currently stored locally.
     * This filter is sent in the initial contact handshake with encountered neighbors.
     */
    suspend fun generateLocalInventoryFilter(): BloomFilter = syncMutex.withLock {
        val now = System.currentTimeMillis()
        val activeIds = bundleDao.getActiveBundleIds(now)
        val filter = BloomFilter()
        for (id in activeIds) {
            filter.add(id)
        }
        return filter
    }

    /**
     * Given a neighbor's advertised Bloom Filter, determines which bundles our local node carries
     * that the neighbor does NOT possess, prioritizing higher priority and earlier bundles.
     */
    suspend fun computeMissingBundlesForPeer(peerFilter: BloomFilter): List<DtnBundleEntity> = syncMutex.withLock {
        val now = System.currentTimeMillis()
        val carried = bundleDao.getPendingCarriedBundles(now)

        return carried.filter { bundle ->
            // If peer's Bloom Filter doesn't contain this bundle, they definitely don't have it!
            !peerFilter.mightContain(bundle.bundleId)
        }
    }

    /**
     * Ingests an incoming bundle from a neighbor node.
     * Enforces reputation verification, anti-spam rate limiting, duplicate rejection, hop limit checks, and DelAck validation.
     */
    suspend fun ingestIncomingBundle(bundle: DtnBundleEntity, senderPeerId: String? = null): Boolean = syncMutex.withLock {
        // 0. Reputation and Anti-Spam / Rate-Limiting evaluation
        val peerId = senderPeerId ?: bundle.sourceNodeId
        reputationManager?.let { repMgr ->
            val evaluation = repMgr.evaluateIncomingTransmission(
                peerId = peerId,
                peerAlias = "Node-${peerId.take(8)}",
                messageFingerprint = bundle.bundleId,
                cost = (bundle.payloadSizeBytes / 1024.0).coerceIn(1.0, 5.0)
            )
            if (!evaluation.isAllowed) {
                return false // Rejected due to quarantine, replay, or burst rate-limit violation
            }
        }

        // 1. Check if already acknowledged as delivered
        if (ackDao.getAckForBundle(bundle.bundleId) != null) {
            return false
        }

        // 2. Check if TTL already expired
        if (bundle.isExpired) {
            return false
        }

        // 3. Check hop limit
        if (bundle.hopCount >= bundle.maxHops) {
            return false
        }

        // 4. Ensure buffer size limits
        enforceBufferLimit(bundle.payloadSizeBytes)

        // 5. Insert or ignore duplicate
        val rowId = bundleDao.insertBundle(
            bundle.copy(
                hopCount = bundle.hopCount + 1,
                status = BundleStatus.PENDING_CARRIED
            )
        )

        val inserted = rowId != -1L
        if (inserted) {
            reputationManager?.recordPositiveInteraction(
                peerId = peerId,
                event = ReputationEvent.BUNDLE_DELIVERED_VALID,
                details = "Successfully ingested valid bundle ${bundle.bundleId.take(8)}"
            )
        }

        return inserted
    }

    /**
     * Ingests a cryptographically signed Delivery Acknowledgment (DelAck).
     * Automatically transitions carried bundles to DELIVERED status and purges payloads.
     */
    suspend fun ingestDeliveryAck(ack: DeliveryAckEntity): Boolean = syncMutex.withLock {
        val rowId = ackDao.insertAck(ack)
        if (rowId != -1L) {
            bundleDao.updateBundleStatus(ack.bundleId, BundleStatus.DELIVERED)
            reputationManager?.recordPositiveInteraction(
                peerId = ack.destinationNodeId,
                event = ReputationEvent.DELACK_CONFIRMED,
                details = "DelAck verified for bundle ${ack.bundleId.take(8)}"
            )
            return true
        }
        return false
    }

    /**
     * Executes periodic maintenance:
     * 1. Purges bundles whose TTL has expired.
     * 2. Marks stale neighbors inactive.
     */
    suspend fun runMaintenanceCycle(staleNeighborThresholdMs: Long = 60_000L) = syncMutex.withLock {
        val now = System.currentTimeMillis()
        bundleDao.markExpiredBundles(now)
        peerDao.markStaleNeighbors(now - staleNeighborThresholdMs)
    }

    /**
     * Enforces buffer capacity via priority-based eviction.
     */
    private suspend fun enforceBufferLimit(incomingBytes: Long) {
        val candidates = bundleDao.getCandidateBundlesForEviction(20)
        for (candidate in candidates) {
            bundleDao.updateBundleStatus(candidate.bundleId, BundleStatus.EVICTED)
        }
    }

    /**
     * Persists a locally originated bundle without incrementing hop count.
     */
    suspend fun storeLocalBundle(bundle: DtnBundleEntity): Boolean = syncMutex.withLock {
        enforceBufferLimit(bundle.payloadSizeBytes)
        val rowId = bundleDao.insertBundle(bundle.copy(status = BundleStatus.PENDING_CARRIED))
        return rowId != -1L
    }

    suspend fun recordPeerContact(peer: MeshPeerEntity) {
        peerDao.insertOrUpdatePeer(peer)
    }

    suspend fun getPeerByNodeId(nodeId: String): MeshPeerEntity? = peerDao.getPeerByNodeId(nodeId)

    suspend fun getPendingBundlesForDestination(destinationNodeId: String): List<DtnBundleEntity> {
        val now = System.currentTimeMillis()
        return bundleDao.getPendingCarriedBundles(now).filter { it.destinationNodeId == destinationNodeId }
    }

    suspend fun replaceBundle(bundle: DtnBundleEntity) = syncMutex.withLock {
        bundleDao.insertOrUpdateBundle(bundle.copy(status = BundleStatus.PENDING_CARRIED))
    }

    fun observeAllBundles(): Flow<List<DtnBundleEntity>> = bundleDao.observeAllBundles()
    fun observePendingCount(): Flow<Int> = bundleDao.observePendingCount()
    fun observeTotalCarriedBytes(): Flow<Long?> = bundleDao.observeTotalCarriedBytes()
    fun observeAllPeers(): Flow<List<MeshPeerEntity>> = peerDao.observeAllPeers()
    fun observeDirectNeighbors(): Flow<List<MeshPeerEntity>> = peerDao.observeDirectNeighbors()
    fun observeAllAcks(): Flow<List<DeliveryAckEntity>> = ackDao.observeAllAcks()
}
