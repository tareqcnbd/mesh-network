package com.example

import com.example.core.radio.RadioHealth
import com.example.core.radio.RadioStatus
import com.example.ui.components.ConnectionSignalState
import com.example.ui.components.resolveSignalState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConnectionStatusBadgeTest {

    @Test
    fun `resolveSignalState returns GREEN when peers exist`() {
        val status = RadioStatus(
            isServiceRunning = true,
            bluetoothHealth = RadioHealth.ACTIVE,
            wifiHealth = RadioHealth.ACTIVE
        )
        val state = resolveSignalState(status = status, peerCount = 3)
        assertEquals(ConnectionSignalState.GREEN, state)
    }

    @Test
    fun `resolveSignalState returns GREEN when mesh is operational without peers`() {
        val status = RadioStatus(
            isServiceRunning = true,
            bluetoothHealth = RadioHealth.ACTIVE,
            wifiHealth = RadioHealth.ACTIVE
        )
        val state = resolveSignalState(status = status, peerCount = 0)
        assertEquals(ConnectionSignalState.GREEN, state)
    }

    @Test
    fun `resolveSignalState returns AMBER when scanning with no peers and degraded mesh`() {
        val status = RadioStatus(
            isServiceRunning = true,
            bluetoothHealth = RadioHealth.ACTIVE,
            wifiHealth = RadioHealth.DISABLED,
            isScanningActive = true
        )
        val state = resolveSignalState(status = status, peerCount = 0)
        assertEquals(ConnectionSignalState.AMBER, state)
    }

    @Test
    fun `resolveSignalState returns DISCONNECTED when service is stopped`() {
        val status = RadioStatus(
            isServiceRunning = false,
            bluetoothHealth = RadioHealth.DISABLED,
            wifiHealth = RadioHealth.DISABLED
        )
        val state = resolveSignalState(status = status, peerCount = 0)
        assertEquals(ConnectionSignalState.DISCONNECTED, state)
    }

    @Test
    fun `resolveSignalState returns DISCONNECTED when all radios are disabled`() {
        val status = RadioStatus(
            isServiceRunning = true,
            bluetoothHealth = RadioHealth.DISABLED,
            wifiHealth = RadioHealth.DISABLED
        )
        val state = resolveSignalState(status = status, peerCount = 0)
        assertEquals(ConnectionSignalState.DISCONNECTED, state)
    }

    @Test
    fun `resolveSignalState respects override states`() {
        val status = RadioStatus(
            isServiceRunning = true,
            bluetoothHealth = RadioHealth.ACTIVE,
            wifiHealth = RadioHealth.ACTIVE
        )
        assertEquals(
            ConnectionSignalState.DISCONNECTED,
            resolveSignalState(status = status, peerCount = 5, overrideState = ConnectionSignalState.DISCONNECTED)
        )
        assertEquals(
            ConnectionSignalState.AMBER,
            resolveSignalState(status = status, peerCount = 5, overrideState = ConnectionSignalState.AMBER)
        )
        assertEquals(
            ConnectionSignalState.GREEN,
            resolveSignalState(status = null, peerCount = 0, overrideState = ConnectionSignalState.GREEN)
        )
    }
}
