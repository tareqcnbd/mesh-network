package com.example.core.voice

import java.util.PriorityQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Adaptive jitter buffer to smooth out out-of-order and variable latency delivery
 * of voice packets over ad-hoc wireless links.
 */
class JitterBuffer(
    private val targetDepthFrames: Int = 4, // 4 frames * 20ms = 80ms buffering
    private val maxDepthFrames: Int = 15     // 300ms maximum backlog
) {
    private val lock = ReentrantLock()
    private val queue = PriorityQueue<VoicePacket>(compareBy { it.sequenceNumber })

    private var nextExpectedSequence: Long = -1L
    private var isBuffering = true

    fun reset() {
        lock.withLock {
            queue.clear()
            nextExpectedSequence = -1L
            isBuffering = true
        }
    }

    fun push(packet: VoicePacket) {
        lock.withLock {
            // Drop packets older than what we have already played
            if (nextExpectedSequence != -1L && packet.sequenceNumber < nextExpectedSequence) {
                return
            }

            queue.offer(packet)

            // If queue exceeds max depth, drop oldest frames to catch up
            while (queue.size > maxDepthFrames) {
                val dropped = queue.poll()
                nextExpectedSequence = (dropped?.sequenceNumber ?: 0L) + 1
            }

            if (isBuffering && queue.size >= targetDepthFrames) {
                isBuffering = false
                nextExpectedSequence = queue.peek()?.sequenceNumber ?: 0L
            }
        }
    }

    /**
     * Polls the next in-order packet ready for playback.
     * Returns null if buffering or queue is empty.
     */
    fun poll(): VoicePacket? {
        lock.withLock {
            if (isBuffering || queue.isEmpty()) {
                return null
            }

            val packet = queue.poll()
            if (packet != null) {
                nextExpectedSequence = packet.sequenceNumber + 1
            }

            if (queue.isEmpty()) {
                isBuffering = true
            }

            return packet
        }
    }

    val size: Int
        get() = lock.withLock { queue.size }
}
