package com.example.core.geo

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.example.core.crypto.IdentityKeyManager
import com.example.core.dtn.model.BundlePriority
import com.example.core.dtn.model.BundleStatus
import com.example.core.dtn.model.DtnBundleEntity
import com.example.core.dtn.sync.DtnSyncEngine
import com.example.core.emergency.EmergencyBeacon
import com.example.core.geo.db.BreadcrumbDao
import com.example.core.geo.db.BreadcrumbEntity
import com.example.core.geo.db.TacticalMarkerDao
import com.example.core.geo.db.TacticalMarkerEntity
import com.example.core.transport.packet.toHexString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class NodeLocationTelemetry(
    val nodeId: String,
    val alias: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
    val accuracyMeters: Float = 5f,
    val bearingDegrees: Float = 0f,
    val speedMps: Float = 0f,
    val batteryPct: Int = 100,
    val timestampMs: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromJson(jsonStr: String): NodeLocationTelemetry? {
            return try {
                fun extractString(key: String): String? {
                    val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                    return pattern.find(jsonStr)?.groupValues?.get(1)
                }
                fun extractDouble(key: String): Double? {
                    val pattern = "\"$key\"\\s*:\\s*([0-9.-]+)".toRegex()
                    return pattern.find(jsonStr)?.groupValues?.get(1)?.toDoubleOrNull()
                }
                fun extractFloat(key: String): Float? {
                    val pattern = "\"$key\"\\s*:\\s*([0-9.-]+)".toRegex()
                    return pattern.find(jsonStr)?.groupValues?.get(1)?.toFloatOrNull()
                }
                fun extractInt(key: String): Int? {
                    val pattern = "\"$key\"\\s*:\\s*([0-9-]+)".toRegex()
                    return pattern.find(jsonStr)?.groupValues?.get(1)?.toIntOrNull()
                }
                fun extractLong(key: String): Long? {
                    val pattern = "\"$key\"\\s*:\\s*([0-9-]+)".toRegex()
                    return pattern.find(jsonStr)?.groupValues?.get(1)?.toLongOrNull()
                }
                val nodeId = extractString("nodeId") ?: return null
                val lat = extractDouble("lat") ?: return null
                val lon = extractDouble("lon") ?: return null
                NodeLocationTelemetry(
                    nodeId = nodeId,
                    alias = extractString("alias") ?: nodeId,
                    latitude = lat,
                    longitude = lon,
                    altitudeMeters = extractDouble("alt") ?: 0.0,
                    accuracyMeters = extractFloat("acc") ?: 5f,
                    bearingDegrees = extractFloat("bearing") ?: 0f,
                    speedMps = extractFloat("speed") ?: 0f,
                    batteryPct = extractInt("battery") ?: 100,
                    timestampMs = extractLong("timestampMs") ?: System.currentTimeMillis()
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

class GeoMeshManager(
    private val context: Context,
    private val identityKeyManager: IdentityKeyManager,
    private val dtnSyncEngine: DtnSyncEngine,
    private val markerDao: TacticalMarkerDao,
    private val breadcrumbDao: BreadcrumbDao
) {
    companion object {
        private const val TAG = "GeoMeshManager"
        const val PAYLOAD_TYPE_MARKER = "tactical_marker"
        const val PAYLOAD_TYPE_TELEMETRY = "node_telemetry"

        // Default initial reference coordinate (e.g. San Francisco downtown)
        const val DEFAULT_LAT = 37.7749
        const val DEFAULT_LON = -122.4194
    }

    private val localNodeId = identityKeyManager.getIdentityFingerprint()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var telemetryBroadcastJob: Job? = null
    private var locationManager: LocationManager? = null
    private var gpsListener: LocationListener? = null
    private var gpsStarted = false

    var onOutboundBundle: (suspend (DtnBundleEntity) -> Unit)? = null

    // Local Node Telemetry State
    private val _myLocation = MutableStateFlow(
        NodeLocationTelemetry(
            nodeId = localNodeId,
            alias = "My Node (${localNodeId.take(6)})",
            latitude = DEFAULT_LAT,
            longitude = DEFAULT_LON,
            altitudeMeters = 15.0,
            accuracyMeters = 3.5f,
            bearingDegrees = 45f,
            speedMps = 0f
        )
    )
    val myLocation: StateFlow<NodeLocationTelemetry> = _myLocation.asStateFlow()

    // Active Tactical Markers
    private val _markers = MutableStateFlow<List<TacticalMarker>>(emptyList())
    val markers: StateFlow<List<TacticalMarker>> = _markers.asStateFlow()

    // Breadcrumb Trail for Local Node
    private val _breadcrumbs = MutableStateFlow<List<BreadcrumbTrackPoint>>(emptyList())
    val breadcrumbs: StateFlow<List<BreadcrumbTrackPoint>> = _breadcrumbs.asStateFlow()

    // Peer Locations collected from mesh telemetry broadcasts
    private val _peerLocations = MutableStateFlow<Map<String, NodeLocationTelemetry>>(emptyMap())
    val peerLocations: StateFlow<Map<String, NodeLocationTelemetry>> = _peerLocations.asStateFlow()

    // Selected marker for inspection card
    private val _selectedMarker = MutableStateFlow<TacticalMarker?>(null)
    val selectedMarker: StateFlow<TacticalMarker?> = _selectedMarker.asStateFlow()

    init {
        // Observe active markers from database
        scope.launch {
            markerDao.getActiveMarkersFlow(System.currentTimeMillis()).collect { entities ->
                _markers.value = entities.map { it.toDomain() }
            }
        }

        // Observe breadcrumbs for local node
        scope.launch {
            breadcrumbDao.getTrackPointsFlow(localNodeId, 150).collect { entities ->
                _breadcrumbs.value = entities.map {
                    BreadcrumbTrackPoint(
                        id = it.id,
                        nodeId = it.nodeId,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        altitudeMeters = it.altitudeMeters,
                        speedMps = it.speedMps,
                        bearingDegrees = it.bearingDegrees,
                        timestampMs = it.timestampMs
                    )
                }
            }
        }

        startTelemetryBroadcastLoop()
    }

    /**
     * Attempts to start native Android GPS location updates if permissions allow.
     */
    @SuppressLint("MissingPermission")
    fun startGpsTracking() {
        if (gpsStarted) return
        try {
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val provider = when {
                locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true -> LocationManager.GPS_PROVIDER
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true -> LocationManager.NETWORK_PROVIDER
                else -> null
            }

            if (provider != null) {
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        updateMyLocation(
                            lat = loc.latitude,
                            lon = loc.longitude,
                            alt = loc.altitude,
                            acc = loc.accuracy,
                            speed = loc.speed,
                            bearing = loc.bearing
                        )
                    }
                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }
                gpsListener = listener
                locationManager?.requestLocationUpdates(
                    provider,
                    3000L, // 3 seconds
                    2f,    // 2 meters
                    listener,
                    Looper.getMainLooper()
                )
                locationManager?.getLastKnownLocation(provider)?.let { loc ->
                    updateMyLocation(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        alt = loc.altitude,
                        acc = loc.accuracy,
                        speed = loc.speed,
                        bearing = loc.bearing
                    )
                }
                gpsStarted = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "GPS tracking setup failed (running in emulator or restricted env): ${e.message}")
        }
    }

    /**
     * Updates current node's coordinates, appends breadcrumb point, and broadcasts telemetry.
     */
    fun updateMyLocation(
        lat: Double,
        lon: Double,
        alt: Double = 0.0,
        acc: Float = 4.0f,
        speed: Float = 0f,
        bearing: Float = 0f
    ) {
        val updated = _myLocation.value.copy(
            latitude = lat,
            longitude = lon,
            altitudeMeters = alt,
            accuracyMeters = acc,
            speedMps = speed,
            bearingDegrees = bearing,
            timestampMs = System.currentTimeMillis()
        )
        _myLocation.value = updated

        // Record breadcrumb point
        scope.launch {
            breadcrumbDao.insertPoint(
                BreadcrumbEntity(
                    nodeId = localNodeId,
                    latitude = lat,
                    longitude = lon,
                    altitudeMeters = alt,
                    speedMps = speed,
                    bearingDegrees = bearing,
                    timestampMs = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Simulates walking/movement to generate breadcrumbs and directional cones for testing.
     */
    fun simulateStepMovement(bearingDelta: Float = 15f) {
        val current = _myLocation.value
        val newBearing = (current.bearingDegrees + bearingDelta) % 360
        val rad = Math.toRadians(newBearing.toDouble())

        // ~35 meters step in the direction of newBearing
        val deltaLat = (35.0 / 111111.0) * kotlin.math.cos(rad)
        val deltaLon = (35.0 / (111111.0 * kotlin.math.cos(Math.toRadians(current.latitude)))) * kotlin.math.sin(rad)

        updateMyLocation(
            lat = current.latitude + deltaLat,
            lon = current.longitude + deltaLon,
            alt = current.altitudeMeters + ((-2..2).random()),
            acc = 3.2f,
            speed = 1.4f, // ~5 km/h walking speed
            bearing = newBearing
        )
    }

    /**
     * Create a new tactical marker and propagate it via DTN to the mesh.
     */
    fun createTacticalMarker(
        type: TacticalMarkerType,
        title: String,
        description: String,
        lat: Double = _myLocation.value.latitude,
        lon: Double = _myLocation.value.longitude,
        ttlHours: Int = 24
    ) {
        scope.launch {
            val marker = TacticalMarker(
                markerId = UUID.randomUUID().toString(),
                type = type,
                title = title.ifBlank { type.label },
                description = description,
                latitude = lat,
                longitude = lon,
                altitudeMeters = _myLocation.value.altitudeMeters,
                accuracyMeters = _myLocation.value.accuracyMeters,
                creatorNodeId = localNodeId,
                creatorAlias = _myLocation.value.alias,
                createdAtMs = System.currentTimeMillis(),
                expiresAtMs = System.currentTimeMillis() + (ttlHours * 3600 * 1000L),
                isResolved = false
            )

            // Save locally
            markerDao.insertOrUpdate(TacticalMarkerEntity.fromDomain(marker))

            // Broadcast via DTN store-and-forward mesh
            val payloadBytes = marker.toJson().toByteArray(Charsets.UTF_8)
            val bundle = signedBroadcast(
                payloadBytes = payloadBytes,
                priority = if (type == TacticalMarkerType.HAZARD || type == TacticalMarkerType.SOS_DISTRESS) {
                    BundlePriority.HIGH
                } else {
                    BundlePriority.NORMAL
                },
                ttlMs = ttlHours * 3600 * 1000L,
                maxHops = 5
            )
            storeAndFlood(bundle)
            Log.d(TAG, "Created tactical marker: ${marker.title} at ${marker.latitude}, ${marker.longitude}")
        }
    }

    /**
     * Toggles resolution status of a marker (e.g. hazard neutralized / supply collected).
     */
    fun toggleMarkerResolved(markerId: String) {
        scope.launch {
            val existing = markerDao.getMarkerById(markerId) ?: return@launch
            val updated = existing.copy(isResolved = !existing.isResolved)
            markerDao.insertOrUpdate(updated)

            // Broadcast update
            val domain = updated.toDomain()
            val payloadBytes = domain.toJson().toByteArray(Charsets.UTF_8)
            val bundle = signedBroadcast(
                payloadBytes = payloadBytes,
                priority = BundlePriority.NORMAL,
                ttlMs = 86_400_000L,
                maxHops = 5
            )
            storeAndFlood(bundle)
        }
    }

    /**
     * Deletes a marker from local cache.
     */
    fun deleteMarker(markerId: String) {
        scope.launch {
            markerDao.deleteById(markerId)
            if (_selectedMarker.value?.markerId == markerId) {
                _selectedMarker.value = null
            }
        }
    }

    fun selectMarker(marker: TacticalMarker?) {
        _selectedMarker.value = marker
    }

    fun clearBreadcrumbs() {
        scope.launch {
            breadcrumbDao.clearTrackForNode(localNodeId)
        }
    }

    /**
     * Ingests incoming DTN bundles or wire frames containing markers.
     */
    fun ingestIncomingPayload(payload: String) {
        val marker = TacticalMarker.fromJson(payload) ?: return
        ingestIncomingMarker(marker)
    }

    fun ingestIncomingMarker(marker: TacticalMarker) {
        scope.launch {
            markerDao.insertOrUpdate(TacticalMarkerEntity.fromDomain(marker))
            Log.d(TAG, "Ingested tactical marker: ${marker.title}")
        }
    }

    /**
     * Ingests Emergency SOS beacons directly as high-visibility tactical distress markers.
     */
    fun ingestSosBeaconAsMarker(beacon: EmergencyBeacon) {
        if (beacon.latitude == null || beacon.longitude == null) return
        scope.launch {
            val marker = TacticalMarker(
                markerId = "sos-${beacon.beaconId}",
                type = TacticalMarkerType.SOS_DISTRESS,
                title = "SOS: ${beacon.senderAlias}",
                description = "[${beacon.distressLevel.label}] ${beacon.distressMessage}",
                latitude = beacon.latitude,
                longitude = beacon.longitude,
                creatorNodeId = beacon.senderNodeId,
                creatorAlias = beacon.senderAlias,
                createdAtMs = beacon.timestampMs,
                expiresAtMs = beacon.timestampMs + 86400000L,
                isResolved = beacon.isCancelled
            )
            markerDao.insertOrUpdate(TacticalMarkerEntity.fromDomain(marker))
        }
    }

    /**
     * Ingests peer telemetry updates into the live map.
     */
    fun updatePeerTelemetry(telemetry: NodeLocationTelemetry) {
        val current = _peerLocations.value.toMutableMap()
        current[telemetry.nodeId] = telemetry
        _peerLocations.value = current
    }

    /**
     * Simulates peer telemetry and tactical items for comprehensive testing without multi-device setups.
     */
    fun simulatePeerMovements() {
        val myPos = _myLocation.value
        val peers = listOf(
            NodeLocationTelemetry(
                nodeId = "peer_bravo_002",
                alias = "Node Bravo",
                latitude = myPos.latitude + 0.0028,
                longitude = myPos.longitude - 0.0035,
                altitudeMeters = 18.0,
                accuracyMeters = 4f,
                bearingDegrees = 110f,
                speedMps = 1.2f,
                batteryPct = 84
            ),
            NodeLocationTelemetry(
                nodeId = "peer_charlie_003",
                alias = "Node Charlie",
                latitude = myPos.latitude - 0.0042,
                longitude = myPos.longitude + 0.0021,
                altitudeMeters = 12.0,
                accuracyMeters = 3f,
                bearingDegrees = 240f,
                speedMps = 0.5f,
                batteryPct = 68
            ),
            NodeLocationTelemetry(
                nodeId = "peer_delta_relay_004",
                alias = "Relay Delta (Ridge)",
                latitude = myPos.latitude + 0.0055,
                longitude = myPos.longitude + 0.0048,
                altitudeMeters = 54.0,
                accuracyMeters = 2f,
                bearingDegrees = 0f,
                speedMps = 0f,
                batteryPct = 95
            )
        )

        val updated = _peerLocations.value.toMutableMap()
        peers.forEach { updated[it.nodeId] = it }
        _peerLocations.value = updated
    }

    private fun startTelemetryBroadcastLoop() {
        telemetryBroadcastJob?.cancel()
        telemetryBroadcastJob = scope.launch {
            while (isActive) {
                delay(30_000L) // Broadcast node position every 30s
                val loc = _myLocation.value
                val telemetryJson = """
                    {"type":"$PAYLOAD_TYPE_TELEMETRY","nodeId":"${loc.nodeId}","alias":"${loc.alias}","lat":${loc.latitude},"lon":${loc.longitude},"alt":${loc.altitudeMeters},"bearing":${loc.bearingDegrees},"battery":${loc.batteryPct}}
                """.trimIndent()
                val payloadBytes = telemetryJson.toByteArray(Charsets.UTF_8)
                val bundle = signedBroadcast(
                    payloadBytes = payloadBytes,
                    priority = BundlePriority.LOW,
                    ttlMs = 120_000L,
                    maxHops = 3
                )
                storeAndFlood(bundle)
            }
        }
    }

    private fun signedBroadcast(
        payloadBytes: ByteArray,
        bundleId: String = UUID.randomUUID().toString(),
        priority: BundlePriority,
        ttlMs: Long,
        maxHops: Int
    ): DtnBundleEntity {
        val signature = identityKeyManager.signWithIdentity(payloadBytes)
        return DtnBundleEntity(
            bundleId = bundleId,
            sourceNodeId = localNodeId,
            destinationNodeId = "*",
            isBroadcast = true,
            hopCount = 0,
            maxHops = maxHops,
            priority = priority,
            expiresAtEpochMs = System.currentTimeMillis() + ttlMs,
            payloadSizeBytes = payloadBytes.size.toLong(),
            encryptedPayloadHex = payloadBytes.toHexString(),
            senderSignatureHex = signature.toHexString(),
            status = BundleStatus.PENDING_CARRIED
        )
    }

    private suspend fun storeAndFlood(bundle: DtnBundleEntity) {
        dtnSyncEngine.storeLocalBundle(bundle)
        onOutboundBundle?.invoke(bundle)
    }
}

