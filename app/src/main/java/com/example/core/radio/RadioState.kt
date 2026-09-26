package com.example.core.radio

/**
 * Three-tier transport hierarchy as defined in the system architecture.
 */
enum class TransportTier(val displayName: String, val description: String) {
    TIER_1_INTERNET(
        displayName = "Internet",
        description = "Uses Wi-Fi or mobile data when available"
    ),
    TIER_2_HOTSPOT(
        displayName = "Phone hotspot",
        description = "This phone shares a local Wi-Fi network"
    ),
    TIER_3_MESH(
        displayName = "Nearby mesh",
        description = "Talks phone-to-phone with Bluetooth when there is no internet"
    )
}

/**
 * Health indicator for an individual radio or subsystem.
 */
enum class RadioHealth {
    ACTIVE,
    DISABLED,
    PERMISSION_DENIED,
    HARDWARE_UNSUPPORTED
}

/**
 * Snapshot of radio system metrics and operational mode.
 */
data class RadioStatus(
    val bluetoothHealth: RadioHealth = RadioHealth.DISABLED,
    val wifiHealth: RadioHealth = RadioHealth.DISABLED,
    val wifiAwareHealth: RadioHealth = RadioHealth.HARDWARE_UNSUPPORTED,
    val locationHealth: RadioHealth = RadioHealth.DISABLED,
    val isInternetReachable: Boolean = false,
    val activeTier: TransportTier = TransportTier.TIER_3_MESH,
    val isServiceRunning: Boolean = false,
    val isScanningActive: Boolean = false,
    val isAdvertisingActive: Boolean = false,
    val discoveredPeerCount: Int = 0,
    val activeTransfersCount: Int = 0,
    val isVoipActive: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val wakeLockHeld: Boolean = false,
    val wifiLockHeld: Boolean = false
) {
    val isMeshOperational: Boolean
        get() = (bluetoothHealth == RadioHealth.ACTIVE) && (wifiHealth == RadioHealth.ACTIVE)
}
