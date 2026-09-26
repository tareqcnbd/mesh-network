package com.example.core.geo

import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class TacticalMarkerType(val label: String, val iconKey: String) {
    PEER_POSITION("Peer Location", "peer"),
    SOS_DISTRESS("SOS Distress", "sos"),
    HAZARD("Hazard Warning", "hazard"),
    SUPPLY_DEPOT("Supply Depot", "supply"),
    MEETING_POINT("Meeting Point", "meeting"),
    RALLY_POINT("Rally Point", "rally"),
    COMM_RELAY("Comms Relay", "relay"),
    CUSTOM_NOTE("Tactical Note", "note")
}

data class TacticalMarker(
    val markerId: String = UUID.randomUUID().toString(),
    val type: TacticalMarkerType = TacticalMarkerType.HAZARD,
    val title: String,
    val description: String = "",
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val creatorNodeId: String,
    val creatorAlias: String = "Operator",
    val createdAtMs: Long = System.currentTimeMillis(),
    val expiresAtMs: Long = System.currentTimeMillis() + (24 * 3600 * 1000L), // 24h default TTL
    val isResolved: Boolean = false
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"markerId\":\"").append(escapeJson(markerId)).append("\",")
            append("\"type\":\"").append(type.name).append("\",")
            append("\"title\":\"").append(escapeJson(title)).append("\",")
            append("\"desc\":\"").append(escapeJson(description)).append("\",")
            append("\"lat\":").append(latitude).append(",")
            append("\"lon\":").append(longitude).append(",")
            if (altitudeMeters != null) append("\"alt\":").append(altitudeMeters).append(",")
            if (accuracyMeters != null) append("\"acc\":").append(accuracyMeters).append(",")
            append("\"nodeId\":\"").append(escapeJson(creatorNodeId)).append("\",")
            append("\"alias\":\"").append(escapeJson(creatorAlias)).append("\",")
            append("\"created\":").append(createdAtMs).append(",")
            append("\"expires\":").append(expiresAtMs).append(",")
            append("\"resolved\":").append(isResolved)
            append("}")
        }
    }

    companion object {
        private fun escapeJson(str: String): String =
            str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

        fun fromJson(jsonStr: String): TacticalMarker? {
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
                fun extractLong(key: String): Long? {
                    val pattern = "\"$key\"\\s*:\\s*([0-9-]+)".toRegex()
                    return pattern.find(jsonStr)?.groupValues?.get(1)?.toLongOrNull()
                }
                fun extractBoolean(key: String): Boolean {
                    val pattern = "\"$key\"\\s*:\\s*(true|false)".toRegex()
                    return pattern.find(jsonStr)?.groupValues?.get(1) == "true"
                }

                val markerId = extractString("markerId") ?: UUID.randomUUID().toString()
                val typeStr = extractString("type")
                val type = try {
                    TacticalMarkerType.valueOf(typeStr ?: "")
                } catch (e: Exception) {
                    TacticalMarkerType.HAZARD
                }
                val title = extractString("title") ?: "Marker"
                val desc = extractString("desc") ?: ""
                val lat = extractDouble("lat") ?: return null
                val lon = extractDouble("lon") ?: return null
                val alt = extractDouble("alt")
                val acc = extractFloat("acc")
                val nodeId = extractString("nodeId") ?: "unknown"
                val alias = extractString("alias") ?: "Operator"
                val created = extractLong("created") ?: System.currentTimeMillis()
                val expires = extractLong("expires") ?: (created + 86400000L)
                val resolved = extractBoolean("resolved")

                TacticalMarker(
                    markerId = markerId,
                    type = type,
                    title = title,
                    description = desc,
                    latitude = lat,
                    longitude = lon,
                    altitudeMeters = alt,
                    accuracyMeters = acc,
                    creatorNodeId = nodeId,
                    creatorAlias = alias,
                    createdAtMs = created,
                    expiresAtMs = expires,
                    isResolved = resolved
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class BreadcrumbTrackPoint(
    val id: Long = 0L,
    val nodeId: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
    val speedMps: Float = 0f,
    val bearingDegrees: Float = 0f,
    val timestampMs: Long = System.currentTimeMillis()
)

object GeoMath {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates great-circle distance between two coordinates in meters.
     */
    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculates initial bearing from (lat1, lon1) to (lat2, lon2) in degrees [0..360).
     */
    fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        val initialBearingRad = atan2(y, x)
        val degrees = Math.toDegrees(initialBearingRad)
        return ((degrees + 360) % 360).toFloat()
    }

    /**
     * Format coordinates as tactical standard DMS (Degrees Minutes Seconds)
     */
    fun formatDms(lat: Double, lon: Double): String {
        fun formatSingle(deg: Double, isLat: Boolean): String {
            val dir = if (isLat) {
                if (deg >= 0) "N" else "S"
            } else {
                if (deg >= 0) "E" else "W"
            }
            val absVal = kotlin.math.abs(deg)
            val d = absVal.toInt()
            val m = ((absVal - d) * 60).toInt()
            val s = (((absVal - d) * 60 - m) * 60).toInt()
            return "%02d°%02d'%02d\"%s".format(d, m, s, dir)
        }
        return "${formatSingle(lat, true)} ${formatSingle(lon, false)}"
    }
}
