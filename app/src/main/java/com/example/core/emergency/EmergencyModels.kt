package com.example.core.emergency

import java.util.UUID

enum class EmergencyDistressLevel(val label: String, val severityRank: Int) {
    SOS_CRITICAL("CRITICAL SOS", 1),
    MEDICAL_EMERGENCY("Medical Emergency", 2),
    EVACUATION("Disaster Evacuation", 3),
    TEST_DRILL("Emergency Test Drill", 4)
}

data class EmergencyBeacon(
    val beaconId: String = UUID.randomUUID().toString(),
    val senderNodeId: String,
    val senderAlias: String,
    val distressLevel: EmergencyDistressLevel,
    val distressMessage: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val batteryPct: Int = 100,
    val timestampMs: Long = System.currentTimeMillis(),
    val isCancelled: Boolean = false
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"beaconId\":\"").append(escapeJson(beaconId)).append("\",")
            append("\"senderNodeId\":\"").append(escapeJson(senderNodeId)).append("\",")
            append("\"senderAlias\":\"").append(escapeJson(senderAlias)).append("\",")
            append("\"distressLevel\":\"").append(distressLevel.name).append("\",")
            append("\"distressMessage\":\"").append(escapeJson(distressMessage)).append("\",")
            if (latitude != null) append("\"lat\":").append(latitude).append(",")
            if (longitude != null) append("\"lon\":").append(longitude).append(",")
            append("\"batteryPct\":").append(batteryPct).append(",")
            append("\"timestampMs\":").append(timestampMs).append(",")
            append("\"isCancelled\":").append(isCancelled)
            append("}")
        }
    }

    companion object {
        private fun escapeJson(str: String): String =
            str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

        fun fromJson(jsonStr: String): EmergencyBeacon? {
            return try {
                fun extractString(key: String): String? {
                    val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                    return pattern.find(jsonStr)?.groupValues?.get(1)
                }
                fun extractDouble(key: String): Double? {
                    val pattern = "\"$key\"\\s*:\\s*([0-9.-]+)".toRegex()
                    return pattern.find(jsonStr)?.groupValues?.get(1)?.toDoubleOrNull()
                }
                fun extractLong(key: String): Long? {
                    val pattern = "\"$key\"\\s*:\\s*([0-9-]+)".toRegex()
                    return pattern.find(jsonStr)?.groupValues?.get(1)?.toLongOrNull()
                }
                fun extractInt(key: String): Int? {
                    val pattern = "\"$key\"\\s*:\\s*([0-9-]+)".toRegex()
                    return pattern.find(jsonStr)?.groupValues?.get(1)?.toIntOrNull()
                }
                fun extractBoolean(key: String): Boolean {
                    val pattern = "\"$key\"\\s*:\\s*(true|false)".toRegex()
                    return pattern.find(jsonStr)?.groupValues?.get(1) == "true"
                }

                val beaconId = extractString("beaconId") ?: UUID.randomUUID().toString()
                val senderNodeId = extractString("senderNodeId") ?: return null
                val senderAlias = extractString("senderAlias") ?: "Node"
                val distressLevelStr = extractString("distressLevel")
                val distressLevel = try {
                    EmergencyDistressLevel.valueOf(distressLevelStr ?: "")
                } catch (e: Exception) {
                    EmergencyDistressLevel.SOS_CRITICAL
                }
                val message = extractString("distressMessage") ?: ""
                val lat = extractDouble("lat")
                val lon = extractDouble("lon")
                val battery = extractInt("batteryPct") ?: 100
                val ts = extractLong("timestampMs") ?: System.currentTimeMillis()
                val cancelled = extractBoolean("isCancelled")

                EmergencyBeacon(
                    beaconId = beaconId,
                    senderNodeId = senderNodeId,
                    senderAlias = senderAlias,
                    distressLevel = distressLevel,
                    distressMessage = message,
                    latitude = lat,
                    longitude = lon,
                    batteryPct = battery,
                    timestampMs = ts,
                    isCancelled = cancelled
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
