package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.crypto.ratchet.RatchetSessionState
import com.example.core.discovery.DiscoveredNsdService
import com.example.core.dtn.model.BundlePriority
import com.example.core.dtn.model.ChatMessageEntity
import com.example.core.dtn.model.DeliveryAckEntity
import com.example.core.dtn.model.DtnBundleEntity
import com.example.core.dtn.model.MeshPeerEntity
import com.example.core.emergency.EmergencyBeacon
import com.example.core.emergency.EmergencyDistressLevel
import com.example.core.geo.BreadcrumbTrackPoint
import com.example.core.geo.NodeLocationTelemetry
import com.example.core.geo.TacticalMarker
import com.example.core.geo.TacticalMarkerType
import com.example.core.hotspot.HotspotConfig
import com.example.core.media.db.MediaTransferEntity
import com.example.core.media.model.BandwidthProfile
import com.example.core.media.model.MediaType
import com.example.core.radio.RadioCapabilities
import com.example.core.radio.RadioPermissionManager
import com.example.core.radio.RadioStatus
import com.example.core.radio.TransportTier
import com.example.core.reputation.SecurityMetrics
import com.example.core.reputation.db.PeerReputationEntity
import com.example.core.reputation.db.ReputationAuditLogEntity
import com.example.core.voice.VoiceChannel
import com.example.core.voice.VoiceTransmissionState
import com.example.core.wan.WanConnectionState
import com.example.ui.MeshDashboardViewModel
import com.example.ui.components.ConnectionSignalState
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.components.MonrBottomBar
import com.example.ui.components.MonrScreenBackground
import com.example.ui.screens.CryptoRatchetTab
import com.example.ui.screens.DtnBundlesView
import com.example.ui.screens.InjectBundleDialog
import com.example.ui.screens.MediaTransferTab
import com.example.ui.screens.MeshPeersView
import com.example.ui.screens.MeshVoiceTab
import com.example.ui.screens.MonrChatScreen
import com.example.ui.screens.MonrConversationList
import com.example.ui.screens.MonrPeersScreen
import com.example.ui.screens.MonrSettingsScreen
import com.example.ui.screens.MultiTierNetworkTab
import com.example.ui.screens.NetworkOverviewScreen
import com.example.ui.screens.PeerReputationTab
import com.example.ui.screens.RadioLifecycleTab
import com.example.ui.screens.TacticalMapTab
import com.example.ui.theme.MonrAmberWarning
import com.example.ui.theme.MonrBgDark
import com.example.ui.theme.MonrBorderDark
import com.example.ui.theme.MonrCyanAccent
import com.example.ui.theme.MonrCyanGlow
import com.example.ui.theme.MonrCyanLight
import com.example.ui.theme.MonrPrimary
import com.example.ui.theme.MonrPrimaryDark
import com.example.ui.theme.MonrPrimaryContainer
import com.example.ui.theme.MonrSignalGreen
import com.example.ui.theme.MonrSurfaceDark
import com.example.ui.theme.MonrSurfaceVariantDark
import com.example.ui.theme.MonrTextPrimary
import com.example.ui.theme.MonrTextSecondary
import com.example.ui.theme.MonrTextTertiary
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MeshDashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                val status by viewModel.status.collectAsState()
                val capabilities by viewModel.capabilities.collectAsState()
                val missingPermissions by viewModel.missingPermissions.collectAsState()
                val carriedBundles by viewModel.carriedBundles.collectAsState()
                val peers by viewModel.peers.collectAsState()
                val pendingBundleCount by viewModel.pendingBundleCount.collectAsState()
                val totalCarriedBytes by viewModel.totalCarriedBytes.collectAsState()
                val deliveryAcks by viewModel.deliveryAcks.collectAsState()
                val cryptoSessions by viewModel.cryptoSessions.collectAsState()
                val decryptedMessages by viewModel.decryptedMessages.collectAsState()
                val chatMessages by viewModel.chatMessages.collectAsState()
                val meshError by viewModel.meshError.collectAsState()

                // Module 5 Multi-Tier States
                val wanState by viewModel.wanConnectionState.collectAsState()
                val wanPeers by viewModel.wanRelayedPeers.collectAsState()
                val hotspotConfig by viewModel.hotspotConfig.collectAsState()
                val isHotspotRunning by viewModel.isHotspotRunning.collectAsState()
                val discoveredNsdServices by viewModel.discoveredNsdServices.collectAsState()
                val isNsdAdvertising by viewModel.isNsdAdvertising.collectAsState()
                val isNsdDiscovering by viewModel.isNsdDiscovering.collectAsState()

                // Module 6 Voice & Emergency States
                val voiceTransmissionState by viewModel.voiceTransmissionState.collectAsState()
                val selectedVoiceChannel by viewModel.selectedVoiceChannel.collectAsState()
                val activeSpeakerNodeId by viewModel.activeSpeakerNodeId.collectAsState()
                val audioLevel by viewModel.audioLevel.collectAsState()
                val waveformSamples by viewModel.waveformSamples.collectAsState()
                val isSosActive by viewModel.isSosActive.collectAsState()
                val myActiveBeacon by viewModel.myActiveBeacon.collectAsState()
                val receivedSosBeacons by viewModel.receivedSosBeacons.collectAsState()

                // Module 7 Geospatial Map States
                val myLocation by viewModel.myLocation.collectAsState()
                val tacticalMarkers by viewModel.tacticalMarkers.collectAsState()
                val breadcrumbs by viewModel.breadcrumbs.collectAsState()
                val peerLocations by viewModel.peerLocations.collectAsState()
                val selectedTacticalMarker by viewModel.selectedTacticalMarker.collectAsState()

                // Module 8 Media Transfer States
                val mediaTransfers by viewModel.mediaTransfers.collectAsState()
                val selectedMediaTransfer by viewModel.selectedMediaTransfer.collectAsState()
                val selectedTransferChunks by viewModel.selectedTransferChunks.collectAsState()
                val currentBandwidthProfile by viewModel.currentBandwidthProfile.collectAsState()
                val mediaTransferStats by viewModel.mediaTransferStats.collectAsState()

                // Module 9 Reputation & Anti-Spam States
                val peerReputations by viewModel.peerReputations.collectAsState(initial = emptyList())
                val quarantinedPeers by viewModel.quarantinedPeers.collectAsState(initial = emptyList())
                val reputationAuditLogs by viewModel.reputationAuditLogs.collectAsState(initial = emptyList())
                val securityMetrics by viewModel.securityMetrics.collectAsState()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    viewModel.refreshPermissions()
                    if (RadioPermissionManager.hasAllMeshPermissions(this@MainActivity)) {
                        viewModel.startMeshService()
                    }
                }

                val audioPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {
                    viewModel.refreshPermissions()
                }

                val hasAudioPermission = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                val activityContext = this
                LaunchedEffect(Unit) {
                    viewModel.refreshPermissions()
                }
                LaunchedEffect(missingPermissions) {
                    if (RadioPermissionManager.hasAllMeshPermissions(activityContext)) {
                        viewModel.startMeshService()
                    }
                }

                MonrAppScaffold(
                    isDarkTheme = isDarkTheme,
                    onToggleDarkTheme = { isDarkTheme = !isDarkTheme },
                    status = status,
                    capabilities = capabilities,
                    missingPermissions = missingPermissions,
                    nodeFingerprint = viewModel.nodeFingerprint,
                    cryptoSessions = cryptoSessions,
                    decryptedMessages = decryptedMessages,
                    chatMessages = chatMessages,
                    meshError = meshError,
                    carriedBundles = carriedBundles,
                    peers = peers,
                    pendingBundleCount = pendingBundleCount,
                    totalCarriedBytes = totalCarriedBytes ?: 0L,
                    deliveryAcks = deliveryAcks,
                    wanState = wanState,
                    wanPeers = wanPeers,
                    hotspotConfig = hotspotConfig,
                    isHotspotRunning = isHotspotRunning,
                    discoveredNsdServices = discoveredNsdServices,
                    isNsdAdvertising = isNsdAdvertising,
                    isNsdDiscovering = isNsdDiscovering,
                    voiceTransmissionState = voiceTransmissionState,
                    selectedVoiceChannel = selectedVoiceChannel,
                    activeSpeakerNodeId = activeSpeakerNodeId,
                    audioLevel = audioLevel,
                    waveformSamples = waveformSamples,
                    isSosActive = isSosActive,
                    myActiveBeacon = myActiveBeacon,
                    receivedSosBeacons = receivedSosBeacons,
                    hasAudioPermission = hasAudioPermission,
                    myLocation = myLocation,
                    tacticalMarkers = tacticalMarkers,
                    breadcrumbs = breadcrumbs,
                    peerLocations = peerLocations,
                    selectedTacticalMarker = selectedTacticalMarker,
                    onCreateMarker = { type, title, desc, lat, lon ->
                        viewModel.createTacticalMarker(type, title, desc, lat, lon)
                    },
                    onToggleResolveMarker = { markerId ->
                        viewModel.toggleMarkerResolved(markerId)
                    },
                    onDeleteMarker = { markerId ->
                        viewModel.deleteTacticalMarker(markerId)
                    },
                    onSelectMarker = { marker ->
                        viewModel.selectTacticalMarker(marker)
                    },
                    onSimulateMapStep = { viewModel.simulateMapStep() },
                    onSimulatePeerGps = { viewModel.simulatePeerGpsMovements() },
                    onClearBreadcrumbs = { viewModel.clearBreadcrumbTrail() },
                    onRequestPermissions = {
                        val perms = RadioPermissionManager.getRequiredMeshPermissions()
                        permissionLauncher.launch(perms)
                    },
                    onRequestAudioPermission = {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStartService = { viewModel.startMeshService() },
                    onStopService = { viewModel.stopMeshService() },
                    onSelectTier = { viewModel.selectTransportTier(it) },
                    onToggleVoip = { viewModel.toggleVoipCall() },
                    onSelectVoiceChannel = { viewModel.selectVoiceChannel(it) },
                    onStartPttTransmitting = { viewModel.startPttTransmitting() },
                    onStopPttTransmitting = { viewModel.stopPttTransmitting() },
                    onTriggerSos = { level, msg -> viewModel.triggerEmergencySos(level, msg) },
                    onCancelSos = { viewModel.cancelEmergencySos() },
                    onSimulateIncomingVoice = { viewModel.simulateIncomingVoiceCall() },
                    onSimulateIncomingSos = { viewModel.simulateIncomingSos() },
                    onTestAlarmSiren = { viewModel.testAlarmSiren() },
                    onInjectBundle = { dest, payload, priority ->
                        viewModel.injectTestBundle(dest, payload, priority)
                    },
                    onAckBundle = { bundleId, dest ->
                        viewModel.acknowledgeBundle(bundleId, dest)
                    },
                    onSimulatePeer = { nodeId, alias, link, rssi ->
                        viewModel.recordDiscoveredPeer(nodeId, alias, link, rssi)
                    },
                    onRunMaintenance = { viewModel.runMaintenance() },
                    onSimulateRatchet = { alias, text ->
                        viewModel.simulateRatchetExchange(alias, text)
                    },
                    onSendChatMessage = { peerId, text ->
                        viewModel.sendChatMessage(peerId, text)
                    },
                    onConnectWan = { viewModel.connectCloudWan() },
                    onDisconnectWan = { viewModel.disconnectCloudWan() },
                    onSimulateWanRelay = { viewModel.simulateCloudWanRelay() },
                    onStartHotspot = { viewModel.startLocalHotspot() },
                    onStopHotspot = { viewModel.stopLocalHotspot() },
                    onToggleNsdAdvertising = { viewModel.toggleNsdAdvertising() },
                    onToggleNsdDiscovery = { viewModel.toggleNsdDiscovery() },
                    mediaTransfers = mediaTransfers,
                    selectedMediaTransfer = selectedMediaTransfer,
                    selectedTransferChunks = selectedTransferChunks,
                    currentBandwidthProfile = currentBandwidthProfile,
                    mediaTransferStats = mediaTransferStats,
                    onSelectMediaTransfer = viewModel::selectMediaTransfer,
                    onSendPresetMedia = viewModel::sendPresetMedia,
                    onSendCustomMedia = viewModel::sendCustomMedia,
                    onPauseMediaTransfer = viewModel::pauseMediaTransfer,
                    onResumeMediaTransfer = viewModel::resumeMediaTransfer,
                    onCancelMediaTransfer = viewModel::cancelMediaTransfer,
                    onDeleteMediaTransfer = viewModel::deleteMediaTransfer,
                    onRequestSelectiveRepair = viewModel::requestSelectiveChunkRepair,
                    onSetBandwidthProfile = viewModel::setBandwidthProfile,
                    peerReputations = peerReputations,
                    quarantinedPeers = quarantinedPeers,
                    reputationAuditLogs = reputationAuditLogs,
                    securityMetrics = securityMetrics,
                    onToggleQuarantine = viewModel::togglePeerQuarantine,
                    onBroadcastGossip = viewModel::broadcastReputationAttestation,
                    onSimulateFloodAttack = viewModel::simulateSpamFloodAttack,
                    onSimulateSolvePow = viewModel::simulateSolvePowChallenge,
                    onSimulatePositiveEndorsement = viewModel::simulatePositiveEndorsement
                )
            }
        }
    }
}

// Navigation destinations for Monr bottom navigation bar
enum class MonrNavDestination(val label: String, val icon: ImageVector) {
    NETWORK("Network", Icons.Default.Home),
    CHAT("Chats", Icons.Default.Chat),
    PEERS("Peers", Icons.Default.Group),
    SETTINGS("Settings", Icons.Default.Settings),
    MAP("Map", Icons.Default.Explore),
    MORE("Modules", Icons.Default.Sensors)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonrAppScaffold(
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    status: RadioStatus,
    capabilities: RadioCapabilities,
    missingPermissions: List<String>,
    nodeFingerprint: String = "ABCD:1234:5678:90EF",
    cryptoSessions: List<RatchetSessionState> = emptyList(),
    decryptedMessages: List<String> = emptyList(),
    chatMessages: List<ChatMessageEntity> = emptyList(),
    meshError: String? = null,
    carriedBundles: List<DtnBundleEntity> = emptyList(),
    peers: List<MeshPeerEntity> = emptyList(),
    pendingBundleCount: Int = 0,
    totalCarriedBytes: Long = 0L,
    deliveryAcks: List<DeliveryAckEntity> = emptyList(),
    wanState: WanConnectionState = WanConnectionState.DISCONNECTED,
    wanPeers: Set<String> = emptySet(),
    hotspotConfig: HotspotConfig? = null,
    isHotspotRunning: Boolean = false,
    discoveredNsdServices: Map<String, DiscoveredNsdService> = emptyMap(),
    isNsdAdvertising: Boolean = false,
    isNsdDiscovering: Boolean = false,
    voiceTransmissionState: VoiceTransmissionState = VoiceTransmissionState.IDLE,
    selectedVoiceChannel: VoiceChannel = VoiceChannel.CHANNEL_ALL_CALL,
    activeSpeakerNodeId: String? = null,
    audioLevel: Float = 0f,
    waveformSamples: List<Float> = emptyList(),
    isSosActive: Boolean = false,
    myActiveBeacon: EmergencyBeacon? = null,
    receivedSosBeacons: List<EmergencyBeacon> = emptyList(),
    hasAudioPermission: Boolean = true,
    myLocation: NodeLocationTelemetry = NodeLocationTelemetry("node", "Node", 37.7749, -122.4194),
    tacticalMarkers: List<TacticalMarker> = emptyList(),
    breadcrumbs: List<BreadcrumbTrackPoint> = emptyList(),
    peerLocations: Map<String, NodeLocationTelemetry> = emptyMap(),
    selectedTacticalMarker: TacticalMarker? = null,
    onCreateMarker: (TacticalMarkerType, String, String, Double, Double) -> Unit = { _, _, _, _, _ -> },
    onToggleResolveMarker: (String) -> Unit = {},
    onDeleteMarker: (String) -> Unit = {},
    onSelectMarker: (TacticalMarker?) -> Unit = {},
    onSimulateMapStep: () -> Unit = {},
    onSimulatePeerGps: () -> Unit = {},
    onClearBreadcrumbs: () -> Unit = {},
    onRequestPermissions: () -> Unit,
    onRequestAudioPermission: () -> Unit = {},
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onSelectTier: (TransportTier) -> Unit,
    onToggleVoip: () -> Unit,
    onSelectVoiceChannel: (VoiceChannel) -> Unit = {},
    onStartPttTransmitting: () -> Unit = {},
    onStopPttTransmitting: () -> Unit = {},
    onTriggerSos: (EmergencyDistressLevel, String) -> Unit = { _, _ -> },
    onCancelSos: () -> Unit = {},
    onSimulateIncomingVoice: () -> Unit = {},
    onSimulateIncomingSos: () -> Unit = {},
    onTestAlarmSiren: () -> Unit = {},
    onInjectBundle: (destination: String, payload: String, priority: BundlePriority) -> Unit = { _, _, _ -> },
    onAckBundle: (bundleId: String, dest: String) -> Unit = { _, _ -> },
    onSimulatePeer: (nodeId: String, alias: String, link: String, rssi: Int) -> Unit = { _, _, _, _ -> },
    onRunMaintenance: () -> Unit = {},
    onSimulateRatchet: (remoteAlias: String, plaintext: String) -> Unit = { _, _ -> },
    onSendChatMessage: (peerNodeId: String, plaintext: String) -> Unit = { _, _ -> },
    onConnectWan: () -> Unit = {},
    onDisconnectWan: () -> Unit = {},
    onSimulateWanRelay: () -> Unit = {},
    onStartHotspot: () -> Unit = {},
    onStopHotspot: () -> Unit = {},
    onToggleNsdAdvertising: () -> Unit = {},
    onToggleNsdDiscovery: () -> Unit = {},
    mediaTransfers: List<MediaTransferEntity> = emptyList(),
    selectedMediaTransfer: MediaTransferEntity? = null,
    selectedTransferChunks: List<com.example.core.media.model.ChunkInfo> = emptyList(),
    currentBandwidthProfile: BandwidthProfile = BandwidthProfile.WIFI_AWARE_BALANCED,
    mediaTransferStats: com.example.core.media.MediaTransferStats = com.example.core.media.MediaTransferStats(),
    onSelectMediaTransfer: (MediaTransferEntity?) -> Unit = {},
    onSendPresetMedia: (MediaType, BandwidthProfile?) -> Unit = { _, _ -> },
    onSendCustomMedia: (String, ByteArray, MediaType, BandwidthProfile?) -> Unit = { _, _, _, _ -> },
    onPauseMediaTransfer: (String) -> Unit = {},
    onResumeMediaTransfer: (String) -> Unit = {},
    onCancelMediaTransfer: (String) -> Unit = {},
    onDeleteMediaTransfer: (String) -> Unit = {},
    onRequestSelectiveRepair: (String) -> Unit = {},
    onSetBandwidthProfile: (BandwidthProfile) -> Unit = {},
    peerReputations: List<PeerReputationEntity> = emptyList(),
    quarantinedPeers: List<PeerReputationEntity> = emptyList(),
    reputationAuditLogs: List<ReputationAuditLogEntity> = emptyList(),
    securityMetrics: SecurityMetrics = SecurityMetrics(),
    onToggleQuarantine: (String, Boolean, String) -> Unit = { _, _, _ -> },
    onBroadcastGossip: (String, Int, String) -> Unit = { _, _, _ -> },
    onSimulateFloodAttack: (String) -> Unit = {},
    onSimulateSolvePow: (String) -> Unit = {},
    onSimulatePositiveEndorsement: (String) -> Unit = {}
) {
    var selectedNav by remember { mutableStateOf(MonrNavDestination.NETWORK) }
    var mapViewMode by remember { mutableIntStateOf(0) }
    var signalStateOverride by remember { mutableStateOf<ConnectionSignalState?>(null) }
    var selectedSecondaryTab by remember { mutableIntStateOf(0) }
    var showInjectDialog by remember { mutableStateOf(false) }
    var chatPeerId by remember { mutableStateOf<String?>(null) }
    var chatPeerAlias by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selectedNav == MonrNavDestination.MORE) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selectedNav = MonrNavDestination.SETTINGS }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Settings",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "Mesh Modules",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            MonrBottomBar(
                selectedNav = selectedNav,
                onSelectNav = { dest ->
                    if (dest == MonrNavDestination.CHAT) {
                        chatPeerId = null
                        chatPeerAlias = null
                    }
                    selectedNav = dest
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            when (selectedNav) {
                MonrNavDestination.MAP -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header bar with Connection Status Badge & Toggle between Tactical GPS Map & Mesh Constellation
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MonrSurfaceDark)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (mapViewMode == 0) Icons.Default.Explore else Icons.Default.Hub,
                                    contentDescription = null,
                                    tint = MonrCyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (mapViewMode == 0) "Tactical Map" else "Constellation",
                                    color = MonrTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ConnectionStatusBadge(
                                    status = status,
                                    peerCount = peers.size,
                                    isHotspotRunning = isHotspotRunning,
                                    overrideState = signalStateOverride,
                                    onStateOverrideChange = { signalStateOverride = it }
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Row(
                                    modifier = Modifier
                                        .background(MonrSurfaceVariantDark, RoundedCornerShape(16.dp))
                                        .padding(2.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (mapViewMode == 0) MonrCyanAccent else Color.Transparent,
                                        modifier = Modifier
                                            .clickable { mapViewMode = 0 }
                                            .testTag("btn_mode_gps_map")
                                    ) {
                                        Text(
                                            text = "GPS",
                                            color = if (mapViewMode == 0) MonrBgDark else MonrTextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (mapViewMode == 1) MonrCyanAccent else Color.Transparent,
                                        modifier = Modifier
                                            .clickable { mapViewMode = 1 }
                                            .testTag("btn_mode_constellation")
                                    ) {
                                        Text(
                                            text = "Mesh",
                                            color = if (mapViewMode == 1) MonrBgDark else MonrTextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (mapViewMode == 0) {
                                TacticalMapTab(
                                    myLocation = myLocation,
                                    markers = tacticalMarkers,
                                    breadcrumbs = breadcrumbs,
                                    peerLocations = peerLocations,
                                    selectedMarker = selectedTacticalMarker,
                                    onSelectMarker = onSelectMarker,
                                    onCreateMarker = onCreateMarker,
                                    onToggleResolve = onToggleResolveMarker,
                                    onDeleteMarker = onDeleteMarker,
                                    onSimulateStep = onSimulateMapStep,
                                    onSimulatePeers = onSimulatePeerGps,
                                    onClearBreadcrumbs = onClearBreadcrumbs
                                )
                            } else {
                                NetworkOverviewScreen(
                                    localNodeId = nodeFingerprint,
                                    status = status,
                                    peers = peers,
                                    chatMessages = chatMessages,
                                    isHotspotRunning = isHotspotRunning,
                                    isDarkTheme = isDarkTheme,
                                    pendingBundlesCount = pendingBundleCount,
                                    cryptoSessionsCount = cryptoSessions.size,
                                    onNavigateToChat = { id, alias ->
                                        chatPeerId = id
                                        chatPeerAlias = alias
                                        selectedNav = MonrNavDestination.CHAT
                                    },
                                    onNavigateToPeers = { selectedNav = MonrNavDestination.PEERS },
                                    onStartMesh = {
                                        if (missingPermissions.isNotEmpty()) onRequestPermissions()
                                        onStartService()
                                    },
                                    onSwitchTier = onSelectTier,
                                    meshError = meshError,
                                    overrideSignalState = signalStateOverride,
                                    onOverrideSignalChange = { signalStateOverride = it }
                                )
                            }
                        }
                    }
                }

                MonrNavDestination.CHAT -> {
                    if (chatPeerId != null) {
                        MonrChatScreen(
                            nodeFingerprint = nodeFingerprint,
                            activeSessions = cryptoSessions,
                            peers = peers,
                            chatMessages = chatMessages,
                            selectedPeerId = chatPeerId,
                            selectedPeerAlias = chatPeerAlias,
                            onSendMessage = { peerId, text ->
                                onSendChatMessage(peerId, text)
                            },
                            onBackClick = {
                                chatPeerId = null
                                chatPeerAlias = null
                            },
                            radioStatus = status,
                            overrideSignalState = signalStateOverride,
                            onOverrideSignalChange = { signalStateOverride = it }
                        )
                    } else {
                        MonrConversationList(
                            peers = peers,
                            chatMessages = chatMessages,
                            onSelectConversation = { id, alias ->
                                chatPeerId = id
                                chatPeerAlias = alias
                            }
                        )
                    }
                }

                MonrNavDestination.PEERS -> {
                    MonrPeersScreen(
                        peers = peers,
                        onStartMesh = {
                            if (missingPermissions.isNotEmpty()) onRequestPermissions()
                            onStartService()
                        },
                        onStartChatWithPeer = { id, alias ->
                            chatPeerId = id
                            chatPeerAlias = alias
                            selectedNav = MonrNavDestination.CHAT
                        },
                        onBackClick = { selectedNav = MonrNavDestination.NETWORK },
                        radioStatus = status,
                        overrideSignalState = signalStateOverride,
                        onOverrideSignalChange = { signalStateOverride = it }
                    )
                }

                MonrNavDestination.NETWORK -> {
                    NetworkOverviewScreen(
                        localNodeId = nodeFingerprint,
                        status = status,
                        peers = peers,
                        chatMessages = chatMessages,
                        isHotspotRunning = isHotspotRunning,
                        isDarkTheme = isDarkTheme,
                        pendingBundlesCount = pendingBundleCount,
                        cryptoSessionsCount = cryptoSessions.size,
                        onNavigateToChat = { id, alias ->
                            chatPeerId = id
                            chatPeerAlias = alias
                            selectedNav = MonrNavDestination.CHAT
                        },
                        onNavigateToPeers = { selectedNav = MonrNavDestination.PEERS },
                        onStartMesh = {
                            if (missingPermissions.isNotEmpty()) onRequestPermissions()
                            onStartService()
                        },
                        onSwitchTier = onSelectTier,
                        meshError = meshError,
                        overrideSignalState = signalStateOverride,
                        onOverrideSignalChange = { signalStateOverride = it }
                    )
                }

                MonrNavDestination.MORE -> {
                    // Secondary Tab Row for Deep Technical Architect Modules
                    Column(modifier = Modifier.fillMaxSize()) {
                        ScrollableTabRow(
                            selectedTabIndex = selectedSecondaryTab,
                            edgePadding = 12.dp,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MonrCyanAccent,
                            divider = {
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
                            }
                        ) {
                            Tab(
                                selected = selectedSecondaryTab == 0,
                                onClick = { selectedSecondaryTab = 0 },
                                text = { Text("PTT / SOS", fontSize = 12.sp) },
                                icon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            Tab(
                                selected = selectedSecondaryTab == 1,
                                onClick = { selectedSecondaryTab = 1 },
                                text = { Text("Tactical Map (${tacticalMarkers.size})", fontSize = 12.sp) },
                                icon = { Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            Tab(
                                selected = selectedSecondaryTab == 2,
                                onClick = { selectedSecondaryTab = 2 },
                                text = { Text("Media (${mediaTransfers.size})", fontSize = 12.sp) },
                                icon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            Tab(
                                selected = selectedSecondaryTab == 3,
                                onClick = { selectedSecondaryTab = 3 },
                                text = { Text("Multi-Tier", fontSize = 12.sp) },
                                icon = { Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            Tab(
                                selected = selectedSecondaryTab == 4,
                                onClick = { selectedSecondaryTab = 4 },
                                text = { Text("DTN Queue ($pendingBundleCount)", fontSize = 12.sp) },
                                icon = { Icon(Icons.Default.AllInbox, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            Tab(
                                selected = selectedSecondaryTab == 5,
                                onClick = { selectedSecondaryTab = 5 },
                                text = { Text("E2EE (${cryptoSessions.size})", fontSize = 12.sp) },
                                icon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            Tab(
                                selected = selectedSecondaryTab == 6,
                                onClick = { selectedSecondaryTab = 6 },
                                text = { Text("Trust (${peerReputations.size})", fontSize = 12.sp) },
                                icon = { Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            Tab(
                                selected = selectedSecondaryTab == 7,
                                onClick = { selectedSecondaryTab = 7 },
                                text = { Text("Radios", fontSize = 12.sp) },
                                icon = { Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            MonrScreenBackground {
                                when (selectedSecondaryTab) {
                                0 -> {
                                    MeshVoiceTab(
                                        transmissionState = voiceTransmissionState,
                                        selectedChannel = selectedVoiceChannel,
                                        channels = VoiceChannel.DEFAULT_CHANNELS,
                                        activeSpeaker = activeSpeakerNodeId,
                                        audioLevel = audioLevel,
                                        waveformSamples = waveformSamples,
                                        isSosActive = isSosActive,
                                        myActiveBeacon = myActiveBeacon,
                                        receivedSosBeacons = receivedSosBeacons,
                                        hasAudioPermission = hasAudioPermission,
                                        onRequestAudioPermission = onRequestAudioPermission,
                                        onSelectChannel = onSelectVoiceChannel,
                                        onStartTransmitting = onStartPttTransmitting,
                                        onStopTransmitting = onStopPttTransmitting,
                                        onTriggerSos = onTriggerSos,
                                        onCancelSos = onCancelSos,
                                        onSimulateIncomingVoice = onSimulateIncomingVoice,
                                        onSimulateIncomingSos = onSimulateIncomingSos,
                                        onTestAlarmSiren = onTestAlarmSiren
                                    )
                                }

                                1 -> {
                                    TacticalMapTab(
                                        myLocation = myLocation,
                                        markers = tacticalMarkers,
                                        breadcrumbs = breadcrumbs,
                                        peerLocations = peerLocations,
                                        selectedMarker = selectedTacticalMarker,
                                        onSelectMarker = onSelectMarker,
                                        onCreateMarker = onCreateMarker,
                                        onToggleResolve = onToggleResolveMarker,
                                        onDeleteMarker = onDeleteMarker,
                                        onSimulateStep = onSimulateMapStep,
                                        onSimulatePeers = onSimulatePeerGps,
                                        onClearBreadcrumbs = onClearBreadcrumbs
                                    )
                                }

                                2 -> {
                                    MediaTransferTab(
                                        transfers = mediaTransfers,
                                        selectedTransfer = selectedMediaTransfer,
                                        selectedChunks = selectedTransferChunks,
                                        currentProfile = currentBandwidthProfile,
                                        stats = mediaTransferStats,
                                        onSelectTransfer = onSelectMediaTransfer,
                                        onSendPresetMedia = onSendPresetMedia,
                                        onSendCustomMedia = onSendCustomMedia,
                                        onPauseTransfer = onPauseMediaTransfer,
                                        onResumeTransfer = onResumeMediaTransfer,
                                        onCancelTransfer = onCancelMediaTransfer,
                                        onDeleteTransfer = onDeleteMediaTransfer,
                                        onRequestSelectiveRepair = onRequestSelectiveRepair,
                                        onSetBandwidthProfile = onSetBandwidthProfile
                                    )
                                }

                                3 -> {
                                    MultiTierNetworkTab(
                                        activeTier = status.activeTier,
                                        isInternetReachable = status.isInternetReachable,
                                        onSelectTier = onSelectTier,
                                        wanState = wanState,
                                        wanPeers = wanPeers,
                                        onConnectWan = onConnectWan,
                                        onDisconnectWan = onDisconnectWan,
                                        onSimulateWanRelay = onSimulateWanRelay,
                                        hotspotConfig = hotspotConfig,
                                        isHotspotRunning = isHotspotRunning,
                                        onStartHotspot = onStartHotspot,
                                        onStopHotspot = onStopHotspot,
                                        discoveredNsdServices = discoveredNsdServices,
                                        isNsdAdvertising = isNsdAdvertising,
                                        isNsdDiscovering = isNsdDiscovering,
                                        onToggleNsdAdvertising = onToggleNsdAdvertising,
                                        onToggleNsdDiscovery = onToggleNsdDiscovery
                                    )
                                }

                                4 -> {
                                    DtnBundlesView(
                                        bundles = carriedBundles,
                                        deliveryAcks = deliveryAcks,
                                        pendingCount = pendingBundleCount,
                                        carriedBytes = totalCarriedBytes,
                                        onInjectClick = { showInjectDialog = true },
                                        onAckBundle = onAckBundle
                                    )
                                }

                                5 -> {
                                    CryptoRatchetTab(
                                        nodeFingerprint = nodeFingerprint,
                                        sessions = cryptoSessions,
                                        decryptedMessages = decryptedMessages,
                                        onSimulateRatchet = onSimulateRatchet
                                    )
                                }

                                6 -> {
                                    PeerReputationTab(
                                        localNodeId = nodeFingerprint,
                                        reputations = peerReputations,
                                        auditLogs = reputationAuditLogs,
                                        metrics = securityMetrics,
                                        onToggleQuarantine = onToggleQuarantine,
                                        onBroadcastGossip = onBroadcastGossip,
                                        onSimulateFloodAttack = onSimulateFloodAttack,
                                        onSimulateSolvePow = onSimulateSolvePow,
                                        onSimulatePositiveEndorsement = onSimulatePositiveEndorsement
                                    )
                                }

                                7 -> {
                                    RadioLifecycleTab(
                                        status = status,
                                        capabilities = capabilities,
                                        missingPermissions = missingPermissions,
                                        meshError = meshError,
                                        onRequestPermissions = onRequestPermissions,
                                        onStartService = onStartService,
                                        onStopService = onStopService,
                                        onSelectTier = onSelectTier,
                                        onToggleVoip = onToggleVoip
                                    )
                                }
                                }
                            }
                        }
                    }
                }

                MonrNavDestination.SETTINGS -> {
                    MonrSettingsScreen(
                        nodeFingerprint = nodeFingerprint,
                        radioStatus = status,
                        isDarkTheme = isDarkTheme,
                        onToggleDarkTheme = onToggleDarkTheme,
                        onRunMaintenance = onRunMaintenance,
                        onSelectTier = onSelectTier,
                        onOpenAdvancedTab = { tabIdx ->
                            selectedNav = MonrNavDestination.MORE
                            selectedSecondaryTab = tabIdx
                        }
                    )
                }
            }
        }
    }

    if (showInjectDialog) {
        InjectBundleDialog(
            onDismiss = { showInjectDialog = false },
            onInject = { dest, payload, priority ->
                onInjectBundle(dest, payload, priority)
                showInjectDialog = false
            }
        )
    }
}
