package com.example.core.transport

import com.example.core.radio.DirectLinkType
import kotlinx.coroutines.flow.StateFlow

/**
 * Common abstraction for all physical mesh radio transport drivers:
 * - BLE GATT (Low energy discovery, handshake, control, small bundle transfers)
 * - Wi-Fi Aware (NAN: Neighbor Awareness Networking peer-to-peer data paths)
 * - Wi-Fi Direct (P2P Group Owner / Client high-bandwidth bulk synchronization)
 * - Local-Only Hotspot (AP tethering fallback)
 */
interface PhysicalTransportDriver {
    val transportType: DirectLinkType
    val isAvailable: Boolean
    val isRunning: StateFlow<Boolean>
    val maxMtuBytes: Int

    suspend fun start(peerListener: TransportPeerListener): Boolean
    suspend fun stop()
    suspend fun sendPacket(targetPeerId: String, payload: ByteArray): Boolean
    fun getConnectedPeers(): List<TransportPeerInfo>
}

/**
 * Represents a peer reached over a physical transport link.
 */
data class TransportPeerInfo(
    val peerId: String,
    val deviceAddress: String,
    val transportType: DirectLinkType,
    val rssi: Int = -50,
    val connectionState: PeerConnectionState = PeerConnectionState.CONNECTED,
    val linkBandwidthEstimateKbps: Int = 1000,
    val connectedAtEpochMs: Long = System.currentTimeMillis()
)

enum class PeerConnectionState {
    DISCOVERED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED
}

/**
 * Event callbacks emitted by transport drivers up to the TransportSwitcher and DTN Sync Engine.
 */
interface TransportPeerListener {
    fun onPeerDiscovered(peer: TransportPeerInfo)
    fun onPeerConnected(peer: TransportPeerInfo)
    fun onPeerDisconnected(peerId: String, reason: String)
    fun onDataReceived(fromPeerId: String, data: ByteArray, transportType: DirectLinkType)
}
