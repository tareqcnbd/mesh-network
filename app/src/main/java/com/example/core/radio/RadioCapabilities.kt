package com.example.core.radio

/**
 * Encapsulates the physical radio and hardware security capabilities
 * detected on the local Android device.
 */
data class RadioCapabilities(
    val hasBle: Boolean = false,
    val hasBleAdvertising: Boolean = false,
    val hasBleScanning: Boolean = false,
    val hasBleExtendedAdvertising: Boolean = false,
    val hasWifiAware: Boolean = false,
    val hasWifiDirect: Boolean = false,
    val hasLocalOnlyHotspot: Boolean = false,
    val hasHardwareStrongBox: Boolean = false,
    val maxBleAdvLengthBytes: Int = 31
) {
    /**
     * Determines whether this device can act as an ad-hoc mesh anchor
     * (able to advertise via BLE and support P2P/Aware data links).
     */
    val canActAsMeshAnchor: Boolean
        get() = hasBleAdvertising && (hasWifiAware || hasWifiDirect || hasLocalOnlyHotspot)

    /**
     * High-speed direct link fallback preference order.
     */
    val preferredDirectLink: DirectLinkType
        get() = when {
            hasWifiAware -> DirectLinkType.WIFI_AWARE
            hasWifiDirect -> DirectLinkType.WIFI_DIRECT
            hasLocalOnlyHotspot -> DirectLinkType.LOCAL_HOTSPOT
            else -> DirectLinkType.BLE_GATT
        }
}

enum class DirectLinkType {
    WIFI_AWARE,
    WIFI_DIRECT,
    LOCAL_HOTSPOT,
    BLE_GATT
}
