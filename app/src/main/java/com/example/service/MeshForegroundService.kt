package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.core.crypto.IdentityKeyManager
import com.example.core.dtn.db.MeshDatabase
import com.example.core.dtn.sync.DtnSyncEngine
import com.example.core.emergency.EmergencyBeaconManager
import com.example.core.geo.GeoMeshManager
import com.example.core.radio.RadioLifecycleManager
import com.example.core.radio.RadioPermissionManager
import com.example.core.radio.TransportTier
import com.example.core.reputation.MeshReputationManager
import com.example.core.transport.MeshLinkController
import com.example.core.transport.TransportSwitcher
import com.example.core.transport.packet.BundlePayloadRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Core Foreground Service managing continuous P2P radio discovery,
 * BLE/Wi-Fi mesh transports, battery power management, and DTN link control.
 */
class MeshForegroundService : Service() {

    companion object {
        private const val TAG = "MeshForegroundService"

        const val ACTION_START_MESH = "com.example.mesh.ACTION_START"
        const val ACTION_STOP_MESH = "com.example.mesh.ACTION_STOP"
        const val ACTION_SET_TIER = "com.example.mesh.ACTION_SET_TIER"
        const val ACTION_SET_VOIP_STATE = "com.example.mesh.ACTION_SET_VOIP"

        const val EXTRA_TIER_NAME = "extra_tier_name"
        const val EXTRA_VOIP_ACTIVE = "extra_voip_active"

        private const val WAKE_LOCK_TAG = "MeshNetwork:SyncWakeLock"
        private const val WIFI_LOCK_TAG = "MeshNetwork:WifiHighPerfLock"
        private const val WAKE_LOCK_TIMEOUT_MS = 25 * 60 * 1000L
        private const val WAKE_LOCK_RENEW_MS = 24 * 60 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_START_MESH
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_STOP_MESH
            }
            context.startService(intent)
        }
    }

    inner class MeshServiceBinder : Binder() {
        val service: MeshForegroundService
            get() = this@MeshForegroundService

        val radioLifecycleManager: RadioLifecycleManager
            get() = this@MeshForegroundService.radioManager

        val transportSwitcher: TransportSwitcher
            get() = this@MeshForegroundService.transportSwitcher

        val linkController: MeshLinkController
            get() = this@MeshForegroundService.linkController

        val geoMeshManager: GeoMeshManager
            get() = this@MeshForegroundService.geoMeshManager

        val emergencyManager: EmergencyBeaconManager
            get() = this@MeshForegroundService.emergencyManager
    }

    private val binder = MeshServiceBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var notificationHelper: ServiceNotificationHelper
    lateinit var radioManager: RadioLifecycleManager
        private set

    lateinit var transportSwitcher: TransportSwitcher
        private set

    lateinit var linkController: MeshLinkController
        private set

    lateinit var geoMeshManager: GeoMeshManager
        private set

    lateinit var emergencyManager: EmergencyBeaconManager
        private set

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLockRenewJob: Job? = null

    private var isServiceActive = false
    private var isVoipActive = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing MeshForegroundService")

        notificationHelper = ServiceNotificationHelper(this)
        radioManager = RadioLifecycleManager(applicationContext, serviceScope)

        val identityKeyManager = IdentityKeyManager(applicationContext)
        val nodeId = identityKeyManager.getIdentityFingerprint()
        val database = MeshDatabase.getInstance(applicationContext)
        val reputationManager = MeshReputationManager(
            localNodeId = nodeId,
            reputationDao = database.peerReputationDao(),
            identityKeyManager = identityKeyManager,
            scope = serviceScope
        )
        val syncEngine = DtnSyncEngine(
            bundleDao = database.dtnBundleDao(),
            ackDao = database.deliveryAckDao(),
            peerDao = database.meshPeerDao(),
            reputationManager = reputationManager
        )

        transportSwitcher = TransportSwitcher(
            context = applicationContext,
            localNodeId = nodeId,
            scope = serviceScope
        )
        geoMeshManager = GeoMeshManager(
            context = applicationContext,
            identityKeyManager = identityKeyManager,
            dtnSyncEngine = syncEngine,
            markerDao = database.tacticalMarkerDao(),
            breadcrumbDao = database.breadcrumbDao()
        )
        emergencyManager = EmergencyBeaconManager(
            context = applicationContext,
            localNodeId = nodeId,
            syncEngine = syncEngine,
            identityKeyManager = identityKeyManager,
            scope = serviceScope
        )
        emergencyManager.locationProvider = {
            val loc = geoMeshManager.myLocation.value
            loc.latitude to loc.longitude
        }
        emergencyManager.onBeaconEmitted = { beacon ->
            geoMeshManager.ingestSosBeaconAsMarker(beacon)
        }

        linkController = MeshLinkController(
            localNodeId = nodeId,
            localAlias = "Monr ${nodeId.take(4)}",
            identityKeyManager = identityKeyManager,
            syncEngine = syncEngine,
            chatDao = database.chatMessageDao(),
            transportSwitcher = transportSwitcher,
            scope = serviceScope
        )
        linkController.payloadRouter = BundlePayloadRouter(
            onChat = { bundle, text -> linkController.ingestChatMessage(bundle, text) },
            onSos = { beacon -> emergencyManager.ingestReceivedBeacon(beacon) },
            onTelemetry = { telemetry -> geoMeshManager.updatePeerTelemetry(telemetry) },
            onMarker = { marker -> geoMeshManager.ingestIncomingMarker(marker) }
        )
        val flood: suspend (com.example.core.dtn.model.DtnBundleEntity) -> Unit = { bundle ->
            linkController.floodBundle(bundle, excludeTransportPeerId = null)
        }
        geoMeshManager.onOutboundBundle = flood
        emergencyManager.onOutboundBundle = flood
        linkController.attach()
        linkController.onMeshError = { message ->
            Log.w(TAG, message)
        }

        setupLocks()
        radioManager.startMonitoring()

        transportSwitcher.connectedPeers
            .onEach { peers ->
                radioManager.updateMeshMetrics(
                    peerCount = peers.size,
                    activeTransfers = radioManager.status.value.activeTransfersCount,
                    isVoipActive = isVoipActive
                )
            }
            .launchIn(serviceScope)

        transportSwitcher.isMeshActive
            .onEach { active ->
                radioManager.setScanningAndAdvertising(active, active)
            }
            .launchIn(serviceScope)

        radioManager.status
            .onEach { status ->
                if (isServiceActive) {
                    notificationHelper.updateNotification(status)
                }
            }
            .launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MESH, null -> {
                startMeshService()
            }
            ACTION_STOP_MESH -> {
                stopMeshService()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_TIER -> {
                val tierName = intent.getStringExtra(EXTRA_TIER_NAME)
                if (tierName != null) {
                    try {
                        val tier = TransportTier.valueOf(tierName)
                        radioManager.setTransportTier(tier)
                    } catch (e: IllegalArgumentException) {
                        Log.w(TAG, "Unknown tier specified: $tierName")
                    }
                }
            }
            ACTION_SET_VOIP_STATE -> {
                val voipActive = intent.getBooleanExtra(EXTRA_VOIP_ACTIVE, false)
                setVoipActive(voipActive)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Destroying MeshForegroundService and releasing hardware resources")
        stopMeshService()
        radioManager.stopMonitoring()
        releaseLocks()
        serviceScope.cancel()
    }

    fun retryTransports() {
        serviceScope.launch { ensureTransportsStarted() }
    }

    private fun startMeshService() {
        if (isServiceActive) {
            serviceScope.launch { ensureTransportsStarted() }
            return
        }

        acquireLocks()
        isServiceActive = true

        val initialStatus = radioManager.status.value
        val notification = notificationHelper.buildNotification(initialStatus)

        var serviceType = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (isVoipActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        }

        try {
            ServiceCompat.startForeground(
                this,
                ServiceNotificationHelper.NOTIFICATION_ID,
                notification,
                serviceType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground: ${e.message}", e)
        }

        radioManager.updateServiceState(
            isRunning = true,
            wakeLockHeld = wakeLock?.isHeld == true,
            wifiLockHeld = wifiLock?.isHeld == true
        )

        serviceScope.launch { ensureTransportsStarted() }
        startGpsIfPermitted()
    }

    private suspend fun ensureTransportsStarted() {
        if (!RadioPermissionManager.hasBluetoothPermissions(this)) {
            Log.w(TAG, "Bluetooth permissions missing; mesh radios not started")
            return
        }
        val started = transportSwitcher.startMeshTransports()
        radioManager.setScanningAndAdvertising(started, started)
        if (!started) {
            Log.w(TAG, "Mesh transports failed to start: ${transportSwitcher.lastError.value}")
        }
        startGpsIfPermitted()
    }

    private fun startGpsIfPermitted() {
        if (RadioPermissionManager.hasFineLocationPermission(this)) {
            geoMeshManager.startGpsTracking()
        }
    }

    private fun stopMeshService() {
        isServiceActive = false
        wakeLockRenewJob?.cancel()
        wakeLockRenewJob = null
        serviceScope.launch {
            transportSwitcher.stopMeshTransports()
        }
        releaseLocks()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        radioManager.updateServiceState(
            isRunning = false,
            wakeLockHeld = false,
            wifiLockHeld = false
        )
        radioManager.setScanningAndAdvertising(false, false)
    }

    fun setVoipActive(active: Boolean) {
        if (isVoipActive == active) return
        isVoipActive = active

        radioManager.updateMeshMetrics(
            peerCount = radioManager.status.value.discoveredPeerCount,
            activeTransfers = radioManager.status.value.activeTransfersCount,
            isVoipActive = active
        )

        if (isServiceActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }

            try {
                val notification = notificationHelper.buildNotification(radioManager.status.value)
                ServiceCompat.startForeground(
                    this,
                    ServiceNotificationHelper.NOTIFICATION_ID,
                    notification,
                    serviceType
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not update foreground service type for VoIP: ${e.message}")
            }
        }
    }

    private fun setupLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        )?.apply {
            setReferenceCounted(false)
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiLock = wifiManager?.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            WIFI_LOCK_TAG
        )?.apply {
            setReferenceCounted(false)
        }
    }

    private fun acquireLocks() {
        try {
            acquireWakeLock()
            if (wifiLock?.isHeld != true) {
                wifiLock?.acquire()
            }
            startWakeLockRenewal()
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring system locks", e)
        }
    }

    private fun acquireWakeLock() {
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun startWakeLockRenewal() {
        wakeLockRenewJob?.cancel()
        wakeLockRenewJob = serviceScope.launch {
            while (isActive && isServiceActive) {
                delay(WAKE_LOCK_RENEW_MS)
                if (isServiceActive) {
                    try {
                        acquireWakeLock()
                        radioManager.updateServiceState(
                            isRunning = true,
                            wakeLockHeld = wakeLock?.isHeld == true,
                            wifiLockHeld = wifiLock?.isHeld == true
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Wake lock renew failed", e)
                    }
                }
            }
        }
    }

    private fun releaseLocks() {
        wakeLockRenewJob?.cancel()
        wakeLockRenewJob = null
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake lock", e)
        }

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Wi-Fi lock", e)
        }
    }
}
