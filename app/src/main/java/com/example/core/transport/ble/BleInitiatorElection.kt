package com.example.core.transport.ble

import java.security.MessageDigest

/**
 * Symmetric BLE GATT client election so two phones do not both open client connections.
 * Each advertises an 8-byte SHA-256 prefix of its node id in scan-response manufacturer
 * data (company id [MANUFACTURER_ID]); the lexicographically smaller hash is the GATT
 * client / initiator. Missing hash means stay in the server role.
 */
object BleInitiatorElection {
    const val HASH_LENGTH = 8
    const val MANUFACTURER_ID = 0x4D53

    fun nodeIdHash(nodeId: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(nodeId.toByteArray(Charsets.UTF_8))
        return digest.copyOf(HASH_LENGTH)
    }

    fun hashFromAdvertisement(manufacturerData: ByteArray?, serviceData: ByteArray?): ByteArray? {
        val fromManufacturer = manufacturerData?.takeIf { it.size >= HASH_LENGTH }?.copyOf(HASH_LENGTH)
        if (fromManufacturer != null) return fromManufacturer
        return serviceData?.takeIf { it.size >= HASH_LENGTH }?.copyOf(HASH_LENGTH)
    }

    fun shouldInitiate(localHash: ByteArray, remoteHash: ByteArray?): Boolean {
        if (remoteHash == null || remoteHash.isEmpty()) {
            return false
        }
        val local = if (localHash.size >= HASH_LENGTH) localHash.copyOf(HASH_LENGTH) else localHash
        val remote = if (remoteHash.size >= HASH_LENGTH) remoteHash.copyOf(HASH_LENGTH) else remoteHash
        val cmp = compareUnsigned(local, remote)
        return cmp < 0
    }

    private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            val av = a[i].toInt() and 0xFF
            val bv = b[i].toInt() and 0xFF
            if (av != bv) return av - bv
        }
        return a.size - b.size
    }
}
