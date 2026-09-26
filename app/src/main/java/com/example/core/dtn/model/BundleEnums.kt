package com.example.core.dtn.model

/**
 * Priorities for DTN store-carry-and-forward bundles.
 * Lower priority bundles are evicted first when the storage buffer reaches high-water mark.
 */
enum class BundlePriority(val weight: Int) {
    LOW(1),
    NORMAL(2),
    HIGH(3),
    EMERGENCY(4)
}

/**
 * Status of a DTN bundle in the local pipeline.
 */
enum class BundleStatus {
    PENDING_CARRIED, // Carried locally, awaiting neighbor encounter
    IN_TRANSIT,       // Currently transmitting over radio
    DELIVERED,        // Delivered to destination or DelAck received
    EXPIRED,          // TTL expired, queued for tombstone eviction
    EVICTED           // Evicted due to buffer overflow
}
