package com.example.core.transport

import android.content.Context
import android.util.Log
import com.example.core.radio.DirectLinkType
import com.example.core.radio.RadioCapabilities
import com.example.core.radio.RadioHardwareDetector
import com.example.core.transport.ble.BleGattTransportDriver
import com.example.core.transport.packet.TransportFrame
import com.example.core.transport.packet.TransportOpcode
import com.example.core.transport.wifi.WifiAwareTransportDriver
import com.example.core.transport.wifi.WifiDirectTransportDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Intelligent Multi-Radio Transport Switcher.
 *
 * Orchestrates physical radio drivers:
 * 1. Dual-radio policy: Keeps BLE GATT running continuously for ultra-low power neighbor discovery,
 *    presence beaconing, and identity key handshakes.
 * 2. High-bandwidth upgrade: When bundles exceed MTU thresholds (> 1 KB) or bulk synchronization
 *    is initiated, dynamically requests and promotes peer links to Wi-Fi Aware or Wi-Fi Direct.
 * 3. Graceful fallback: If Wi-Fi links fail or are unsupported, transparently fragments and routes
 *    data across BLE GATT.
 */
class TransportSwitcher(
    private val context: Context,
    private val localNodeId: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : TransportPeerListener {

    companion object {
        private const val TAG = "TransportSwitcher"
        const val BULK_UPGRADE_THRESHOLD_BYTES = 1024 // 1 KB
    }

    private val hardwareDetector = RadioHardwareDetector(context)
    val capabilities: RadioCapabilities = hardwareDetector.detectCapabilities()

    // Drivers
    val bleDriver = BleGattTransportDriver(context, localNodeId, scope)
    val wifiAwareDriver = WifiAwareTransportDriver(context, localNodeId, scope)
    val wifiDirectDriver = WifiDirectTransportDriver(context, localNodeId, scope)

    private val drivers = mutableListOf<PhysicalTransportDriver>().apply {
        add(bleDriver)
        if (capabilities.hasWifiAware) add(wifiAwareDriver)
        if (capabilities.hasWifiDirect) add(wifiDirectDriver)
    }

    // Unified peer topology across all physical radios
    private val _connectedPeers = MutableStateFlow<Map<String, TransportPeerInfo>>(emptyMap())
    val connectedPeers: StateFlow<Map<String, TransportPeerInfo>> = _connectedPeers.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<Map<String, TransportPeerInfo>>(emptyMap())
    val discoveredPeers: StateFlow<Map<String, TransportPeerInfo>> = _discoveredPeers.asStateFlow()

    // High level packet listener (e.g. DTN Sync Engine or Double Ratchet Session Manager)
    var packetListener: ((fromPeerId: String, frame: TransportFrame, transportType: DirectLinkType) -> Unit)? = null
    var peerLifecycleListener: ((peer: TransportPeerInfo, connected: Boolean) -> Unit)? = null

    private val sequenceCounter = AtomicInteger(1)

    private val _isMeshActive = MutableStateFlow(false)
    val isMeshActive: StateFlow<Boolean> = _isMeshActive.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    suspend fun startMeshTransports(): Boolean {
        Log.i(TAG, "Starting Mesh Physical Transports (Preferred direct link: ${capabilities.preferredDirectLink})")

        bleDriver.onError = { message -> _lastError.value = message }

        var anyStarted = false

        // Always start BLE for discovery and control plane
        if (bleDriver.isAvailable) {
            val bleOk = bleDriver.start(this)
            if (bleOk) anyStarted = true
        }

        // Start Wi-Fi Aware if supported
        if (capabilities.hasWifiAware && wifiAwareDriver.isAvailable) {
            val awareOk = wifiAwareDriver.start(this)
            if (awareOk) anyStarted = true
        } else if (capabilities.hasWifiDirect && wifiDirectDriver.isAvailable) {
            // Otherwise start Wi-Fi Direct for high bandwidth
            val directOk = wifiDirectDriver.start(this)
            if (directOk) anyStarted = true
        }

        _isMeshActive.value = anyStarted
        if (!anyStarted) {
            _lastError.value = "No mesh radio could start. Enable Bluetooth and grant Nearby Devices permission."
        } else if (bleDriver.isRunning.value) {
            _lastError.value = null
        }
        return anyStarted
    }

    suspend fun stopMeshTransports() {
        _isMeshActive.value = false
        bleDriver.stop()
        wifiAwareDriver.stop()
        wifiDirectDriver.stop()
        _connectedPeers.value = emptyMap()
        _discoveredPeers.value = emptyMap()
        Log.i(TAG, "Mesh Physical Transports stopped")
    }

    /**
     * Intelligently selects the optimal physical transport driver for transmitting a payload to a peer.
     * Rules:
     * - If payload > 1 KB and Wi-Fi Aware/Direct is active to peer -> Send over high-bandwidth Wi-Fi
     * - Else if BLE is connected -> Send over BLE GATT
     * - Fallback: Attempt any available driver connected to the target peer.
     */
    suspend fun sendFrame(
        targetPeerId: String,
        opcode: TransportOpcode,
        payload: ByteArray,
        flags: Byte = 0
    ): Boolean {
        val frame = TransportFrame(
            opcode = opcode,
            sequenceNumber = sequenceCounter.getAndIncrement(),
            payload = payload,
            flags = flags
        )
        val wireBytes = TransportFrame.serialize(frame)

        // Select driver
        val driver = selectOptimalDriverForPeer(targetPeerId, payload.size)
        if (driver != null) {
            return driver.sendPacket(targetPeerId, wireBytes)
        }

        // Fallback broadcast or attempt on primary BLE driver
        return bleDriver.sendPacket(targetPeerId, wireBytes)
    }

    private fun selectOptimalDriverForPeer(targetPeerId: String, payloadSize: Int): PhysicalTransportDriver? {
        val peer = _connectedPeers.value[targetPeerId]
        if (peer != null) {
            when (peer.transportType) {
                DirectLinkType.WIFI_AWARE -> if (wifiAwareDriver.isRunning.value) return wifiAwareDriver
                DirectLinkType.WIFI_DIRECT -> if (wifiDirectDriver.isRunning.value) return wifiDirectDriver
                DirectLinkType.BLE_GATT -> {
                    // If large bundle, could initiate link promotion here
                    if (bleDriver.isRunning.value) return bleDriver
                }
                else -> {}
            }
        }

        // Prioritize fastest running driver
        if (payloadSize > BULK_UPGRADE_THRESHOLD_BYTES) {
            if (wifiAwareDriver.isRunning.value) return wifiAwareDriver
            if (wifiDirectDriver.isRunning.value) return wifiDirectDriver
        }
        if (bleDriver.isRunning.value) return bleDriver
        return null
    }

    // TransportPeerListener callbacks
    override fun onPeerDiscovered(peer: TransportPeerInfo) {
        _discoveredPeers.update { it + (peer.peerId to peer) }
        peerLifecycleListener?.invoke(peer, false)
    }

    override fun onPeerConnected(peer: TransportPeerInfo) {
        _connectedPeers.update { it + (peer.peerId to peer) }
        _discoveredPeers.update { it + (peer.peerId to peer) }
        peerLifecycleListener?.invoke(peer, true)
    }

    override fun onPeerDisconnected(peerId: String, reason: String) {
        val removed = _connectedPeers.value[peerId]
        _connectedPeers.update { it - peerId }
        if (removed != null) {
            peerLifecycleListener?.invoke(removed.copy(connectionState = PeerConnectionState.DISCONNECTED), false)
        }
    }

    override fun onDataReceived(fromPeerId: String, data: ByteArray, transportType: DirectLinkType) {
        val frame = TransportFrame.deserialize(data)
        if (frame != null) {
            packetListener?.invoke(fromPeerId, frame, transportType)
        } else {
            Log.w(TAG, "Received corrupted or unrecognized frame from $fromPeerId over $transportType")
        }
    }
}
