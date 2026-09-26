package com.example.core.transport.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import com.example.core.radio.DirectLinkType
import com.example.core.radio.RadioPermissionManager
import com.example.core.transport.PeerConnectionState
import com.example.core.transport.PhysicalTransportDriver
import com.example.core.transport.TransportPeerInfo
import com.example.core.transport.TransportPeerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Wi-Fi Direct (P2P) Transport Driver.
 * Provides fallback high-bandwidth link when Wi-Fi Aware is unsupported by device hardware.
 *
 * Implements:
 * 1. Autonomous Group Formation & Peer Discovery.
 * 2. Group Owner (GO) TCP Server vs Client Socket negotiation.
 * 3. Bidirectional streaming socket connection for bulk DTN synchronization.
 */
class WifiDirectTransportDriver(
    private val context: Context,
    private val localNodeId: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : PhysicalTransportDriver {

    companion object {
        private const val TAG = "WifiDirectDriver"
        const val P2P_PORT = 48891
    }

    override val transportType: DirectLinkType = DirectLinkType.WIFI_DIRECT

    private val p2pManager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var p2pChannel: WifiP2pManager.Channel? = null

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    override val isAvailable: Boolean
        get() = p2pManager != null && RadioPermissionManager.hasWifiPermissions(context)

    override val maxMtuBytes: Int = 65535

    private val activePeers = ConcurrentHashMap<String, TransportPeerInfo>()
    private val activeSockets = ConcurrentHashMap<String, Socket>()
    private var serverSocket: ServerSocket? = null

    private var peerListener: TransportPeerListener? = null
    private var isReceiverRegistered = false

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestPeers()
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        requestConnectionInfo()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun start(listener: TransportPeerListener): Boolean {
        if (_isRunning.value) return true
        if (!isAvailable) {
            Log.w(TAG, "Wi-Fi Direct not supported or permissions missing")
            return false
        }

        this.peerListener = listener

        try {
            p2pChannel = p2pManager?.initialize(context, Looper.getMainLooper(), null)
            registerReceiver()

            p2pManager?.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Wi-Fi Direct discovery initiated")
                }

                override fun onFailure(reasonCode: Int) {
                    Log.w(TAG, "Wi-Fi Direct discovery failed: $reasonCode")
                }
            })

            _isRunning.value = true
            Log.i(TAG, "Wi-Fi Direct Transport Driver started")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Wi-Fi Direct transport", e)
            stop()
            return false
        }
    }

    override suspend fun stop() {
        _isRunning.value = false
        unregisterReceiver()

        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null

        activeSockets.values.forEach { socket ->
            try { socket.close() } catch (e: Exception) {}
        }
        activeSockets.clear()
        activePeers.clear()

        Log.i(TAG, "Wi-Fi Direct Transport Driver stopped")
    }

    override suspend fun sendPacket(targetPeerId: String, payload: ByteArray): Boolean {
        if (!_isRunning.value) return false

        val socket = activeSockets[targetPeerId]
        if (socket != null && !socket.isClosed) {
            return try {
                val out = socket.getOutputStream()
                out.write(payload)
                out.flush()
                true
            } catch (e: Exception) {
                Log.w(TAG, "P2P write failed for $targetPeerId", e)
                false
            }
        }
        return false
    }

    override fun getConnectedPeers(): List<TransportPeerInfo> {
        return activePeers.values.toList()
    }

    private fun registerReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        try {
            context.registerReceiver(p2pReceiver, filter)
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Error registering P2P receiver", e)
        }
    }

    private fun unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(p2pReceiver)
            } catch (e: Exception) {}
            isReceiverRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        val channel = p2pChannel ?: return
        p2pManager?.requestPeers(channel) { peerList: WifiP2pDeviceList? ->
            peerList?.deviceList?.forEach { device ->
                val peerId = "p2p_" + device.deviceAddress.replace(":", "").lowercase()
                val peerInfo = TransportPeerInfo(
                    peerId = peerId,
                    deviceAddress = device.deviceAddress,
                    transportType = DirectLinkType.WIFI_DIRECT,
                    rssi = -55,
                    connectionState = PeerConnectionState.DISCOVERED,
                    linkBandwidthEstimateKbps = 25000 // 25 Mbps
                )
                peerListener?.onPeerDiscovered(peerInfo)
            }
        }
    }

    private fun requestConnectionInfo() {
        val channel = p2pChannel ?: return
        p2pManager?.requestConnectionInfo(channel) { info: WifiP2pInfo? ->
            if (info == null) return@requestConnectionInfo
            if (info.groupFormed) {
                if (info.isGroupOwner) {
                    // Start Group Owner Server
                    startGoServer()
                } else {
                    // Connect as Client to Group Owner IP
                    val goIp = info.groupOwnerAddress.hostAddress
                    connectToGroupOwner(goIp)
                }
            }
        }
    }

    private fun startGoServer() {
        scope.launch {
            try {
                serverSocket = ServerSocket(P2P_PORT)
                while (_isRunning.value) {
                    val socket = serverSocket?.accept() ?: break
                    handleClientSocket(socket)
                }
            } catch (e: Exception) {
                if (_isRunning.value) Log.w(TAG, "P2P Server error: ${e.message}")
            }
        }
    }

    private fun connectToGroupOwner(goAddress: String) {
        scope.launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(goAddress, P2P_PORT), 5000)
                handleClientSocket(socket)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to connect to P2P GO at $goAddress", e)
            }
        }
    }

    private fun handleClientSocket(socket: Socket) {
        scope.launch {
            val peerId = "p2p_node_" + socket.inetAddress.hostAddress
            activeSockets[peerId] = socket
            val peerInfo = TransportPeerInfo(
                peerId = peerId,
                deviceAddress = socket.inetAddress.hostAddress,
                transportType = DirectLinkType.WIFI_DIRECT,
                rssi = -50,
                connectionState = PeerConnectionState.CONNECTED,
                linkBandwidthEstimateKbps = 25000
            )
            activePeers[peerId] = peerInfo
            peerListener?.onPeerConnected(peerInfo)

            try {
                val input: InputStream = socket.getInputStream()
                val buffer = ByteArray(4096)
                while (_isRunning.value) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead <= 0) break
                    val packet = buffer.copyOf(bytesRead)
                    peerListener?.onDataReceived(peerId, packet, DirectLinkType.WIFI_DIRECT)
                }
            } catch (e: Exception) {
                Log.d(TAG, "P2P Socket closed for $peerId")
            } finally {
                activeSockets.remove(peerId)
                activePeers.remove(peerId)
                peerListener?.onPeerDisconnected(peerId, "P2P connection closed")
                try { socket.close() } catch (e: Exception) {}
            }
        }
    }
}
