package com.example.core.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.example.core.radio.DirectLinkType
import com.example.core.radio.RadioPermissionManager
import com.example.core.transport.PeerConnectionState
import com.example.core.transport.PhysicalTransportDriver
import com.example.core.transport.TransportPeerInfo
import com.example.core.transport.TransportPeerListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE GATT Mesh Transport Driver.
 *
 * Dual-role: GATT server advertises the mesh service; GATT client connects only when
 * local node-id hash is lexicographically smaller than the remote advertised hash.
 * Data is bidirectional (client writes + server notifications) and MTU-fragmented.
 */
class BleGattTransportDriver(
    private val context: Context,
    private val localNodeId: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : PhysicalTransportDriver {

    companion object {
        private const val TAG = "BleGattDriver"

        val MESH_SERVICE_UUID: UUID = UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")
        val MESH_RX_CHAR_UUID: UUID = UUID.fromString("0000FEAB-0000-1000-8000-00805F9B34FB")
        val MESH_TX_CHAR_UUID: UUID = UUID.fromString("0000FEAC-0000-1000-8000-00805F9B34FB")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        const val PREFERRED_MTU = 512
        const val DEFAULT_ATT_PAYLOAD = 20
    }

    override val transportType: DirectLinkType = DirectLinkType.BLE_GATT

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private var gattServer: BluetoothGattServer? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    private val connectedGattClients = ConcurrentHashMap<String, BluetoothGatt>()
    private val serverDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private val notifySubscribed = ConcurrentHashMap.newKeySet<String>()
    private val connectingPeerIds = ConcurrentHashMap.newKeySet<String>()
    private val activePeers = ConcurrentHashMap<String, TransportPeerInfo>()
    private val peerMtu = ConcurrentHashMap<String, Int>()
    private val reassembly = ConcurrentHashMap<String, BleReassemblyBuffer>()
    private val writeMutexes = ConcurrentHashMap<String, Mutex>()
    private val pendingWrites = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private val localHash = BleInitiatorElection.nodeIdHash(localNodeId)

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    override val isAvailable: Boolean
        get() = bluetoothAdapter != null && bluetoothAdapter.isEnabled &&
            RadioPermissionManager.hasBluetoothPermissions(context)

    override var maxMtuBytes: Int = DEFAULT_ATT_PAYLOAD
        private set

    private var peerListener: TransportPeerListener? = null
    var onError: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    override suspend fun start(listener: TransportPeerListener): Boolean {
        if (_isRunning.value) return true
        if (!isAvailable) {
            val msg = "BLE hardware or permissions unavailable"
            Log.w(TAG, msg)
            onError?.invoke(msg)
            return false
        }

        this.peerListener = listener

        try {
            advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
            scanner = bluetoothAdapter?.bluetoothLeScanner

            setupGattServer()
            startAdvertising()
            startScanning()

            _isRunning.value = true
            Log.i(TAG, "BLE GATT Transport Driver started successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE GATT Driver", e)
            onError?.invoke(e.message ?: "BLE start failed")
            stop()
            return false
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun stop() {
        _isRunning.value = false
        stopAdvertising()
        stopScanning()

        connectedGattClients.values.forEach { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing client GATT", e)
            }
        }
        connectedGattClients.clear()
        serverDevices.clear()
        notifySubscribed.clear()
        connectingPeerIds.clear()
        reassembly.values.forEach { it.clear() }
        reassembly.clear()

        try {
            gattServer?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing GATT server", e)
        }
        gattServer = null
        txCharacteristic = null
        activePeers.clear()
        Log.i(TAG, "BLE GATT Transport Driver stopped")
    }

    @SuppressLint("MissingPermission")
    override suspend fun sendPacket(targetPeerId: String, payload: ByteArray): Boolean {
        if (!_isRunning.value) return false
        val mtu = peerMtu[targetPeerId] ?: maxMtuBytes.coerceAtLeast(DEFAULT_ATT_PAYLOAD)
        val chunks = BleFrameFramer.split(payload, mtu)

        val gatt = connectedGattClients[targetPeerId]
        if (gatt != null) {
            return writeChunksAsClient(targetPeerId, gatt, chunks)
        }

        val device = serverDevices[targetPeerId]
        if (device != null) {
            return notifyChunksAsServer(targetPeerId, device, chunks)
        }

        Log.w(TAG, "No active GATT path to $targetPeerId")
        return false
    }

    override fun getConnectedPeers(): List<TransportPeerInfo> {
        return activePeers.values.filter { it.connectionState == PeerConnectionState.CONNECTED }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeChunksAsClient(
        peerId: String,
        gatt: BluetoothGatt,
        chunks: List<ByteArray>
    ): Boolean {
        val service = gatt.getService(MESH_SERVICE_UUID) ?: return false
        val rxChar = service.getCharacteristic(MESH_RX_CHAR_UUID) ?: return false
        val mutex = writeMutexes.getOrPut(peerId) { Mutex() }
        return mutex.withLock {
            var allOk = true
            for (chunk in chunks) {
                val deferred = CompletableDeferred<Boolean>()
                pendingWrites[peerId] = deferred
                val queued = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeCharacteristic(
                            rxChar,
                            chunk,
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        ) == BluetoothGatt.GATT_SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        rxChar.value = chunk
                        @Suppress("DEPRECATION")
                        rxChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(rxChar)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "BLE write failed to $peerId", e)
                    false
                }
                if (!queued) {
                    pendingWrites.remove(peerId)
                    allOk = false
                    break
                }
                val ok = withTimeoutOrNull(2_000) { deferred.await() } ?: false
                if (!ok) {
                    allOk = false
                    break
                }
            }
            pendingWrites.remove(peerId)
            allOk
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyChunksAsServer(
        peerId: String,
        device: BluetoothDevice,
        chunks: List<ByteArray>
    ): Boolean {
        val server = gattServer ?: return false
        val tx = txCharacteristic ?: return false
        if (peerId !in notifySubscribed) {
            Log.w(TAG, "Peer $peerId has not subscribed to notifications yet")
            return false
        }
        var allOk = true
        for (chunk in chunks) {
            val ok = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val status = server.notifyCharacteristicChanged(device, tx, false, chunk)
                    status == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    tx.value = chunk
                    @Suppress("DEPRECATION")
                    server.notifyCharacteristicChanged(device, tx, false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "BLE notify failed to $peerId", e)
                false
            }
            if (!ok) allOk = false
        }
        return allOk
    }

    private fun deliverUp(peerId: String, chunk: ByteArray) {
        val buffer = reassembly.getOrPut(peerId) { BleReassemblyBuffer() }
        val complete = buffer.offer(chunk) ?: return
        scope.launch {
            peerListener?.onDataReceived(peerId, complete, DirectLinkType.BLE_GATT)
        }
    }

    private fun markConnected(peerId: String, address: String, rssi: Int) {
        val peerInfo = TransportPeerInfo(
            peerId = peerId,
            deviceAddress = address,
            transportType = DirectLinkType.BLE_GATT,
            rssi = rssi,
            connectionState = PeerConnectionState.CONNECTED,
            linkBandwidthEstimateKbps = 120
        )
        val previous = activePeers.put(peerId, peerInfo)
        if (previous?.connectionState != PeerConnectionState.CONNECTED) {
            peerListener?.onPeerConnected(peerInfo)
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupGattServer() {
        val serverCallback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                val peerId = peerIdFor(device)
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    serverDevices[peerId] = device
                    markConnected(peerId, device.address, -60)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    serverDevices.remove(peerId)
                    notifySubscribed.remove(peerId)
                    if (!connectedGattClients.containsKey(peerId)) {
                        activePeers.remove(peerId)
                        peerListener?.onPeerDisconnected(peerId, "GATT Server client disconnected")
                    }
                }
            }

            override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
                val usable = (mtu - 3).coerceAtLeast(DEFAULT_ATT_PAYLOAD)
                peerMtu[peerIdFor(device)] = usable
                maxMtuBytes = maxOf(maxMtuBytes, usable)
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (descriptor.uuid == CCCD_UUID) {
                    val enabled = value != null && value.isNotEmpty() &&
                        value[0].toInt() and 0x01 == 1
                    if (enabled) {
                        notifySubscribed.add(peerIdFor(device))
                    } else {
                        notifySubscribed.remove(peerIdFor(device))
                    }
                }
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
                if (value != null && value.isNotEmpty() && characteristic.uuid == MESH_RX_CHAR_UUID) {
                    deliverUp(peerIdFor(device), value)
                }
            }
        }

        gattServer = bluetoothManager?.openGattServer(context, serverCallback)
        val service = BluetoothGattService(MESH_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val rxChar = BluetoothGattCharacteristic(
            MESH_RX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val txChar = BluetoothGattCharacteristic(
            MESH_TX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        txChar.addDescriptor(cccd)

        service.addCharacteristic(rxChar)
        service.addCharacteristic(txChar)
        txCharacteristic = txChar
        gattServer?.addService(service)
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (advertiser == null) return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(BleInitiatorElection.MANUFACTURER_ID, localHash)
            .build()

        advertiser?.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping advertiser", e)
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "BLE Advertisement started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE) {
                retryMinimalAdvertise()
                return
            }
            val msg = "BLE advertisement failed: $errorCode"
            Log.w(TAG, msg)
            onError?.invoke(msg)
        }
    }

    @SuppressLint("MissingPermission")
    private fun retryMinimalAdvertise() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
            .build()
        try {
            advertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {
                override fun onStartFailure(errorCode: Int) {
                    val msg = "BLE advertisement failed: $errorCode"
                    Log.w(TAG, msg)
                    onError?.invoke(msg)
                }
            })
        } catch (e: Exception) {
            onError?.invoke(e.message ?: "BLE advertisement failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        if (scanner == null) return

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
                .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(filters, scanSettings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping scan", e)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                handleDiscoveredBleDevice(device, result.rssi, result)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { res ->
                res.device?.let { handleDiscoveredBleDevice(it, res.rssi, res) }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val msg = "BLE scan failed: $errorCode"
            Log.w(TAG, msg)
            onError?.invoke(msg)
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDiscoveredBleDevice(device: BluetoothDevice, rssi: Int, scanResult: ScanResult) {
        val peerId = peerIdFor(device)
        val scanRecord = scanResult.scanRecord
        val remoteHash = BleInitiatorElection.hashFromAdvertisement(
            manufacturerData = scanRecord?.getManufacturerSpecificData(BleInitiatorElection.MANUFACTURER_ID),
            serviceData = scanRecord?.getServiceData(ParcelUuid(MESH_SERVICE_UUID))
        )
        val peerInfo = TransportPeerInfo(
            peerId = peerId,
            deviceAddress = device.address,
            transportType = DirectLinkType.BLE_GATT,
            rssi = rssi,
            connectionState = PeerConnectionState.DISCOVERED,
            linkBandwidthEstimateKbps = 120
        )
        peerListener?.onPeerDiscovered(peerInfo)

        if (!BleInitiatorElection.shouldInitiate(localHash, remoteHash)) {
            return
        }
        if (connectedGattClients.containsKey(peerId) || !connectingPeerIds.add(peerId)) {
            return
        }

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to remote BLE peer: $peerId, requesting MTU $PREFERRED_MTU")
                    gatt.requestMtu(PREFERRED_MTU)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedGattClients.remove(peerId)
                    connectingPeerIds.remove(peerId)
                    pendingWrites.remove(peerId)?.complete(false)
                    if (!serverDevices.containsKey(peerId)) {
                        activePeers.remove(peerId)
                        peerListener?.onPeerDisconnected(peerId, "Client GATT disconnected")
                    }
                    gatt.close()
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                val usable = (mtu - 3).coerceAtLeast(DEFAULT_ATT_PAYLOAD)
                peerMtu[peerId] = usable
                maxMtuBytes = maxOf(maxMtuBytes, usable)
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    connectingPeerIds.remove(peerId)
                    return
                }
                connectedGattClients[peerId] = gatt
                enableNotifications(gatt)
                markConnected(peerId, device.address, rssi)
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                Log.d(TAG, "CCCD write for $peerId status=$status")
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                pendingWrites[peerId]?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                deliverUp(peerId, value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                deliverUp(peerId, value)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(MESH_SERVICE_UUID) ?: return
        val txChar = service.getCharacteristic(MESH_TX_CHAR_UUID) ?: return
        gatt.setCharacteristicNotification(txChar, true)
        val cccd = txChar.getDescriptor(CCCD_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }
    }

    private fun peerIdFor(device: BluetoothDevice): String {
        return "ble_" + device.address.replace(":", "").lowercase()
    }
}
