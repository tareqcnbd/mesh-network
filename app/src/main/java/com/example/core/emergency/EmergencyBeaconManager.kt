package com.example.core.emergency

import android.content.Context
import android.os.BatteryManager
import com.example.core.crypto.IdentityKeyManager
import com.example.core.dtn.model.BundlePriority
import com.example.core.dtn.model.BundleStatus
import com.example.core.dtn.model.DtnBundleEntity
import com.example.core.dtn.sync.DtnSyncEngine
import com.example.core.transport.packet.toHexString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manages Emergency SOS Distress Beaconing across all active mesh radio interfaces.
 * Elevates DTN bundles to highest priority (EMERGENCY) and triggers acoustic siren alerts.
 */
class EmergencyBeaconManager(
    private val context: Context,
    private val localNodeId: String,
    private val syncEngine: DtnSyncEngine,
    private val identityKeyManager: IdentityKeyManager,
    private val scope: CoroutineScope
) {
    private val _isSosActive = MutableStateFlow(false)
    val isSosActive: StateFlow<Boolean> = _isSosActive.asStateFlow()

    private val _myActiveBeacon = MutableStateFlow<EmergencyBeacon?>(null)
    val myActiveBeacon: StateFlow<EmergencyBeacon?> = _myActiveBeacon.asStateFlow()

    private val _receivedEmergencyBeacons = MutableStateFlow<List<EmergencyBeacon>>(emptyList())
    val receivedEmergencyBeacons: StateFlow<List<EmergencyBeacon>> = _receivedEmergencyBeacons.asStateFlow()

    var locationProvider: () -> Pair<Double?, Double?> = { null to null }
    var onOutboundBundle: (suspend (DtnBundleEntity) -> Unit)? = null
    var onBeaconEmitted: ((EmergencyBeacon) -> Unit)? = null
    var onCriticalAlarm: (() -> Unit)? = null

    fun triggerEmergencySos(
        level: EmergencyDistressLevel = EmergencyDistressLevel.SOS_CRITICAL,
        message: String = "EMERGENCY: Immediate assistance required. Node in distress."
    ) {
        val (lat, lon) = locationProvider()
        val beacon = EmergencyBeacon(
            beaconId = UUID.randomUUID().toString(),
            senderNodeId = localNodeId,
            senderAlias = "Node-${localNodeId.takeLast(4).uppercase()}",
            distressLevel = level,
            distressMessage = message,
            latitude = lat,
            longitude = lon,
            batteryPct = currentBatteryPct(),
            timestampMs = System.currentTimeMillis()
        )

        _isSosActive.value = true
        _myActiveBeacon.value = beacon
        onBeaconEmitted?.invoke(beacon)

        scope.launch {
            emitBeaconBundle(beacon, "sos_${beacon.beaconId}", ttlMs = 24 * 60 * 60 * 1000L)
        }
    }

    fun cancelEmergencySos() {
        val current = _myActiveBeacon.value ?: return

        val cancelBeacon = current.copy(
            isCancelled = true,
            distressMessage = "ALL-CLEAR: Emergency condition resolved. Beacon cancelled.",
            timestampMs = System.currentTimeMillis()
        )

        _isSosActive.value = false
        _myActiveBeacon.value = null
        onBeaconEmitted?.invoke(cancelBeacon)

        scope.launch {
            emitBeaconBundle(cancelBeacon, "sos_cancel_${cancelBeacon.beaconId}", ttlMs = 12 * 60 * 60 * 1000L)
        }
    }

    fun ingestReceivedBeacon(beacon: EmergencyBeacon) {
        val current = _receivedEmergencyBeacons.value.toMutableList()
        val index = current.indexOfFirst { it.beaconId == beacon.beaconId }
        if (index != -1) {
            current[index] = beacon
        } else {
            current.add(0, beacon)
            if (!beacon.isCancelled && beacon.distressLevel == EmergencyDistressLevel.SOS_CRITICAL) {
                onCriticalAlarm?.invoke()
            }
        }
        _receivedEmergencyBeacons.value = current
        onBeaconEmitted?.invoke(beacon)
    }

    fun simulateIncomingSosBeacon(peerAlias: String, level: EmergencyDistressLevel, msg: String) {
        val (lat, lon) = locationProvider()
        val simulated = EmergencyBeacon(
            beaconId = UUID.randomUUID().toString(),
            senderNodeId = "node_${peerAlias.lowercase().replace(" ", "_")}",
            senderAlias = peerAlias,
            distressLevel = level,
            distressMessage = msg,
            latitude = lat,
            longitude = lon,
            batteryPct = 24,
            timestampMs = System.currentTimeMillis()
        )
        ingestReceivedBeacon(simulated)
    }

    private suspend fun emitBeaconBundle(beacon: EmergencyBeacon, bundleId: String, ttlMs: Long) {
        val json = beacon.toJson()
        val payloadBytes = json.toByteArray(Charsets.UTF_8)
        val signature = identityKeyManager.signWithIdentity(payloadBytes)
        val entity = DtnBundleEntity(
            bundleId = bundleId,
            sourceNodeId = localNodeId,
            destinationNodeId = "*",
            isBroadcast = true,
            priority = BundlePriority.EMERGENCY,
            createdAtEpochMs = beacon.timestampMs,
            expiresAtEpochMs = beacon.timestampMs + ttlMs,
            payloadSizeBytes = payloadBytes.size.toLong(),
            encryptedPayloadHex = payloadBytes.toHexString(),
            senderSignatureHex = signature.toHexString(),
            status = BundleStatus.PENDING_CARRIED
        )
        syncEngine.storeLocalBundle(entity)
        onOutboundBundle?.invoke(entity)
    }

    private fun currentBatteryPct(): Int {
        return try {
            val battery = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 100
        } catch (_: Exception) {
            100
        }
    }
}
