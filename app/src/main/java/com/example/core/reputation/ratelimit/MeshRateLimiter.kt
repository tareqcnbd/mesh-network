package com.example.core.reputation.ratelimit

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Token-Bucket Rate Limiter with Sliding Window Replay Mitigation.
 * Protects constrained mesh radio channels (BLE/Wi-Fi) from spam floods and replay attacks.
 */
class MeshRateLimiter(
    private val defaultCapacity: Int = 10,
    private val refillRatePerSecond: Double = 2.0
) {
    private data class Bucket(
        var tokens: Double,
        var lastRefillMs: Long,
        val messageCountInWindow: AtomicInteger = AtomicInteger(0),
        var windowStartMs: Long = System.currentTimeMillis()
    )

    private val peerBuckets = ConcurrentHashMap<String, Bucket>()
    // Replay cache: hashes of seen message IDs / signatures within 5-minute sliding window
    private val seenMessageHashes = ConcurrentHashMap<String, Long>()

    private val REPLAY_WINDOW_MS = 5 * 60 * 1000L // 5 minutes

    /**
     * Checks whether a message/bundle from [peerId] is a replayed duplicate.
     * Returns true if newly seen, false if REPLAY ATTACK detected.
     */
    fun checkAndRecordMessage(messageFingerprint: String): Boolean {
        cleanupExpiredHashes()
        val now = System.currentTimeMillis()
        val existing = seenMessageHashes.putIfAbsent(messageFingerprint, now)
        return existing == null
    }

    /**
     * Evaluates whether [peerId] has enough tokens to transmit [cost] packets.
     * Dynamically adjusts capacity based on peer trust multiplier.
     */
    fun tryConsume(peerId: String, cost: Double = 1.0, trustMultiplier: Double = 1.0): RateLimitResult {
        val now = System.currentTimeMillis()
        val effectiveCapacity = (defaultCapacity * trustMultiplier).coerceIn(2.0, 30.0)
        val effectiveRefill = (refillRatePerSecond * trustMultiplier).coerceIn(0.5, 10.0)

        val bucket = peerBuckets.compute(peerId) { _, current ->
            if (current == null) {
                Bucket(tokens = effectiveCapacity - cost, lastRefillMs = now)
            } else {
                val elapsedSeconds = (now - current.lastRefillMs).coerceAtLeast(0L) / 1000.0
                val refilled = (current.tokens + elapsedSeconds * effectiveRefill).coerceAtMost(effectiveCapacity)
                current.lastRefillMs = now
                current.tokens = refilled
                current
            }
        } ?: return RateLimitResult(allowed = false, remainingTokens = 0.0, violationCount = 0)

        // Reset sliding window
        if (now - bucket.windowStartMs > 10_000L) { // 10s sliding burst window
            bucket.messageCountInWindow.set(0)
            bucket.windowStartMs = now
        }
        val currentBurst = bucket.messageCountInWindow.incrementAndGet()

        return if (bucket.tokens >= 0.0) {
            bucket.tokens -= cost
            RateLimitResult(
                allowed = true,
                remainingTokens = bucket.tokens,
                violationCount = 0,
                currentBurstCount = currentBurst
            )
        } else {
            RateLimitResult(
                allowed = false,
                remainingTokens = 0.0,
                violationCount = 1,
                currentBurstCount = currentBurst
            )
        }
    }

    fun getRemainingTokens(peerId: String): Double {
        val bucket = peerBuckets[peerId] ?: return defaultCapacity.toDouble()
        val now = System.currentTimeMillis()
        val elapsedSeconds = (now - bucket.lastRefillMs).coerceAtLeast(0L) / 1000.0
        return (bucket.tokens + elapsedSeconds * refillRatePerSecond).coerceIn(0.0, defaultCapacity.toDouble())
    }

    fun resetPeer(peerId: String) {
        peerBuckets.remove(peerId)
    }

    private fun cleanupExpiredHashes() {
        if (seenMessageHashes.size > 2000) {
            val cutoff = System.currentTimeMillis() - REPLAY_WINDOW_MS
            seenMessageHashes.entries.removeIf { it.value < cutoff }
        }
    }
}

data class RateLimitResult(
    val allowed: Boolean,
    val remainingTokens: Double,
    val violationCount: Int,
    val currentBurstCount: Int = 0
)
