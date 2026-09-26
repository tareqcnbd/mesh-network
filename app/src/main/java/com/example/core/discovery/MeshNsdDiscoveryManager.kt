package com.example.core.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * mDNS / DNS-SD Service Discovery via Android Network Service Discovery (NSD).
 * Used across Tier 1 (Local Subnet WAN) and Tier 2 (Local-Only Hotspot) to resolve
 * mesh node service endpoints and exchange port & node identity information.
 */
data class DiscoveredNsdService(
    val serviceName: String,
    val serviceType: String,
    val host: InetAddress?,
    val port: Int,
    val attributes: Map<String, String> = emptyMap(),
    val discoveredAtEpochMs: Long = System.currentTimeMillis()
)

class MeshNsdDiscoveryManager(
    private val context: Context,
    private val localNodeId: String,
    private val servicePort: Int = 48890
) {
    companion object {
        private const val TAG = "MeshNsdManager"
        const val SERVICE_TYPE = "_mesh-dtn._tcp."
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val _discoveredServices = MutableStateFlow<Map<String, DiscoveredNsdService>>(emptyMap())
    val discoveredServices: StateFlow<Map<String, DiscoveredNsdService>> = _discoveredServices.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startAdvertising(customName: String = "MeshNode-$localNodeId") {
        if (_isAdvertising.value || nsdManager == null) return

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = customName
            serviceType = SERVICE_TYPE
            port = servicePort
            setAttribute("nodeId", localNodeId)
            setAttribute("protoVersion", "1.0")
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo?) {
                _isAdvertising.value = true
                Log.i(TAG, "mDNS Service registered: ${NsdServiceInfo?.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                _isAdvertising.value = false
                Log.w(TAG, "mDNS Service registration failed: $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo?) {
                _isAdvertising.value = false
                Log.i(TAG, "mDNS Service unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.w(TAG, "mDNS Service unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering mDNS service", e)
        }
    }

    fun stopAdvertising() {
        if (!_isAdvertising.value || nsdManager == null) return
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering mDNS service", e)
        } finally {
            _isAdvertising.value = false
            registrationListener = null
        }
    }

    fun startDiscovery() {
        if (_isDiscovering.value || nsdManager == null) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) {
                _isDiscovering.value = true
                Log.d(TAG, "NSD Discovery started for $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo?) {
                if (service == null) return
                if (service.serviceType.contains("mesh-dtn")) {
                    // Resolve service details (IP & port)
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo?) {
                if (service != null) {
                    _discoveredServices.update { it - service.serviceName }
                    Log.d(TAG, "NSD Service lost: ${service.serviceName}")
                }
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                _isDiscovering.value = false
                Log.d(TAG, "NSD Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                _isDiscovering.value = false
                Log.w(TAG, "NSD Start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.w(TAG, "NSD Stop discovery failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating NSD discovery", e)
        }
    }

    fun stopDiscovery() {
        if (!_isDiscovering.value || nsdManager == null) return
        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping NSD discovery", e)
        } finally {
            _isDiscovering.value = false
            discoveryListener = null
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo?.serviceName}: code $errorCode")
            }

            override fun onServiceResolved(resolved: NsdServiceInfo?) {
                if (resolved == null) return
                val name = resolved.serviceName

                val attrs = mutableMapOf<String, String>()
                try {
                    resolved.attributes.forEach { (k, v) ->
                        attrs[k] = String(v, Charsets.UTF_8)
                    }
                } catch (e: Exception) {}

                val discovered = DiscoveredNsdService(
                    serviceName = name,
                    serviceType = resolved.serviceType,
                    host = resolved.host,
                    port = resolved.port,
                    attributes = attrs
                )
                _discoveredServices.update { it + (name to discovered) }
                Log.i(TAG, "Resolved NSD Service: $name at ${resolved.host}:${resolved.port}")
            }
        })
    }
}
