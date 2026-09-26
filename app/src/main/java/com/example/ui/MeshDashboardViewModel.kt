package com.example.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.crypto.EccCryptoEngine
import com.example.core.crypto.IdentityKeyManager
import com.example.core.crypto.ratchet.DoubleRatchetSession
import com.example.core.crypto.ratchet.MeshCryptoManager
import com.example.core.crypto.ratchet.RatchetMessage
import com.example.core.crypto.ratchet.RatchetSessionState
import com.example.core.dtn.db.MeshDatabase
import com.example.core.dtn.model.BundlePriority
import com.example.core.dtn.model.BundleStatus
import com.example.core.dtn.model.ChatMessageEntity
import com.example.core.dtn.model.DeliveryAckEntity
import com.example.core.dtn.model.DtnBundleEntity
import com.example.core.dtn.model.MeshPeerEntity
import com.example.core.dtn.sync.DtnSyncEngine
import com.example.core.discovery.DiscoveredNsdService
import com.example.core.discovery.MeshNsdDiscoveryManager
import com.example.core.hotspot.HotspotConfig
import com.example.core.hotspot.LocalHotspotManager
import com.example.core.wan.CloudWanRelayManager
import com.example.core.wan.WanConnectionState
import com.example.core.wan.WanSignalingMessage
import com.example.core.radio.RadioCapabilities
import com.example.core.radio.RadioPermissionManager
import com.example.core.radio.RadioStatus
import com.example.core.radio.TransportTier
import com.example.core.transport.TransportPeerInfo
import com.example.core.voice.MeshVoiceManager
import com.example.core.voice.VoiceChannel
import com.example.core.voice.VoiceTransmissionState
import com.example.core.emergency.EmergencyBeacon
import com.example.core.emergency.EmergencyDistressLevel
import com.example.service.MeshForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MeshDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MeshDatabase.getInstance(application)
    val syncEngine = DtnSyncEngine(
        bundleDao = database.dtnBundleDao(),
        ackDao = database.deliveryAckDao(),
        peerDao = database.meshPeerDao()
    )

    private val identityKeyManager = IdentityKeyManager(application)
    val cryptoManager = MeshCryptoManager(identityKeyManager.getOrCreateIdentityKeyPair())

    val nodeFingerprint: String = cryptoManager.identityFingerprint

    // Tier 1 Cloud WAN Manager
    val cloudWanManager = CloudWanRelayManager(
        context = application,
        localNodeId = nodeFingerprint,
        scope = viewModelScope
    )

    // Tier 2 Local-Only Hotspot Manager
    val localHotspotManager = LocalHotspotManager(application)

    // Tier 2 / Local Subnet mDNS Service Discovery Manager
    val nsdManager = MeshNsdDiscoveryManager(
        context = application,
        localNodeId = nodeFingerprint
    )

    val wanConnectionState: StateFlow<WanConnectionState> = cloudWanManager.connectionState
    val wanRelayedPeers: StateFlow<Set<String>> = cloudWanManager.activeRelayedPeers
    val hotspotConfig: StateFlow<HotspotConfig?> = localHotspotManager.hotspotConfig
    val isHotspotRunning: StateFlow<Boolean> = localHotspotManager.isHotspotRunning
    val discoveredNsdServices: StateFlow<Map<String, DiscoveredNsdService>> = nsdManager.discoveredServices
    val isNsdAdvertising: StateFlow<Boolean> = nsdManager.isAdvertising
    val isNsdDiscovering: StateFlow<Boolean> = nsdManager.isDiscovering

    // Module 6: Push-To-Talk Voice Manager & Emergency Beacon Manager
    val voiceManager = MeshVoiceManager(
        context = application,
        localNodeId = nodeFingerprint,
        scope = viewModelScope
    )

    val voiceTransmissionState: StateFlow<VoiceTransmissionState> = voiceManager.transmissionState
    val selectedVoiceChannel: StateFlow<VoiceChannel> = voiceManager.selectedChannel
    val activeSpeakerNodeId: StateFlow<String?> = voiceManager.activeSpeakerNodeId
    val audioLevel: StateFlow<Float> = voiceManager.audioLevel
    val waveformSamples: StateFlow<List<Float>> = voiceManager.waveformSamples

    private val _isSosActive = MutableStateFlow(false)
    val isSosActive: StateFlow<Boolean> = _isSosActive.asStateFlow()
    private val _myActiveBeacon = MutableStateFlow<EmergencyBeacon?>(null)
    val myActiveBeacon: StateFlow<EmergencyBeacon?> = _myActiveBeacon.asStateFlow()
    private val _receivedSosBeacons = MutableStateFlow<List<EmergencyBeacon>>(emptyList())
    val receivedSosBeacons: StateFlow<List<EmergencyBeacon>> = _receivedSosBeacons.asStateFlow()

    private val _myLocation = MutableStateFlow(
        com.example.core.geo.NodeLocationTelemetry(
            nodeId = nodeFingerprint,
            alias = "My Node (${nodeFingerprint.take(6)})",
            latitude = com.example.core.geo.GeoMeshManager.DEFAULT_LAT,
            longitude = com.example.core.geo.GeoMeshManager.DEFAULT_LON
        )
    )
    val myLocation: StateFlow<com.example.core.geo.NodeLocationTelemetry> = _myLocation.asStateFlow()
    private val _tacticalMarkers = MutableStateFlow<List<com.example.core.geo.TacticalMarker>>(emptyList())
    val tacticalMarkers: StateFlow<List<com.example.core.geo.TacticalMarker>> = _tacticalMarkers.asStateFlow()
    private val _breadcrumbs = MutableStateFlow<List<com.example.core.geo.BreadcrumbTrackPoint>>(emptyList())
    val breadcrumbs: StateFlow<List<com.example.core.geo.BreadcrumbTrackPoint>> = _breadcrumbs.asStateFlow()
    private val _peerLocations = MutableStateFlow<Map<String, com.example.core.geo.NodeLocationTelemetry>>(emptyMap())
    val peerLocations: StateFlow<Map<String, com.example.core.geo.NodeLocationTelemetry>> = _peerLocations.asStateFlow()
    private val _selectedTacticalMarker = MutableStateFlow<com.example.core.geo.TacticalMarker?>(null)
    val selectedTacticalMarker: StateFlow<com.example.core.geo.TacticalMarker?> = _selectedTacticalMarker.asStateFlow()

    // Module 8: Bandwidth-Adaptive Media Sharing & Chunked File Transfer Manager
    val mediaTransferManager = com.example.core.media.MediaTransferManager(
        context = application,
        identityKeyManager = identityKeyManager,
        dtnSyncEngine = syncEngine,
        mediaDao = database.mediaTransferDao()
    )

    val mediaTransfers = mediaTransferManager.allTransfers
    val selectedMediaTransfer = mediaTransferManager.selectedTransfer
    val selectedTransferChunks = mediaTransferManager.selectedTransferChunks
    val currentBandwidthProfile = mediaTransferManager.currentProfile
    val mediaTransferStats = mediaTransferManager.transferStats

    // Module 9: Decentralized Reputation & Anti-Spam Gossip Protocol Manager
    val reputationManager = com.example.core.reputation.MeshReputationManager(
        localNodeId = nodeFingerprint,
        reputationDao = database.peerReputationDao(),
        identityKeyManager = identityKeyManager,
        scope = viewModelScope
    )

    val peerReputations = reputationManager.allReputations
    val quarantinedPeers = reputationManager.quarantinedPeers
    val reputationAuditLogs = reputationManager.recentAuditLogs
    val securityMetrics = reputationManager.securityMetrics

    private val _cryptoSessions = MutableStateFlow<List<RatchetSessionState>>(emptyList())
    val cryptoSessions: StateFlow<List<RatchetSessionState>> = _cryptoSessions.asStateFlow()

    private val _decryptedMessages = MutableStateFlow<List<String>>(emptyList())
    val decryptedMessages: StateFlow<List<String>> = _decryptedMessages.asStateFlow()

    val chatMessages: StateFlow<List<ChatMessageEntity>> = database.chatMessageDao().observeAllMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isMeshActive = MutableStateFlow(false)
    val isMeshActive: StateFlow<Boolean> = _isMeshActive.asStateFlow()

    private val _meshError = MutableStateFlow<String?>(null)
    val meshError: StateFlow<String?> = _meshError.asStateFlow()

    private val _connectedTransportPeers = MutableStateFlow<Map<String, TransportPeerInfo>>(emptyMap())
    val connectedTransportPeers: StateFlow<Map<String, TransportPeerInfo>> = _connectedTransportPeers.asStateFlow()

    val carriedBundles: StateFlow<List<DtnBundleEntity>> = syncEngine.observeAllBundles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val peers: StateFlow<List<MeshPeerEntity>> = syncEngine.observeAllPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingBundleCount: StateFlow<Int> = syncEngine.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCarriedBytes: StateFlow<Long?> = syncEngine.observeTotalCarriedBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val deliveryAcks: StateFlow<List<DeliveryAckEntity>> = syncEngine.observeAllAcks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _status = MutableStateFlow(RadioStatus())
    val status: StateFlow<RadioStatus> = _status.asStateFlow()

    private val _capabilities = MutableStateFlow(RadioCapabilities())
    val capabilities: StateFlow<RadioCapabilities> = _capabilities.asStateFlow()

    private val _missingPermissions = MutableStateFlow<List<String>>(emptyList())
    val missingPermissions: StateFlow<List<String>> = _missingPermissions.asStateFlow()

    private var meshService: MeshForegroundService? = null
    private var isBound = false
    private var pendingChat: Pair<String, String>? = null
    private var pendingTier: TransportTier? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MeshForegroundService.MeshServiceBinder
            meshService = binder?.service
            isBound = true

            // Sync with service's radio manager
            binder?.radioLifecycleManager?.status?.onEach { svcStatus ->
                _status.value = svcStatus
            }?.launchIn(viewModelScope)

            binder?.radioLifecycleManager?.capabilities?.onEach { svcCaps ->
                _capabilities.value = svcCaps
            }?.launchIn(viewModelScope)

            binder?.transportSwitcher?.isMeshActive?.onEach { active ->
                _isMeshActive.value = active
            }?.launchIn(viewModelScope)

            binder?.transportSwitcher?.lastError?.onEach { error ->
                _meshError.value = error
            }?.launchIn(viewModelScope)

            binder?.transportSwitcher?.connectedPeers?.onEach { peers ->
                _connectedTransportPeers.value = peers
            }?.launchIn(viewModelScope)

            binder?.geoMeshManager?.myLocation?.onEach { _myLocation.value = it }?.launchIn(viewModelScope)
            binder?.geoMeshManager?.markers?.onEach { _tacticalMarkers.value = it }?.launchIn(viewModelScope)
            binder?.geoMeshManager?.breadcrumbs?.onEach { _breadcrumbs.value = it }?.launchIn(viewModelScope)
            binder?.geoMeshManager?.peerLocations?.onEach { _peerLocations.value = it }?.launchIn(viewModelScope)
            binder?.geoMeshManager?.selectedMarker?.onEach { _selectedTacticalMarker.value = it }?.launchIn(viewModelScope)

            binder?.emergencyManager?.isSosActive?.onEach { _isSosActive.value = it }?.launchIn(viewModelScope)
            binder?.emergencyManager?.myActiveBeacon?.onEach { _myActiveBeacon.value = it }?.launchIn(viewModelScope)
            binder?.emergencyManager?.receivedEmergencyBeacons?.onEach { _receivedSosBeacons.value = it }?.launchIn(viewModelScope)
            binder?.emergencyManager?.onCriticalAlarm = { voiceManager.playAcousticAlarm() }

            pendingChat?.let { (peerId, text) ->
                pendingChat = null
                sendChatMessage(peerId, text)
            }
            pendingTier?.let { tier ->
                pendingTier = null
                binder?.radioLifecycleManager?.setTransportTier(tier)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            meshService = null
            isBound = false
        }
    }

    init {
        refreshPermissions()

        // Wire reputation engine to DTN sync engine
        syncEngine.reputationManager = reputationManager

        voiceManager.onVoipStateChanged = { isVoipActive ->
            meshService?.setVoipActive(isVoipActive)
        }

        bindToExistingService()
    }

    fun refreshPermissions() {
        _missingPermissions.value = RadioPermissionManager.getMissingPermissions(getApplication())
        meshService?.radioManager?.evaluateRadioStatus()
        meshService?.retryTransports()
    }

    fun startMeshService() {
        val context = getApplication<Application>()
        MeshForegroundService.start(context)
        bindToService()
    }

    fun stopMeshService() {
        val context = getApplication<Application>()
        MeshForegroundService.stop(context)
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
                isBound = false
            } catch (_: Exception) {}
        }
    }

    fun selectTransportTier(tier: TransportTier) {
        val service = meshService
        if (service != null) {
            service.radioManager.setTransportTier(tier)
        } else {
            pendingTier = tier
            startMeshService()
        }
        mediaTransferManager.onTransportTierChanged(tier)

        // Apply tier actions. Internet uses this phone's Wi-Fi/mobile data;
        // sharing over the cloud relay is an explicit user action, not auto-connect.
        when (tier) {
            TransportTier.TIER_1_INTERNET -> {
                stopLocalHotspot()
            }
            TransportTier.TIER_2_HOTSPOT -> {
                startLocalHotspot()
                startNsdAdvertising()
                startNsdDiscovery()
            }
            TransportTier.TIER_3_MESH -> {
                cloudWanManager.disconnect()
                stopLocalHotspot()
                startMeshService()
            }
        }
    }

    // --- Tier 1 Cloud WAN Actions ---
    fun connectCloudWan() {
        cloudWanManager.connect()
    }

    fun disconnectCloudWan() {
        cloudWanManager.disconnect()
    }

    fun simulateCloudWanRelay() {
        val simulatedWanNode = "cloud_relay_node_" + (100..999).random()
        cloudWanManager.simulateCloudRelayContact(simulatedWanNode) {
            recordDiscoveredPeer(
                nodeId = simulatedWanNode,
                alias = "Cloud Relay (STUN/TURN)",
                linkType = "Tier 1: Cloud WAN",
                rssi = -30
            )
            injectTestBundle(
                destination = nodeFingerprint,
                payload = "Cloud WAN Relayed Encrypted DTN Bundle from $simulatedWanNode",
                priority = BundlePriority.EMERGENCY
            )
        }
    }

    // --- Tier 2 Hotspot Actions ---
    fun startLocalHotspot() {
        localHotspotManager.startHotspot { success, err ->
            if (success) {
                startNsdAdvertising()
            }
        }
    }

    fun stopLocalHotspot() {
        localHotspotManager.stopHotspot()
        stopNsdAdvertising()
    }

    // --- mDNS / DNS-SD Subnet Discovery Actions ---
    fun toggleNsdAdvertising() {
        if (isNsdAdvertising.value) {
            stopNsdAdvertising()
        } else {
            startNsdAdvertising()
        }
    }

    fun startNsdAdvertising() {
        nsdManager.startAdvertising()
    }

    fun stopNsdAdvertising() {
        nsdManager.stopAdvertising()
    }

    fun toggleNsdDiscovery() {
        if (isNsdDiscovering.value) {
            stopNsdDiscovery()
        } else {
            startNsdDiscovery()
        }
    }

    fun startNsdDiscovery() {
        nsdManager.startDiscovery()
    }

    fun stopNsdDiscovery() {
        nsdManager.stopDiscovery()
    }

    fun toggleVoipCall() {
        val nextVoip = !_status.value.isVoipActive
        meshService?.setVoipActive(nextVoip)
    }

    // --- Module 6: Voice & Emergency Actions ---
    fun selectVoiceChannel(channel: VoiceChannel) {
        voiceManager.selectChannel(channel)
    }

    fun startPttTransmitting() {
        voiceManager.startTransmitting()
    }

    fun stopPttTransmitting() {
        voiceManager.stopTransmitting()
    }

    fun simulateIncomingVoiceCall(peerAlias: String = "Squad Leader Bravo") {
        voiceManager.simulateIncomingVoiceTransmission(peerAlias)
    }

    fun testAlarmSiren() {
        voiceManager.playAcousticAlarm()
    }

    fun triggerEmergencySos(
        level: EmergencyDistressLevel = EmergencyDistressLevel.SOS_CRITICAL,
        message: String = "CRITICAL: Immediate assistance requested. Off-grid SOS beacon."
    ) {
        val manager = meshService?.emergencyManager
        if (manager != null) {
            manager.triggerEmergencySos(level, message)
            voiceManager.playAcousticAlarm()
        } else {
            startMeshService()
        }
    }

    fun cancelEmergencySos() {
        meshService?.emergencyManager?.cancelEmergencySos() ?: startMeshService()
    }

    fun simulateIncomingSos(
        peerAlias: String = "Scout Charlie",
        level: EmergencyDistressLevel = EmergencyDistressLevel.MEDICAL_EMERGENCY,
        message: String = "Medical emergency: Evac needed at grid marker 37.78, -122.41."
    ) {
        meshService?.emergencyManager?.simulateIncomingSosBeacon(peerAlias, level, message)
            ?: startMeshService()
    }

    // --- Module 7: Geospatial Tactical Map Actions ---
    fun createTacticalMarker(
        type: com.example.core.geo.TacticalMarkerType,
        title: String,
        description: String,
        lat: Double,
        lon: Double
    ) {
        val geo = meshService?.geoMeshManager
        if (geo != null) {
            geo.createTacticalMarker(type, title, description, lat, lon)
        } else {
            startMeshService()
        }
    }

    fun toggleMarkerResolved(markerId: String) {
        meshService?.geoMeshManager?.toggleMarkerResolved(markerId) ?: startMeshService()
    }

    fun deleteTacticalMarker(markerId: String) {
        meshService?.geoMeshManager?.deleteMarker(markerId)
    }

    fun selectTacticalMarker(marker: com.example.core.geo.TacticalMarker?) {
        meshService?.geoMeshManager?.selectMarker(marker)
        _selectedTacticalMarker.value = marker
    }

    fun simulateMapStep() {
        meshService?.geoMeshManager?.simulateStepMovement()
    }

    fun simulatePeerGpsMovements() {
        meshService?.geoMeshManager?.simulatePeerMovements()
    }

    fun clearBreadcrumbTrail() {
        meshService?.geoMeshManager?.clearBreadcrumbs()
    }

    /**
     * Injects a mock bundle into local storage to test store-carry-and-forward routing.
     */
    fun injectTestBundle(destination: String, payload: String, priority: BundlePriority = BundlePriority.NORMAL) {
        viewModelScope.launch {
            val bundleId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val entity = DtnBundleEntity(
                bundleId = bundleId,
                sourceNodeId = "node_local_me",
                destinationNodeId = destination,
                priority = priority,
                createdAtEpochMs = now,
                expiresAtEpochMs = now + (60 * 60 * 1000L), // 1 hour TTL
                payloadSizeBytes = payload.toByteArray().size.toLong(),
                encryptedPayloadHex = payload.toByteArray().joinToString("") { "%02x".format(it) },
                senderSignatureHex = "3045022100${UUID.randomUUID().toString().replace("-", "")}"
            )
            syncEngine.ingestIncomingBundle(entity)
        }
    }

    /**
     * Simulates receiving a Delivery Acknowledgment (DelAck) for a bundle.
     */
    fun acknowledgeBundle(bundleId: String, destNodeId: String) {
        viewModelScope.launch {
            val ack = DeliveryAckEntity(
                bundleId = bundleId,
                destinationNodeId = destNodeId,
                recipientSignatureHex = "30440220${UUID.randomUUID().toString().replace("-", "")}"
            )
            syncEngine.ingestDeliveryAck(ack)
        }
    }

    /**
     * Discovers or updates a mesh peer contact.
     */
    fun recordDiscoveredPeer(nodeId: String, alias: String, linkType: String, rssi: Int) {
        viewModelScope.launch {
            val peer = MeshPeerEntity(
                nodeId = nodeId,
                alias = alias,
                lastSeenEpochMs = System.currentTimeMillis(),
                rssiDbm = rssi,
                directLinkType = linkType,
                isDirectNeighbor = true
            )
            syncEngine.recordPeerContact(peer)
        }
    }

    /**
     * Executes maintenance cycle (purging expired TTL bundles and pruning stale peers).
     */
    fun runMaintenance() {
        viewModelScope.launch {
            syncEngine.runMaintenanceCycle()
        }
    }

    /**
     * Sends a signed chat message over the live mesh, or queues it until radios start.
     */
    fun sendChatMessage(peerNodeId: String, plaintext: String) {
        val dest = peerNodeId.ifBlank { "*" }
        val text = plaintext.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val controller = meshService?.linkController
            if (controller != null) {
                controller.sendChatMessage(dest, text)
            } else {
                pendingChat = dest to text
                startMeshService()
            }
        }
    }

    /**
     * Simulates an end-to-end Double Ratchet exchange with a simulated remote node.
     */
    fun simulateRatchetExchange(remoteAlias: String, outgoingPlaintext: String) {
        viewModelScope.launch {
            val remotePeerNodeId = "peer_" + remoteAlias.lowercase().replace(" ", "_")
            
            // 1. Generate simulated remote identity & prekey
            val remoteIdentityKp = EccCryptoEngine.generateKeyPair()
            val remotePrekeyKp = EccCryptoEngine.generateKeyPair()

            // 2. Initialize local initiator session with remote peer keys
            val localSession = cryptoManager.startInitiatorSession(
                peerNodeId = remotePeerNodeId,
                peerIdentityPublicKey = remoteIdentityKp.public,
                peerPrekeyPublic = remotePrekeyKp.public
            )

            // 3. Encrypt message locally with Forward Secrecy
            val encryptedMsg = localSession.encrypt(outgoingPlaintext.toByteArray(Charsets.UTF_8))

            // 4. Initialize simulated receiver session
            val remoteReceiver = DoubleRatchetSession.initializeReceiver(
                peerNodeId = nodeFingerprint,
                sharedMasterKey = com.example.core.crypto.Hkdf.deriveKey(
                    salt = "Mesh3DhSalt".toByteArray(Charsets.UTF_8),
                    ikm = EccCryptoEngine.performEcdh(remotePrekeyKp.private, identityKeyManager.getOrCreateIdentityKeyPair().public) +
                          EccCryptoEngine.performEcdh(remoteIdentityKp.private, EccCryptoEngine.decodePublicKeyCompressed(encryptedMsg.ephemeralPublicKeyBytes)) +
                          EccCryptoEngine.performEcdh(remotePrekeyKp.private, EccCryptoEngine.decodePublicKeyCompressed(encryptedMsg.ephemeralPublicKeyBytes)),
                    info = "MeshRatchetBootstrap".toByteArray(Charsets.UTF_8),
                    length = 32
                ),
                ourPreSharedKeyPair = remotePrekeyKp
            )

            // 5. Receiver decrypts
            val decryptedBytes = remoteReceiver.decrypt(encryptedMsg)
            val decryptedText = String(decryptedBytes, Charsets.UTF_8)

            // 6. Record peer & bundle in database
            val signature = cryptoManager.sign(outgoingPlaintext.toByteArray(Charsets.UTF_8))
            val bundle = DtnBundleEntity(
                bundleId = UUID.randomUUID().toString(),
                sourceNodeId = nodeFingerprint,
                destinationNodeId = remotePeerNodeId,
                hopCount = 0,
                maxHops = 5,
                priority = BundlePriority.HIGH,
                expiresAtEpochMs = System.currentTimeMillis() + 86_400_000,
                payloadSizeBytes = encryptedMsg.ciphertext.size.toLong(),
                encryptedPayloadHex = encryptedMsg.ciphertext.joinToString("") { "%02x".format(it) },
                senderSignatureHex = signature.joinToString("") { "%02x".format(it) },
                status = BundleStatus.PENDING_CARRIED
            )
            syncEngine.ingestIncomingBundle(bundle)
            recordDiscoveredPeer(remotePeerNodeId, remoteAlias, "Double-Ratchet Direct", -42)

            _cryptoSessions.value = cryptoManager.getAllSessions()
            _decryptedMessages.value = _decryptedMessages.value + "[$remoteAlias]: $decryptedText (Verified ECDSA + AES-GCM)"
        }
    }

    // --- Module 8: Media Transfer Operations ---
    fun selectMediaTransfer(transfer: com.example.core.media.db.MediaTransferEntity?) {
        mediaTransferManager.selectTransfer(transfer)
    }

    fun sendPresetMedia(type: com.example.core.media.model.MediaType, forcedProfile: com.example.core.media.model.BandwidthProfile? = null) {
        val (name, bytes) = com.example.core.media.compression.CompressionEngine.generatePresetSample(type)
        mediaTransferManager.createAndStartTransfer(
            fileName = name,
            mediaType = type,
            rawBytes = bytes,
            recipientNodeId = "*",
            recipientAlias = "All Mesh Nodes",
            forcedProfile = forcedProfile
        )
    }

    fun sendCustomMedia(
        name: String,
        bytes: ByteArray,
        type: com.example.core.media.model.MediaType,
        forcedProfile: com.example.core.media.model.BandwidthProfile? = null
    ) {
        mediaTransferManager.createAndStartTransfer(
            fileName = name,
            mediaType = type,
            rawBytes = bytes,
            recipientNodeId = "*",
            recipientAlias = "All Mesh Nodes",
            forcedProfile = forcedProfile
        )
    }

    fun pauseMediaTransfer(transferId: String) {
        mediaTransferManager.pauseTransfer(transferId)
    }

    fun resumeMediaTransfer(transferId: String) {
        mediaTransferManager.resumeTransfer(transferId)
    }

    fun cancelMediaTransfer(transferId: String) {
        mediaTransferManager.cancelTransfer(transferId)
    }

    fun deleteMediaTransfer(transferId: String) {
        mediaTransferManager.deleteTransfer(transferId)
    }

    fun requestSelectiveChunkRepair(transferId: String) {
        mediaTransferManager.requestSelectiveRepair(transferId)
    }

    fun setBandwidthProfile(profile: com.example.core.media.model.BandwidthProfile) {
        mediaTransferManager.setManualProfile(profile)
    }

    // Module 9: Decentralized Reputation & Anti-Spam Actions
    fun togglePeerQuarantine(peerId: String, quarantined: Boolean, reason: String = "Manual operator toggle") {
        viewModelScope.launch {
            reputationManager.togglePeerQuarantine(peerId, quarantined, reason)
        }
    }

    fun broadcastReputationAttestation(targetPeerId: String, delta: Int, reason: String) {
        viewModelScope.launch {
            reputationManager.broadcastAttestationGossip(targetPeerId, delta, reason)
        }
    }

    fun simulateSpamFloodAttack(peerId: String) {
        viewModelScope.launch {
            // Rapidly inject 15 bursts from the same peer ID with duplicate hash to trigger replay and rate-limiting
            val messageHash = "flood_probe_attack_${System.currentTimeMillis()}"
            repeat(15) { i ->
                val eval = reputationManager.evaluateIncomingTransmission(
                    peerId = peerId,
                    peerAlias = "Attacker-$peerId",
                    messageFingerprint = if (i % 2 == 0) messageHash else "unique_burst_${i}_$peerId",
                    cost = 1.5
                )
            }
        }
    }

    fun simulateSolvePowChallenge(peerId: String) {
        viewModelScope.launch {
            val challenge = reputationManager.createPowChallengeForPeer(peerId, com.example.core.reputation.model.TrustTier.SUSPICIOUS)
            val solution = com.example.core.reputation.pow.PowEngine.solve(challenge, peerId)
            if (solution != null) {
                reputationManager.verifyAndApplyPowSolution(solution)
            }
        }
    }

    fun simulatePositiveEndorsement(peerId: String) {
        viewModelScope.launch {
            reputationManager.recordPositiveInteraction(
                peerId = peerId,
                event = com.example.core.reputation.model.ReputationEvent.BUNDLE_DELIVERED_VALID,
                details = "Relayed encrypted bundle to destination successfully"
            )
            reputationManager.recordPositiveInteraction(
                peerId = peerId,
                event = com.example.core.reputation.model.ReputationEvent.DELACK_CONFIRMED,
                details = "DelAck receipt confirmed and signed"
            )
        }
    }

    fun bindToService() {
        if (isBound) return
        val context = getApplication<Application>()
        val intent = Intent(context, MeshForegroundService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun bindToExistingService() {
        if (isBound) return
        val context = getApplication<Application>()
        val intent = Intent(context, MeshForegroundService::class.java)
        context.bindService(intent, serviceConnection, 0)
    }

    override fun onCleared() {
        super.onCleared()
        cloudWanManager.disconnect()
        localHotspotManager.stopHotspot()
        nsdManager.stopAdvertising()
        nsdManager.stopDiscovery()
        voiceManager.release()
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: Exception) {}
        }
    }
}
