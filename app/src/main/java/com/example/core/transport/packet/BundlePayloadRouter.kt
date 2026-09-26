package com.example.core.transport.packet

import com.example.core.dtn.model.DtnBundleEntity
import com.example.core.emergency.EmergencyBeacon
import com.example.core.geo.NodeLocationTelemetry
import com.example.core.geo.TacticalMarker

/**
 * Classifies a verified DTN payload and dispatches it to chat, SOS, telemetry, or map markers.
 */
class BundlePayloadRouter(
    private val onChat: suspend (DtnBundleEntity, String) -> Unit = { _, _ -> },
    private val onSos: suspend (EmergencyBeacon) -> Unit = {},
    private val onTelemetry: suspend (NodeLocationTelemetry) -> Unit = {},
    private val onMarker: suspend (TacticalMarker) -> Unit = {}
) {
    enum class Kind {
        CHAT,
        SOS,
        TELEMETRY,
        MARKER,
        UNKNOWN
    }

    suspend fun dispatch(bundle: DtnBundleEntity, payload: ByteArray) {
        when (classify(payload)) {
            Kind.CHAT -> {
                val text = ChatPayload.decode(payload) ?: return
                onChat(bundle, text)
            }
            Kind.SOS -> {
                val beacon = EmergencyBeacon.fromJson(payload.decodeToString()) ?: return
                onSos(beacon)
            }
            Kind.TELEMETRY -> {
                val telemetry = NodeLocationTelemetry.fromJson(payload.decodeToString()) ?: return
                onTelemetry(telemetry)
            }
            Kind.MARKER -> {
                val marker = TacticalMarker.fromJson(payload.decodeToString()) ?: return
                onMarker(marker)
            }
            Kind.UNKNOWN -> Unit
        }
    }

    companion object {
        fun classify(payload: ByteArray): Kind {
            if (ChatPayload.isChat(payload)) return Kind.CHAT
            val text = payload.toString(Charsets.UTF_8).trim()
            if (!text.startsWith("{")) return Kind.UNKNOWN
            if (text.contains("\"beaconId\"") && text.contains("\"distressLevel\"")) {
                return Kind.SOS
            }
            if (text.contains("\"type\":\"node_telemetry\"") ||
                text.contains("\"type\": \"node_telemetry\"")
            ) {
                return Kind.TELEMETRY
            }
            if (text.contains("\"markerId\"")) {
                return Kind.MARKER
            }
            return Kind.UNKNOWN
        }
    }
}
