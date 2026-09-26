package com.example

import com.example.core.geo.BreadcrumbTrackPoint
import com.example.core.geo.GeoMath
import com.example.core.geo.TacticalMarker
import com.example.core.geo.TacticalMarkerType
import com.example.core.transport.packet.TransportFrame
import com.example.core.transport.packet.TransportOpcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GeoMeshUnitTest {

    @Test
    fun testGeoMathDistanceCalculation() {
        val lat1 = 37.7793
        val lon1 = -122.4193
        val lat2 = 37.7955
        val lon2 = -122.3937

        val distance = GeoMath.haversineDistanceMeters(lat1, lon1, lat2, lon2)
        assertTrue("Distance should be approx 2850m (+/- 100m)", abs(distance - 2850.0) < 100.0)
    }

    @Test
    fun testGeoMathBearingCalculation() {
        val bearingNorth = GeoMath.initialBearing(37.0, -122.0, 38.0, -122.0)
        assertEquals(0.0f, bearingNorth, 1.0f)

        val bearingEast = GeoMath.initialBearing(0.0, 0.0, 0.0, 10.0)
        assertEquals(90.0f, bearingEast, 1.0f)

        val bearingSouth = GeoMath.initialBearing(38.0, -122.0, 37.0, -122.0)
        assertEquals(180.0f, bearingSouth, 1.0f)

        val bearingWest = GeoMath.initialBearing(0.0, 10.0, 0.0, 0.0)
        assertEquals(270.0f, bearingWest, 1.0f)
    }

    @Test
    fun testTacticalDmsFormatting() {
        val dms = GeoMath.formatDms(37.7749, -122.4194)
        assertTrue("DMS string should format N and W hemispheres", dms.contains("N") && dms.contains("W"))
        assertTrue("DMS string should format degrees symbol", dms.contains("°"))
    }

    @Test
    fun testTacticalMarkerJsonRoundTrip() {
        val marker = TacticalMarker(
            markerId = "marker-alpha-001",
            type = TacticalMarkerType.HAZARD,
            title = "Bridge Blocked",
            description = "Downed trees and power cables blocking passage.",
            latitude = 37.7749,
            longitude = -122.4194,
            altitudeMeters = 24.5,
            accuracyMeters = 3.5f,
            creatorNodeId = "NODE_FINGERPRINT_1234",
            creatorAlias = "Scout Lead",
            createdAtMs = 1700000000000L,
            expiresAtMs = 1700086400000L,
            isResolved = false
        )

        val json = marker.toJson()
        val deserialized = TacticalMarker.fromJson(json)

        assertNotNull(deserialized)
        assertEquals(marker.markerId, deserialized?.markerId)
        assertEquals(marker.type, deserialized?.type)
        assertEquals(marker.title, deserialized?.title)
        assertEquals(marker.description, deserialized?.description)
        assertEquals(marker.latitude, deserialized!!.latitude, 0.0001)
        assertEquals(marker.longitude, deserialized.longitude, 0.0001)
        assertEquals(marker.altitudeMeters ?: 0.0, deserialized.altitudeMeters ?: 0.0, 0.1)
        assertEquals(marker.creatorNodeId, deserialized.creatorNodeId)
        assertEquals(marker.creatorAlias, deserialized.creatorAlias)
        assertFalse(deserialized.isResolved)
    }

    @Test
    fun testGeoMarkerTransportFrameSerialization() {
        val markerPayload = "{\"id\":\"m1\",\"type\":\"SUPPLY_DEPOT\",\"lat\":37.77,\"lon\":-122.41}".toByteArray(Charsets.UTF_8)
        val frame = TransportFrame(
            opcode = TransportOpcode.OP_GEO_MARKER,
            sequenceNumber = 42,
            payload = markerPayload,
            flags = 0x01
        )

        val serialized = TransportFrame.serialize(frame)
        val parsed = TransportFrame.deserialize(serialized)

        assertNotNull(parsed)
        assertEquals(TransportOpcode.OP_GEO_MARKER, parsed?.opcode)
        assertEquals(0x01.toByte(), parsed?.flags)
        assertEquals(42, parsed?.sequenceNumber)
        assertEquals(String(markerPayload, Charsets.UTF_8), String(parsed?.payload ?: ByteArray(0), Charsets.UTF_8))
    }

    @Test
    fun testGeoTrackTransportFrameSerialization() {
        val trackPayload = "{\"nodeId\":\"N1\",\"lat\":37.7749,\"lon\":-122.4194,\"bearing\":90.0}".toByteArray(Charsets.UTF_8)
        val frame = TransportFrame(
            opcode = TransportOpcode.OP_GEO_TRACK,
            sequenceNumber = 101,
            payload = trackPayload,
            flags = 0x00
        )

        val serialized = TransportFrame.serialize(frame)
        val parsed = TransportFrame.deserialize(serialized)

        assertNotNull(parsed)
        assertEquals(TransportOpcode.OP_GEO_TRACK, parsed?.opcode)
        assertEquals(101, parsed?.sequenceNumber)
        assertEquals(String(trackPayload, Charsets.UTF_8), String(parsed?.payload ?: ByteArray(0), Charsets.UTF_8))
    }

    @Test
    fun testBreadcrumbTrackPoint() {
        val pt = BreadcrumbTrackPoint(
            id = 1L,
            nodeId = "local_node",
            latitude = 37.7749,
            longitude = -122.4194,
            altitudeMeters = 15.0,
            speedMps = 1.4f,
            bearingDegrees = 120f,
            timestampMs = System.currentTimeMillis()
        )

        assertEquals("local_node", pt.nodeId)
        assertEquals(37.7749, pt.latitude, 0.0001)
        assertEquals(-122.4194, pt.longitude, 0.0001)
        assertEquals(120f, pt.bearingDegrees, 0.01f)
    }
}

