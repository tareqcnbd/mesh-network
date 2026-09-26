package com.example

import com.example.core.reputation.model.PowChallenge
import com.example.core.reputation.model.ReputationEvent
import com.example.core.reputation.model.TrustTier
import com.example.core.reputation.pow.PowEngine
import com.example.core.reputation.ratelimit.MeshRateLimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReputationAndAntiSpamUnitTest {

    @Test
    fun testProofOfWorkSolveAndVerify() {
        val targetPeerId = "node_adversary_77"
        val challenge = PowEngine.createChallenge(targetPeerId = targetPeerId, difficultyBits = 8)

        assertNotNull(challenge)
        assertEquals(8, challenge.difficultyLeadingZeroBits)
        assertFalse("Fresh challenge must not be expired", challenge.isExpired)

        // Solve puzzle
        val solution = PowEngine.solve(challenge = challenge, solverNodeId = targetPeerId)
        assertNotNull("PoW engine should find solution for 8-bit difficulty", solution)
        assertEquals(challenge.challengeId, solution!!.challengeId)
        assertEquals(targetPeerId, solution.solverNodeId)

        // Verify valid solution
        val isSolutionValid = PowEngine.verify(challenge, solution)
        assertTrue("Valid solution must pass O(1) verification", isSolutionValid)

        // Verify tampering / fake solution rejection
        val fakeSolution = solution.copy(solutionNonce = solution.solutionNonce + 99999L)
        val isFakeValid = PowEngine.verify(challenge, fakeSolution)
        assertFalse("Tampered nonce must fail verification", isFakeValid)
    }

    @Test
    fun testRateLimiterTokenBucketAndBurst() {
        val limiter = MeshRateLimiter(defaultCapacity = 5, refillRatePerSecond = 1.0)
        val peerId = "peer_test_burst"

        // Consume all 5 tokens
        for (i in 1..5) {
            val res = limiter.tryConsume(peerId, cost = 1.0)
            assertTrue("Token consumption #$i within capacity should succeed", res.allowed)
        }

        // 6th consumption without refill should exceed rate limit
        val exceeded = limiter.tryConsume(peerId, cost = 1.0)
        assertFalse("Exceeding capacity must be denied", exceeded.allowed)
        assertEquals(1, exceeded.violationCount)

        // Reset peer clears state
        limiter.resetPeer(peerId)
        val freshAttempt = limiter.tryConsume(peerId, cost = 1.0)
        assertTrue("Attempt after reset should be allowed", freshAttempt.allowed)
    }

    @Test
    fun testReplayAttackDetection() {
        val limiter = MeshRateLimiter()
        val messageHash = "sha256_bundle_unique_fingerprint_9921"

        // First presentation of message
        val firstSeen = limiter.checkAndRecordMessage(messageHash)
        assertTrue("First sighting of message hash should be permitted", firstSeen)

        // Second presentation (Replay Attack)
        val replayAttempt = limiter.checkAndRecordMessage(messageHash)
        assertFalse("Duplicate presentation within sliding window must be identified as REPLAY ATTACK", replayAttempt)
    }

    @Test
    fun testTrustTierClassification() {
        assertEquals(TrustTier.TRUSTED_CORE, TrustTier.fromScore(95))
        assertEquals(TrustTier.TRUSTED_CORE, TrustTier.fromScore(80))
        assertEquals(TrustTier.NEUTRAL_VERIFIED, TrustTier.fromScore(79))
        assertEquals(TrustTier.NEUTRAL_VERIFIED, TrustTier.fromScore(50))
        assertEquals(TrustTier.SUSPICIOUS, TrustTier.fromScore(49))
        assertEquals(TrustTier.SUSPICIOUS, TrustTier.fromScore(20))
        assertEquals(TrustTier.QUARANTINED, TrustTier.fromScore(19))
        assertEquals(TrustTier.QUARANTINED, TrustTier.fromScore(0))
    }

    @Test
    fun testReputationEventsDeltas() {
        assertTrue(ReputationEvent.BUNDLE_DELIVERED_VALID.scoreDelta > 0)
        assertTrue(ReputationEvent.DELACK_CONFIRMED.scoreDelta > 0)
        assertTrue(ReputationEvent.POW_TOKEN_VERIFIED.scoreDelta > 0)
        assertTrue(ReputationEvent.RATE_LIMIT_EXCEEDED.scoreDelta < 0)
        assertTrue(ReputationEvent.DUPLICATE_FLOOD_ATTEMPT.scoreDelta < 0)
        assertTrue(ReputationEvent.INVALID_SIGNATURE.scoreDelta < 0)
    }
}
