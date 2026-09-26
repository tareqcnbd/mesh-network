package com.example.core.radio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.aware.WifiAwareManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manages the live state, broadcast monitoring, connectivity changes,
 * and dynamic transport tier evaluation for all wireless radios.
 */
class RadioLifecycleManager(
    private val context: Context,
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    companion object {
        private const val TAG = "RadioLifecycleManager"
    }

    private val hardwareDetector = RadioHardwareDetector(context)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _capabilities = MutableStateFlow(hardwareDetector.detectCapabilities())
    val capabilities: StateFlow<RadioCapabilities> = _capabilities.asStateFlow()

    private val _status = MutableStateFlow(RadioStatus())
    val status: StateFlow<RadioStatus> = _status.asStateFlow()

    private var isRegistered = false
    @Volatile
    private var manualTierOverride: TransportTier? = null

    private val radioStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let { action ->
                Log.d(TAG, "Radio broadcast received: $action")
                evaluateRadioStatus()
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            evaluateInternetStatus()
        }

        override fun onLost(network: Network) {
            evaluateInternetStatus()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            evaluateInternetStatus()
        }
    }

    /**
     * Initializes radio observation and triggers initial hardware state evaluation.
     */
    fun startMonitoring() {
        if (isRegistered) return

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                addAction(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(radioStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(radioStateReceiver, filter)
            }
            isRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register radio state receiver", e)
        }

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }

        evaluateRadioStatus()
        evaluateInternetStatus()
    }

    /**
     * Unregisters broadcast receivers and network callbacks.
     */
    fun stopMonitoring() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(radioStateReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
            isRegistered = false
        }

        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
    }

    /**
     * Updates peer and sync transfer metrics from mesh routing engine.
     */
    fun updateMeshMetrics(peerCount: Int, activeTransfers: Int, isVoipActive: Boolean) {
        _status.update { current ->
            current.copy(
                discoveredPeerCount = peerCount,
                activeTransfersCount = activeTransfers,
                isVoipActive = isVoipActive
            )
        }
    }

    /**
     * Manually overrides active transport tier. Auto-tier evaluation will not replace this
     * until [clearManualTierOverride] is called.
     */
    fun setTransportTier(tier: TransportTier) {
        manualTierOverride = tier
        _status.update { it.copy(activeTier = tier) }
    }

    fun clearManualTierOverride() {
        manualTierOverride = null
        evaluateRadioStatus()
    }

    /**
     * Updates foreground service and lock states.
     */
    fun updateServiceState(isRunning: Boolean, wakeLockHeld: Boolean, wifiLockHeld: Boolean) {
        _status.update {
            it.copy(
                isServiceRunning = isRunning,
                wakeLockHeld = wakeLockHeld,
                wifiLockHeld = wifiLockHeld
            )
        }
    }

    fun setScanningAndAdvertising(isScanning: Boolean, isAdvertising: Boolean) {
        _status.update {
            it.copy(
                isScanningActive = isScanning,
                isAdvertisingActive = isAdvertising
            )
        }
    }

    /**
     * Evaluates radio states: Bluetooth, Wi-Fi, Aware, Location.
     */
    fun evaluateRadioStatus() {
        externalScope.launch {
            // Update hardware capabilities cache
            val currentCaps = hardwareDetector.detectCapabilities()
            _capabilities.value = currentCaps

            // Evaluate Bluetooth Health
            val btHealth = when {
                !RadioPermissionManager.hasBluetoothPermissions(context) -> RadioHealth.PERMISSION_DENIED
                !currentCaps.hasBle -> RadioHealth.HARDWARE_UNSUPPORTED
                bluetoothManager?.adapter?.isEnabled == true -> RadioHealth.ACTIVE
                else -> RadioHealth.DISABLED
            }

            // Evaluate Wi-Fi Health
            val wifiHealth = when {
                !RadioPermissionManager.hasWifiPermissions(context) -> RadioHealth.PERMISSION_DENIED
                wifiManager?.isWifiEnabled == true -> RadioHealth.ACTIVE
                else -> RadioHealth.DISABLED
            }

            // Evaluate Wi-Fi Aware Health
            val awareHealth = when {
                !currentCaps.hasWifiAware -> RadioHealth.HARDWARE_UNSUPPORTED
                !RadioPermissionManager.hasWifiPermissions(context) -> RadioHealth.PERMISSION_DENIED
                hardwareDetector.isWifiAwareReady() -> RadioHealth.ACTIVE
                else -> RadioHealth.DISABLED
            }

            // Evaluate Location provider (required on Android <= 11 for radio discovery)
            val locationEnabled = try {
                locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                        locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            } catch (e: Exception) {
                false
            }
            val locationHealth = if (locationEnabled) RadioHealth.ACTIVE else RadioHealth.DISABLED

            _status.update { current ->
                val autoTier = determineOptimalTier(current.isInternetReachable, btHealth, wifiHealth)
                current.copy(
                    bluetoothHealth = btHealth,
                    wifiHealth = wifiHealth,
                    wifiAwareHealth = awareHealth,
                    locationHealth = locationHealth,
                    activeTier = manualTierOverride ?: autoTier
                )
            }
        }
    }

    private fun evaluateInternetStatus() {
        externalScope.launch {
            val isInternet = checkInternetAvailability()
            _status.update { current ->
                val autoTier = determineOptimalTier(isInternet, current.bluetoothHealth, current.wifiHealth)
                current.copy(
                    isInternetReachable = isInternet,
                    activeTier = manualTierOverride ?: autoTier
                )
            }
        }
    }

    private fun checkInternetAvailability(): Boolean {
        val activeNetwork = connectivityManager?.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Determines optimal transport tier according to the 3-tier hierarchy:
     * 1. If Internet is available -> Tier 1 (Cloud WAN / WebSocket / WebRTC STUN)
     * 2. If no Internet, but radios operational -> Tier 3 (DTN Mesh)
     * 3. Fallback: Tier 2 if requested.
     */
    private fun determineOptimalTier(
        isInternet: Boolean,
        btHealth: RadioHealth,
        wifiHealth: RadioHealth
    ): TransportTier {
        return if (isInternet) {
            TransportTier.TIER_1_INTERNET
        } else {
            TransportTier.TIER_3_MESH
        }
    }
}
