package com.example

import com.example.core.discovery.DiscoveredNsdService
import com.example.core.hotspot.HotspotConfig
import com.example.core.radio.TransportTier
import com.example.core.wan.WanConnectionState
import com.example.core.wan.WanSignalingMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class MultiTierNetworkUnitTest {

    @Test
    fun testTransportTierDefinitionsAndHierarchy() {
        assertEquals("Internet", TransportTier.TIER_1_INTERNET.displayName)
        assertEquals("Phone hotspot", TransportTier.TIER_2_HOTSPOT.displayName)
        assertEquals("Nearby mesh", TransportTier.TIER_3_MESH.displayName)

        assertTrue(TransportTier.entries.contains(TransportTier.TIER_1_INTERNET))
        assertTrue(TransportTier.entries.contains(TransportTier.TIER_2_HOTSPOT))
        assertTrue(TransportTier.entries.contains(TransportTier.TIER_3_MESH))
    }

    @Test
    fun testWanSignalingMessageFormatting() {
        val msg = WanSignalingMessage(
            type = "bundle-relay",
            senderNodeId = "node_source_alpha",
            targetNodeId = "node_dest_beta",
            payload = "encrypted_dtn_bundle_data",
            timestampMs = 1700000000000L
        )

        assertEquals("bundle-relay", msg.type)
        assertEquals("node_source_alpha", msg.senderNodeId)
        assertEquals("node_dest_beta", msg.targetNodeId)
        assertEquals("encrypted_dtn_bundle_data", msg.payload)
        assertEquals(1700000000000L, msg.timestampMs)
    }

    @Test
    fun testWanConnectionStates() {
        val states = WanConnectionState.entries
        assertTrue(states.contains(WanConnectionState.DISCONNECTED))
        assertTrue(states.contains(WanConnectionState.CONNECTING))
        assertTrue(states.contains(WanConnectionState.CONNECTED))
        assertTrue(states.contains(WanConnectionState.RELAYING))
        assertTrue(states.contains(WanConnectionState.ERROR))
    }

    @Test
    fun testHotspotConfigModel() {
        val config = HotspotConfig(
            ssid = "Direct-Mesh-AP",
            passphrase = "supersecretpassphrase",
            isRunning = true,
            connectedClientsCount = 3
        )

        assertEquals("Direct-Mesh-AP", config.ssid)
        assertEquals("supersecretpassphrase", config.passphrase)
        assertTrue(config.isRunning)
        assertEquals(3, config.connectedClientsCount)
    }

    @Test
    fun testDiscoveredNsdServiceModel() {
        val host = InetAddress.getByName("192.168.49.1")
        val attrs = mapOf("nodeId" to "node_gamma_99", "proto" to "1.0")

        val service = DiscoveredNsdService(
            serviceName = "MeshNode-Gamma",
            serviceType = "_mesh-dtn._tcp.",
            host = host,
            port = 48890,
            attributes = attrs
        )

        assertEquals("MeshNode-Gamma", service.serviceName)
        assertEquals("_mesh-dtn._tcp.", service.serviceType)
        assertEquals(host, service.host)
        assertEquals(48890, service.port)
        assertEquals("node_gamma_99", service.attributes["nodeId"])
    }
}
