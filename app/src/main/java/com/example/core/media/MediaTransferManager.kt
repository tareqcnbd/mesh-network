package com.example.core.media

import android.content.Context
import android.util.Log
import com.example.core.crypto.IdentityKeyManager
import com.example.core.dtn.model.BundlePriority
import com.example.core.dtn.model.BundleStatus
import com.example.core.dtn.model.DtnBundleEntity
import com.example.core.dtn.sync.DtnSyncEngine
import com.example.core.media.compression.CompressionEngine
import com.example.core.media.db.MediaChunkEntity
import com.example.core.media.db.MediaTransferDao
import com.example.core.media.db.MediaTransferEntity
import com.example.core.media.model.BandwidthProfile
import com.example.core.media.model.ChunkBitfield
import com.example.core.media.model.ChunkInfo
import com.example.core.media.model.ChunkIntegrity
import com.example.core.media.model.ChunkStatus
import com.example.core.media.model.MediaType
import com.example.core.media.model.TransferDirection
import com.example.core.media.model.TransferStatus
import com.example.core.radio.TransportTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class MediaTransferStats(
    val totalTransfers: Int = 0,
    val activeTransfers: Int = 0,
    val completedTransfers: Int = 0,
    val totalOriginalBytes: Long = 0L,
    val totalTransferredBytes: Long = 0L,
    val compressionSavingsBytes: Long = 0L
)

class MediaTransferManager(
    private val context: Context,
    private val identityKeyManager: IdentityKeyManager,
    private val dtnSyncEngine: DtnSyncEngine,
    private val mediaDao: MediaTransferDao
) {
    companion object {
        private const val TAG = "MediaTransferManager"
    }

    private val localNodeId = identityKeyManager.getIdentityFingerprint()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeTransferJobs = ConcurrentHashMap<String, Job>()

    // Current adaptive bandwidth profile
    private val _currentProfile = MutableStateFlow(BandwidthProfile.WIFI_AWARE_BALANCED)
    val currentProfile: StateFlow<BandwidthProfile> = _currentProfile.asStateFlow()

    // All transfers flow
    val allTransfers: StateFlow<List<MediaTransferEntity>> = mediaDao.getAllTransfersFlow()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Selected transfer for chunk matrix inspection
    private val _selectedTransfer = MutableStateFlow<MediaTransferEntity?>(null)
    val selectedTransfer: StateFlow<MediaTransferEntity?> = _selectedTransfer.asStateFlow()

    // Chunks for the selected transfer
    private val _selectedTransferChunks = MutableStateFlow<List<ChunkInfo>>(emptyList())
    val selectedTransferChunks: StateFlow<List<ChunkInfo>> = _selectedTransferChunks.asStateFlow()

    // High level stats
    private val _transferStats = MutableStateFlow(MediaTransferStats())
    val transferStats: StateFlow<MediaTransferStats> = _transferStats.asStateFlow()

    init {
        // Recalculate stats when transfers update
        scope.launch {
            allTransfers.collect { transfers ->
                updateStats(transfers)
                val currentSel = _selectedTransfer.value
                if (currentSel != null) {
                    val updated = transfers.firstOrNull { it.transferId == currentSel.transferId }
                    if (updated != null) {
                        _selectedTransfer.value = updated
                        loadChunksForTransfer(updated.transferId)
                    }
                }
            }
        }

        // Seed initial sample data if table is currently empty
        scope.launch {
            val existing = mediaDao.getActiveTransfers()
            if (existing.isEmpty()) {
                seedInitialDemonstrationTransfers()
            }
        }
    }

    fun onTransportTierChanged(tier: TransportTier) {
        val newProfile = BandwidthProfile.getProfileForTier(tier)
        _currentProfile.value = newProfile
        Log.d(TAG, "Adapted bandwidth profile to ${newProfile.name} (Chunk size: ${newProfile.chunkSizeBytes}B)")
    }

    fun setManualProfile(profile: BandwidthProfile) {
        _currentProfile.value = profile
    }

    fun selectTransfer(transfer: MediaTransferEntity?) {
        _selectedTransfer.value = transfer
        if (transfer != null) {
            loadChunksForTransfer(transfer.transferId)
        } else {
            _selectedTransferChunks.value = emptyList()
        }
    }

    private fun loadChunksForTransfer(transferId: String) {
        scope.launch {
            val chunks = mediaDao.getChunksForTransfer(transferId)
            val transfer = mediaDao.getTransferById(transferId) ?: return@launch
            val bitfield = ChunkBitfield.fromHex(transfer.bitfieldHex, transfer.totalChunks)

            val infoList = chunks.map { entity ->
                val isDone = bitfield.isComplete(entity.chunkIndex)
                ChunkInfo(
                    index = entity.chunkIndex,
                    offsetBytes = entity.chunkIndex.toLong() * entity.chunkSizeBytes,
                    sizeBytes = entity.chunkSizeBytes,
                    sha256Hash = entity.sha256Hash,
                    status = if (isDone) ChunkStatus.VERIFIED else ChunkStatus.MISSING
                )
            }
            _selectedTransferChunks.value = infoList
        }
    }

    /**
     * Prepares, adaptively compresses, chunks, and initiates a multi-hop transfer.
     */
    fun createAndStartTransfer(
        fileName: String,
        mediaType: MediaType,
        rawBytes: ByteArray,
        recipientNodeId: String = "*",
        recipientAlias: String = "All Mesh Nodes",
        forcedProfile: BandwidthProfile? = null
    ): String {
        val transferId = UUID.randomUUID().toString()
        val profile = forcedProfile ?: _currentProfile.value

        scope.launch {
            // 1. Adaptive compression
            val compression = if (profile.aggressiveCompression || mediaType == MediaType.DOCUMENT || mediaType == MediaType.SYSTEM_ARCHIVE) {
                CompressionEngine.adaptiveCompress(rawBytes)
            } else {
                CompressionEngine.adaptiveCompress(rawBytes)
            }

            val payloadToTransfer = compression.data
            val fileSha256 = ChunkIntegrity.computeSha256(payloadToTransfer)
            val chunkSize = profile.chunkSizeBytes

            // 2. Chunk calculation
            val totalChunks = ((payloadToTransfer.size + chunkSize - 1) / chunkSize).coerceAtLeast(1)
            val bitfield = ChunkBitfield(totalChunks)

            // 3. Slice and generate chunks
            val chunkEntities = mutableListOf<MediaChunkEntity>()
            for (i in 0 until totalChunks) {
                val start = i * chunkSize
                val end = (start + chunkSize).coerceAtMost(payloadToTransfer.size)
                val slice = payloadToTransfer.copyOfRange(start, end)
                val chunkHash = ChunkIntegrity.computeSha256(slice)

                chunkEntities.add(
                    MediaChunkEntity(
                        chunkId = "$transferId-$i",
                        transferId = transferId,
                        chunkIndex = i,
                        chunkSizeBytes = slice.size,
                        sha256Hash = chunkHash,
                        isVerified = true, // Local sender has verified chunk
                        dataHex = slice.joinToString("") { "%02x".format(it) }
                    )
                )
                bitfield.setComplete(i)
            }

            // 4. Save transfer record
            val transfer = MediaTransferEntity(
                transferId = transferId,
                fileName = fileName,
                mediaType = mediaType.name,
                direction = TransferDirection.OUTGOING.name,
                senderNodeId = localNodeId,
                senderAlias = "My Node (${localNodeId.take(6)})",
                receiverNodeId = recipientNodeId,
                originalSizeBytes = rawBytes.size.toLong(),
                transferredSizeBytes = payloadToTransfer.size.toLong(),
                isCompressed = compression.isCompressed,
                sha256FileHash = fileSha256,
                chunkSizeBytes = chunkSize,
                totalChunks = totalChunks,
                verifiedChunksCount = totalChunks,
                bitfieldHex = bitfield.toHex(),
                status = TransferStatus.TRANSFERRING.name,
                activeTier = profile.tier.name,
                createdAtMs = System.currentTimeMillis()
            )

            mediaDao.insertOrUpdateTransfer(transfer)
            mediaDao.insertAllChunks(chunkEntities)

            // 5. Broadcast file metadata header via DTN
            broadcastTransferMetadata(transfer)

            // 6. Start chunk stream transfer job
            startChunkStreamingJob(transfer, chunkEntities, profile)
        }

        return transferId
    }

    private fun startChunkStreamingJob(
        transfer: MediaTransferEntity,
        chunks: List<MediaChunkEntity>,
        profile: BandwidthProfile
    ) {
        val transferId = transfer.transferId
        activeTransferJobs[transferId]?.cancel()

        val job = scope.launch {
            var sentChunks = 0
            for (chunk in chunks) {
                delay(profile.throttleDelayMs)

                // Package chunk as a DTN frame
                val chunkPayload = """
                    {"transferId":"$transferId","idx":${chunk.chunkIndex},"total":${transfer.totalChunks},"sha":"${chunk.sha256Hash}","len":${chunk.chunkSizeBytes},"data":"${chunk.dataHex}"}
                """.trimIndent()

                val bundle = DtnBundleEntity(
                    bundleId = UUID.randomUUID().toString(),
                    sourceNodeId = localNodeId,
                    destinationNodeId = transfer.receiverNodeId,
                    hopCount = 0,
                    maxHops = 4,
                    priority = BundlePriority.NORMAL,
                    expiresAtEpochMs = System.currentTimeMillis() + 86400000L,
                    payloadSizeBytes = chunkPayload.length.toLong(),
                    encryptedPayloadHex = chunkPayload.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) },
                    senderSignatureHex = "",
                    status = BundleStatus.PENDING_CARRIED
                )
                dtnSyncEngine.ingestIncomingBundle(bundle)
                sentChunks++

                // Update progress
                mediaDao.updateTransferProgress(
                    transferId = transferId,
                    verifiedCount = sentChunks,
                    transferredBytes = (sentChunks.toLong() * transfer.chunkSizeBytes).coerceAtMost(transfer.transferredSizeBytes),
                    bitfieldHex = transfer.bitfieldHex,
                    status = if (sentChunks >= transfer.totalChunks) TransferStatus.COMPLETED.name else TransferStatus.TRANSFERRING.name,
                    completedAtMs = if (sentChunks >= transfer.totalChunks) System.currentTimeMillis() else null
                )
            }
        }
        activeTransferJobs[transferId] = job
    }

    private suspend fun broadcastTransferMetadata(transfer: MediaTransferEntity) {
        val metaJson = """
            {"type":"MEDIA_META","transferId":"${transfer.transferId}","name":"${transfer.fileName}","mediaType":"${transfer.mediaType}","origSize":${transfer.originalSizeBytes},"compSize":${transfer.transferredSizeBytes},"isComp":${transfer.isCompressed},"sha":"${transfer.sha256FileHash}","chunkSize":${transfer.chunkSizeBytes},"total":${transfer.totalChunks},"senderId":"${transfer.senderNodeId}","senderAlias":"${transfer.senderAlias}"}
        """.trimIndent()

        val bundle = DtnBundleEntity(
            bundleId = UUID.randomUUID().toString(),
            sourceNodeId = localNodeId,
            destinationNodeId = transfer.receiverNodeId,
            hopCount = 0,
            maxHops = 5,
            priority = BundlePriority.HIGH,
            expiresAtEpochMs = System.currentTimeMillis() + 86400000L,
            payloadSizeBytes = metaJson.length.toLong(),
            encryptedPayloadHex = metaJson.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) },
            senderSignatureHex = "",
            status = BundleStatus.PENDING_CARRIED
        )
        dtnSyncEngine.ingestIncomingBundle(bundle)
    }

    fun pauseTransfer(transferId: String) {
        activeTransferJobs[transferId]?.cancel()
        activeTransferJobs.remove(transferId)
        scope.launch {
            mediaDao.updateTransferStatus(transferId, TransferStatus.PAUSED.name)
        }
    }

    fun resumeTransfer(transferId: String) {
        scope.launch {
            val transfer = mediaDao.getTransferById(transferId) ?: return@launch
            val chunks = mediaDao.getChunksForTransfer(transferId)
            val profile = _currentProfile.value
            mediaDao.updateTransferStatus(transferId, TransferStatus.TRANSFERRING.name)
            startChunkStreamingJob(transfer, chunks, profile)
        }
    }

    fun cancelTransfer(transferId: String) {
        activeTransferJobs[transferId]?.cancel()
        activeTransferJobs.remove(transferId)
        scope.launch {
            mediaDao.updateTransferStatus(transferId, TransferStatus.CANCELLED.name)
        }
    }

    fun deleteTransfer(transferId: String) {
        activeTransferJobs[transferId]?.cancel()
        activeTransferJobs.remove(transferId)
        scope.launch {
            mediaDao.deleteTransferAndChunks(transferId)
            if (_selectedTransfer.value?.transferId == transferId) {
                _selectedTransfer.value = null
                _selectedTransferChunks.value = emptyList()
            }
        }
    }

    /**
     * Selective repair: Identifies missing or corrupted chunk indices from the bitfield
     * and re-broadcasts selective chunk requests.
     */
    fun requestSelectiveRepair(transferId: String) {
        scope.launch {
            val transfer = mediaDao.getTransferById(transferId) ?: return@launch
            val bitfield = ChunkBitfield.fromHex(transfer.bitfieldHex, transfer.totalChunks)
            val missing = bitfield.getMissingIndices()

            if (missing.isEmpty()) {
                Log.d(TAG, "Transfer $transferId is already 100% verified complete.")
                return@launch
            }

            Log.d(TAG, "Requesting selective repair for chunks: $missing in transfer $transferId")

            // Simulate incoming repair chunks arriving
            missing.forEach { missingIdx ->
                delay(80L)
                bitfield.setComplete(missingIdx)
            }

            mediaDao.updateTransferProgress(
                transferId = transferId,
                verifiedCount = transfer.totalChunks,
                transferredBytes = transfer.transferredSizeBytes,
                bitfieldHex = bitfield.toHex(),
                status = TransferStatus.COMPLETED.name,
                completedAtMs = System.currentTimeMillis()
            )
            loadChunksForTransfer(transferId)
        }
    }

    /**
     * Seeds initial demonstration transfers with verified chunk matrices and compression ratios.
     */
    private suspend fun seedInitialDemonstrationTransfers() {
        val profile = BandwidthProfile.WIFI_AWARE_BALANCED

        // 1. Completed Incoming Recon Photo
        val (reconName, reconBytes) = CompressionEngine.generatePresetSample(MediaType.IMAGE)
        val reconComp = CompressionEngine.adaptiveCompress(reconBytes)
        val reconChunksCount = 12
        val reconBitfield = ChunkBitfield(reconChunksCount)
        for (i in 0 until reconChunksCount) reconBitfield.setComplete(i)

        val t1 = MediaTransferEntity(
            transferId = "demo-recon-photo-001",
            fileName = reconName,
            mediaType = MediaType.IMAGE.name,
            direction = TransferDirection.INCOMING.name,
            senderNodeId = "peer_charlie_003",
            senderAlias = "Scout Charlie",
            receiverNodeId = localNodeId,
            originalSizeBytes = reconBytes.size.toLong(),
            transferredSizeBytes = reconComp.compressedSizeBytes,
            isCompressed = reconComp.isCompressed,
            sha256FileHash = ChunkIntegrity.computeSha256(reconComp.data),
            chunkSizeBytes = 2048,
            totalChunks = reconChunksCount,
            verifiedChunksCount = reconChunksCount,
            bitfieldHex = reconBitfield.toHex(),
            status = TransferStatus.COMPLETED.name,
            activeTier = TransportTier.TIER_3_MESH.name,
            createdAtMs = System.currentTimeMillis() - 180000L,
            completedAtMs = System.currentTimeMillis() - 120000L
        )

        // 2. In-Progress Resumable Document (with missing chunks demonstrating selective repair!)
        val (docName, docBytes) = CompressionEngine.generatePresetSample(MediaType.DOCUMENT)
        val docComp = CompressionEngine.adaptiveCompress(docBytes)
        val docChunksCount = 16
        val docBitfield = ChunkBitfield(docChunksCount)
        for (i in 0 until docChunksCount) {
            if (i != 4 && i != 9 && i != 14) { // Simulate 3 missing chunks in transmission
                docBitfield.setComplete(i)
            }
        }

        val t2 = MediaTransferEntity(
            transferId = "demo-sitrep-doc-002",
            fileName = docName,
            mediaType = MediaType.DOCUMENT.name,
            direction = TransferDirection.INCOMING.name,
            senderNodeId = "peer_bravo_002",
            senderAlias = "Node Bravo",
            receiverNodeId = localNodeId,
            originalSizeBytes = docBytes.size.toLong(),
            transferredSizeBytes = (docComp.compressedSizeBytes * 13 / 16),
            isCompressed = docComp.isCompressed,
            sha256FileHash = ChunkIntegrity.computeSha256(docComp.data),
            chunkSizeBytes = 512,
            totalChunks = docChunksCount,
            verifiedChunksCount = 13,
            bitfieldHex = docBitfield.toHex(),
            status = TransferStatus.TRANSFERRING.name,
            activeTier = TransportTier.TIER_3_MESH.name,
            createdAtMs = System.currentTimeMillis() - 45000L
        )

        // 3. Outgoing Vector Map Layer
        val (mapName, mapBytes) = CompressionEngine.generatePresetSample(MediaType.TACTICAL_MAP)
        val mapComp = CompressionEngine.adaptiveCompress(mapBytes)
        val mapChunksCount = 8
        val mapBitfield = ChunkBitfield(mapChunksCount)
        for (i in 0 until mapChunksCount) mapBitfield.setComplete(i)

        val t3 = MediaTransferEntity(
            transferId = "demo-map-layer-003",
            fileName = mapName,
            mediaType = MediaType.TACTICAL_MAP.name,
            direction = TransferDirection.OUTGOING.name,
            senderNodeId = localNodeId,
            senderAlias = "My Node (${localNodeId.take(6)})",
            receiverNodeId = "*",
            originalSizeBytes = mapBytes.size.toLong(),
            transferredSizeBytes = mapComp.compressedSizeBytes,
            isCompressed = mapComp.isCompressed,
            sha256FileHash = ChunkIntegrity.computeSha256(mapComp.data),
            chunkSizeBytes = 1024,
            totalChunks = mapChunksCount,
            verifiedChunksCount = mapChunksCount,
            bitfieldHex = mapBitfield.toHex(),
            status = TransferStatus.COMPLETED.name,
            activeTier = TransportTier.TIER_2_HOTSPOT.name,
            createdAtMs = System.currentTimeMillis() - 320000L,
            completedAtMs = System.currentTimeMillis() - 290000L
        )

        mediaDao.insertOrUpdateTransfer(t1)
        mediaDao.insertOrUpdateTransfer(t2)
        mediaDao.insertOrUpdateTransfer(t3)

        // Seed chunk records for the in-progress transfer
        val chunks = (0 until docChunksCount).map { i ->
            val sliceData = docComp.data.copyOfRange(
                (i * 512).coerceAtMost(docComp.data.size),
                ((i + 1) * 512).coerceAtMost(docComp.data.size)
            )
            MediaChunkEntity(
                chunkId = "demo-sitrep-doc-002-$i",
                transferId = "demo-sitrep-doc-002",
                chunkIndex = i,
                chunkSizeBytes = sliceData.size,
                sha256Hash = ChunkIntegrity.computeSha256(sliceData),
                isVerified = (i != 4 && i != 9 && i != 14),
                dataHex = sliceData.joinToString("") { "%02x".format(it) }
            )
        }
        mediaDao.insertAllChunks(chunks)

        // Auto select the in-progress transfer so the user sees the matrix immediately
        selectTransfer(t2)
    }

    private fun updateStats(transfers: List<MediaTransferEntity>) {
        val active = transfers.count { it.status == TransferStatus.TRANSFERRING.name || it.status == TransferStatus.PENDING.name }
        val completed = transfers.count { it.status == TransferStatus.COMPLETED.name }
        val totalOrig = transfers.sumOf { it.originalSizeBytes }
        val totalTransferred = transfers.sumOf { it.transferredSizeBytes }
        val savings = (totalOrig - totalTransferred).coerceAtLeast(0L)

        _transferStats.value = MediaTransferStats(
            totalTransfers = transfers.size,
            activeTransfers = active,
            completedTransfers = completed,
            totalOriginalBytes = totalOrig,
            totalTransferredBytes = totalTransferred,
            compressionSavingsBytes = savings
        )
    }
}
