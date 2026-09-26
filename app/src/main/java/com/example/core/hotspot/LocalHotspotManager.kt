package com.example.core.hotspot

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.core.radio.RadioPermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Tier 2: Local-Only Hotspot (AP tethering).
 *
 * Provides fallback local wireless AP on Android 8.0+ (API 26+) using
 * [WifiManager.startLocalOnlyHotspot].
 * Allows 2-8 neighbor devices to join an ad-hoc Wi-Fi network without Internet access.
 */
data class HotspotConfig(
    val ssid: String,
    val passphrase: String,
    val isRunning: Boolean = false,
    val connectedClientsCount: Int = 0
)

class LocalHotspotManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "LocalHotspotManager"
    }

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null

    private val _hotspotConfig = MutableStateFlow<HotspotConfig?>(null)
    val hotspotConfig: StateFlow<HotspotConfig?> = _hotspotConfig.asStateFlow()

    private val _isHotspotRunning = MutableStateFlow(false)
    val isHotspotRunning: StateFlow<Boolean> = _isHotspotRunning.asStateFlow()

    val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                wifiManager != null &&
                RadioPermissionManager.hasWifiPermissions(context)

    fun startHotspot(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (_isHotspotRunning.value) {
            onResult(true, "Hotspot already active")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || wifiManager == null) {
            onResult(false, "Local-Only Hotspot requires Android 8.0+ and Wi-Fi hardware")
            return
        }

        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                    hotspotReservation = reservation
                    _isHotspotRunning.value = true

                    val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        reservation?.softApConfiguration?.let { softAp ->
                            HotspotConfig(
                                ssid = softAp.ssid ?: "Mesh-Hotspot",
                                passphrase = softAp.passphrase ?: "meshpass123",
                                isRunning = true
                            )
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        reservation?.wifiConfiguration?.let { wifiConfig ->
                            HotspotConfig(
                                ssid = wifiConfig.SSID ?: "Mesh-Hotspot",
                                passphrase = wifiConfig.preSharedKey ?: "meshpass123",
                                isRunning = true
                            )
                        }
                    }

                    val finalConfig = config ?: HotspotConfig(
                        ssid = "Mesh-Local-AP",
                        passphrase = "meshpassphrase",
                        isRunning = true
                    )

                    _hotspotConfig.value = finalConfig
                    Log.i(TAG, "Local-Only Hotspot started: SSID=${finalConfig.ssid}")
                    onResult(true, null)
                }

                override fun onStopped() {
                    _isHotspotRunning.value = false
                    _hotspotConfig.value = null
                    hotspotReservation = null
                    Log.i(TAG, "Local-Only Hotspot stopped")
                }

                override fun onFailed(reason: Int) {
                    _isHotspotRunning.value = false
                    _hotspotConfig.value = null
                    val errorMsg = when (reason) {
                        ERROR_NO_CHANNEL -> "No frequency channel available"
                        ERROR_GENERIC -> "Generic hotspot failure"
                        ERROR_INCOMPATIBLE_MODE -> "Incompatible mode (Tethering already active?)"
                        ERROR_TETHERING_DISALLOWED -> "Tethering disallowed by carrier/admin policy"
                        else -> "Failed with error code: $reason"
                    }
                    Log.w(TAG, "Local-Only Hotspot failed: $errorMsg")
                    onResult(false, errorMsg)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing NEARBY_WIFI_DEVICES or FINE_LOCATION permission for Hotspot", e)
            onResult(false, e.message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Local-Only Hotspot", e)
            onResult(false, e.message)
        }
    }

    fun stopHotspot() {
        try {
            hotspotReservation?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing hotspot reservation", e)
        } finally {
            hotspotReservation = null
            _isHotspotRunning.value = false
            _hotspotConfig.value = null
            Log.i(TAG, "Local-Only Hotspot closed")
        }
    }
}
