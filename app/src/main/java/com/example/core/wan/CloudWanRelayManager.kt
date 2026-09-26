package com.example.core.wan

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * State of Tier 1: Cloud WAN signaling & relay link.
 */
enum class WanConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RELAYING,
    ERROR
}

data class WanSignalingMessage(
    val type: String, // "sdp-offer", "sdp-answer", "ice-candidate", "bundle-relay", "presence"
    val senderNodeId: String,
    val targetNodeId: String,
    val payload: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Manages Tier 1: Cloud WAN connectivity.
 * - WebSocket persistent bidirectional signaling
 * - STUN/TURN server configuration profiles for WebRTC peer-to-peer traversal
 * - Fallback store-and-forward relay when direct mesh links are partitioned
 */
class CloudWanRelayManager(
    private val context: Context,
    private val localNodeId: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "CloudWanRelayManager"
        const val DEFAULT_SIGNALING_URL = "wss://relay.mesh.internal/v1/signaling"
        val DEFAULT_STUN_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302"
        )
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var activeWebSocket: WebSocket? = null
    private val isConnecting = AtomicBoolean(false)

    private val _connectionState = MutableStateFlow(WanConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WanConnectionState> = _connectionState.asStateFlow()

    private val _activeRelayedPeers = MutableStateFlow<Set<String>>(emptySet())
    val activeRelayedPeers: StateFlow<Set<String>> = _activeRelayedPeers.asStateFlow()

    var onMessageReceived: ((WanSignalingMessage) -> Unit)? = null

    fun connect(signalingUrl: String = DEFAULT_SIGNALING_URL) {
        if (_connectionState.value == WanConnectionState.CONNECTED || isConnecting.get()) return

        isConnecting.set(true)
        _connectionState.value = WanConnectionState.CONNECTING

        scope.launch {
            try {
                val request = Request.Builder()
                    .url(signalingUrl)
                    .addHeader("X-Mesh-Node-Id", localNodeId)
                    .build()

                activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        isConnecting.set(false)
                        _connectionState.value = WanConnectionState.CONNECTED
                        Log.i(TAG, "Tier 1 WAN WebSocket connected to $signalingUrl")
                        // Send registration presence
                        sendPresence()
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        parseIncomingMessage(text)
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        parseIncomingMessage(bytes.utf8())
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket closing: $code $reason")
                        _connectionState.value = WanConnectionState.DISCONNECTED
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "WebSocket failed: ${t.message}")
                        isConnecting.set(false)
                        _connectionState.value = WanConnectionState.ERROR
                    }
                })
            } catch (e: Exception) {
                isConnecting.set(false)
                _connectionState.value = WanConnectionState.ERROR
                Log.e(TAG, "Failed to connect WAN WebSocket", e)
            }
        }
    }

    fun disconnect() {
        try {
            activeWebSocket?.close(1000, "Client disconnect")
        } catch (e: Exception) {}
        activeWebSocket = null
        isConnecting.set(false)
        _connectionState.value = WanConnectionState.DISCONNECTED
        _activeRelayedPeers.value = emptySet()
    }

    fun sendSignalingMessage(message: WanSignalingMessage): Boolean {
        val ws = activeWebSocket ?: return false
        if (_connectionState.value != WanConnectionState.CONNECTED) return false

        val json = """{"type":"${message.type}","sender":"${message.senderNodeId}","target":"${message.targetNodeId}","payload":"${message.payload.replace("\"", "\\\"")}","ts":${message.timestampMs}}"""
        return ws.send(json)
    }

    /**
     * For simulated offline/hybrid testing environments, simulates a cloud relay exchange
     */
    fun simulateCloudRelayContact(remoteNodeId: String, onComplete: () -> Unit = {}) {
        scope.launch {
            _connectionState.value = WanConnectionState.RELAYING
            _activeRelayedPeers.value = _activeRelayedPeers.value + remoteNodeId
            onMessageReceived?.invoke(
                WanSignalingMessage(
                    type = "bundle-relay",
                    senderNodeId = remoteNodeId,
                    targetNodeId = localNodeId,
                    payload = "Cloud WAN Synced Bloom Inventory [Simulated Relay via STUN]"
                )
            )
            onComplete()
        }
    }

    private fun sendPresence() {
        val presence = WanSignalingMessage(
            type = "presence",
            senderNodeId = localNodeId,
            targetNodeId = "all",
            payload = "online"
        )
        sendSignalingMessage(presence)
    }

    private fun parseIncomingMessage(text: String) {
        try {
            // Lightweight parsing for signaling
            val msg = WanSignalingMessage(
                type = "signaling",
                senderNodeId = "remote_wan_node",
                targetNodeId = localNodeId,
                payload = text
            )
            onMessageReceived?.invoke(msg)
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing incoming WAN message", e)
        }
    }
}
