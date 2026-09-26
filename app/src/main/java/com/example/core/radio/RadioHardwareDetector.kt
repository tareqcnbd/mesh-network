package com.example.core.radio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.aware.WifiAwareManager
import android.os.Build

/**
 * Probes hardware capabilities and platform features to construct
 * a definitive [RadioCapabilities] profile for runtime routing decisions.
 */
class RadioHardwareDetector(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val wifiAwareManager: WifiAwareManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
        } else {
            null
        }

    fun detectCapabilities(): RadioCapabilities {
        val hasBle = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

        var hasBleAdv = false
        var hasBleExtAdv = false
        var maxAdvLength = 31

        if (hasBle && bluetoothAdapter != null) {
            hasBleAdv = bluetoothAdapter.isMultipleAdvertisementSupported
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                hasBleExtAdv = bluetoothAdapter.isLeExtendedAdvertisingSupported
                if (hasBleExtAdv) {
                    maxAdvLength = bluetoothAdapter.leMaximumAdvertisingDataLength
                }
            }
        }

        val hasWifiAware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE) &&
                    (wifiAwareManager?.isAvailable == true)
        } else {
            false
        }

        val hasWifiDirect = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        val hasLocalHotspot = wifiManager != null

        val hasStrongBox = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        } else {
            false
        }

        return RadioCapabilities(
            hasBle = hasBle,
            hasBleAdvertising = hasBleAdv,
            hasBleScanning = hasBle,
            hasBleExtendedAdvertising = hasBleExtAdv,
            hasWifiAware = hasWifiAware,
            hasWifiDirect = hasWifiDirect,
            hasLocalOnlyHotspot = hasLocalHotspot,
            hasHardwareStrongBox = hasStrongBox,
            maxBleAdvLengthBytes = maxAdvLength
        )
    }

    /**
     * Re-evaluates dynamic Wi-Fi Aware hardware readiness (which can toggle dynamically
     * based on Wi-Fi state and location service status).
     */
    fun isWifiAwareReady(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE) &&
                    (wifiAwareManager?.isAvailable == true)
        } else {
            false
        }
    }
}
