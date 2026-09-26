package com.example.core.reputation

import com.example.core.crypto.EccCryptoEngine
import com.example.core.crypto.IdentityKeyManager
import com.example.core.reputation.db.PeerReputationDao
import com.example.core.reputation.db.PeerReputationEntity
import com.example.core.reputation.db.ReputationAuditLogEntity
import com.example.core.reputation.model.PowChallenge
import com.example.core.reputation.model.PowSolutionToken
import com.example.core.reputation.model.ReputationAttestation
import com.example.core.reputation.model.ReputationEvent
import com.example.core.reputation.model.TrustTier
import com.example.core.reputation.pow.PowEngine
import com.example.core.reputation.ratelimit.MeshRateLimiter
import com.example.core.reputation.ratelimit.RateLimitResult
import com.example.core.transport.packet.TransportFrame
import com.example.core.transport.packet.TransportOpcode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Decentralized Reputation & Anti-Spam Gossip Protocol Manager.
 * Orchestrates:
 * 1. Peer trust scores & audit history
 * 2. Adaptive Token-Bucket rate-limiting & replay mitigation
 * 3. Dynamic Proof-of-Work challenge/solution tokens
 * 4. Anti-spam quarantine & blacklist broadcast alerts
 * 5. Decentralized reputation attestation gossip over the mesh
 */
class MeshReputationManager(
    val localNodeId: String,
    private val reputationDao: PeerReputationDao,
    private val identityKeyManager: IdentityKeyManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val mutex = Mutex()
    val rateLimiter = MeshRateLimiter(defaultCapacity = 10, refillRatePerSecond = 2.0)

    // Outstanding active PoW challenges issued to peers: challengeId -> PowChallenge
    private val activeChallenges = ConcurrentHashMap<String, PowChallenge>()

    // Broadcast frame flow for gossip attestations and quarantine alerts
    private val _outboundReputationFrames = MutableSharedFlow<TransportFrame>(extraBufferCapacity = 64)
    val outboundReputationFrames: SharedFlow<TransportFrame> = _outboundReputationFrames.asSharedFlow()

    // Real-time security events counter
    private val _securityMetrics = MutableStateFlow(SecurityMetrics())
    val securityMetrics: StateFlow<SecurityMetrics> = _securityMetrics.asStateFlow()

    val allReputations: Flow<List<PeerReputationEntity>> = reputationDao.observeAllReputations()
    val quarantinedPeers: Flow<List<PeerReputationEntity>> = reputationDao.observeQuarantinedPeers()
    val recentAuditLogs: Flow<List<ReputationAuditLogEntity>> = reputationDao.observeRecentAuditLogs(60)

    /**
     * Determines whether an incoming bundle or transmission from [peerId] should be permitted.
     * Evaluates quarantine state, replay detection, and token-bucket rate limits.
     */
    suspend fun evaluateIncomingTransmission(
        peerId: String,
        peerAlias: String,
        messageFingerprint: String,
        cost: Double = 1.0
    ): TransmissionEvaluation = mutex.withLock {
        // 1. Fetch or initialize peer reputation record
        val peer = getOrCreatePeerReputation(peerId, peerAlias)

        // 2. Quarantine Check: Immediate drop if node is blacklisted
        if (peer.isQuarantined) {
            _securityMetrics.value = _securityMetrics.value.copy(
                quarantinedDrops = _securityMetrics.value.quarantinedDrops + 1
            )
            return@withLock TransmissionEvaluation(
                isAllowed = false,
                reason = "Peer is QUARANTINED: ${peer.quarantineReason ?: "Blacklisted"}",
                requiresPow = false
            )
        }

        // 3. Replay Attack & Flooding Mitigation Check
        val isNewMessage = rateLimiter.checkAndRecordMessage(messageFingerprint)
        if (!isNewMessage) {
            recordReputationPenalty(peerId, ReputationEvent.DUPLICATE_FLOOD_ATTEMPT, "Replay attack detected: duplicate message hash $messageFingerprint")
            _securityMetrics.value = _securityMetrics.value.copy(
                replayDrops = _securityMetrics.value.replayDrops + 1
            )
            return@withLock TransmissionEvaluation(
                isAllowed = false,
                reason = "REPLAY / FLOODING ATTACK: Duplicate message rejected",
                requiresPow = false
            )
        }

        // 4. Rate-limiting token evaluation based on Trust Tier multiplier
        val tier = TrustTier.valueOf(peer.trustTier)
        val trustMultiplier = when (tier) {
            TrustTier.TRUSTED_CORE -> 2.0 // Double capacity & refill
            TrustTier.NEUTRAL_VERIFIED -> 1.0 // Normal capacity
            TrustTier.SUSPICIOUS -> 0.4 // Restrictive capacity
            TrustTier.QUARANTINED -> 0.0 // Zero tokens
        }

        val rateResult = rateLimiter.tryConsume(peerId, cost, trustMultiplier)

        if (!rateResult.allowed) {
            recordReputationPenalty(peerId, ReputationEvent.RATE_LIMIT_EXCEEDED, "Burst threshold exceeded. Tokens: ${rateResult.remainingTokens}")
            _securityMetrics.value = _securityMetrics.value.copy(
                rateLimitDrops = _securityMetrics.value.rateLimitDrops + 1
            )

            // If peer is already Suspicious and exceeds rate limit, trigger dynamic PoW challenge requirement
            val powChallenge = createPowChallengeForPeer(peerId, tier)
            return@withLock TransmissionEvaluation(
                isAllowed = false,
                reason = "RATE LIMIT EXCEEDED: Submission rejected. PoW puzzle required.",
                requiresPow = true,
                powChallenge = powChallenge
            )
        }

        TransmissionEvaluation(
            isAllowed = true,
            reason = "Accepted (Tier: ${tier.label})",
            requiresPow = false
        )
    }

    /**
     * Issues an adaptive Proof-of-Work challenge to a peer.
     * Lower trust requires higher difficulty (more leading zero bits).
     */
    fun createPowChallengeForPeer(peerId: String, tier: TrustTier): PowChallenge {
        val difficulty = when (tier) {
            TrustTier.TRUSTED_CORE -> 6 // ~64 hash attempts (<1ms)
            TrustTier.NEUTRAL_VERIFIED -> 10 // ~1,024 hash attempts (~5ms)
            TrustTier.SUSPICIOUS -> 14 // ~16,384 hash attempts (~40ms)
            TrustTier.QUARANTINED -> 18 // ~262,144 hash attempts (~500ms)
        }
        val challenge = PowEngine.createChallenge(peerId, difficulty)
        activeChallenges[challenge.challengeId] = challenge
        return challenge
    }

    /**
     * Verifies a Proof-of-Work solution submitted by a sender node.
     */
    suspend fun verifyAndApplyPowSolution(token: PowSolutionToken): Boolean = mutex.withLock {
        val challenge = activeChallenges[token.challengeId] ?: return@withLock false
        val isValid = PowEngine.verify(challenge, token)

        if (isValid) {
            activeChallenges.remove(token.challengeId)
            // Reward peer with reputation increase
            applyReputationEvent(token.solverNodeId, ReputationEvent.POW_TOKEN_VERIFIED, "Solved PoW puzzle ${token.challengeId} (${challenge.difficultyLeadingZeroBits} bits)")
            // Reset rate limiter bucket to allow immediate burst
            rateLimiter.resetPeer(token.solverNodeId)
            _securityMetrics.value = _securityMetrics.value.copy(
                powTokensVerified = _securityMetrics.value.powTokensVerified + 1
            )
            return@withLock true
        } else {
            recordReputationPenalty(token.solverNodeId, ReputationEvent.MALFORMED_CHUNK, "Failed PoW puzzle verification ${token.challengeId}")
            return@withLock false
        }
    }

    /**
     * Records positive peer behavior (valid bundle delivery, DelAck confirmed).
     */
    suspend fun recordPositiveInteraction(peerId: String, event: ReputationEvent, details: String) = mutex.withLock {
        applyReputationEvent(peerId, event, details)
    }

    /**
     * Records a security penalty against a peer. Auto-quarantines if score drops below threshold.
     */
    suspend fun recordReputationPenalty(peerId: String, event: ReputationEvent, details: String) {
        applyReputationEvent(peerId, event, details)
    }

    /**
     * Manually overrides quarantine/blacklist state of a peer node.
     */
    suspend fun togglePeerQuarantine(peerId: String, quarantined: Boolean, reason: String = "Admin operator toggle") = mutex.withLock {
        val peer = reputationDao.getReputationByPeerId(peerId) ?: return@withLock
        val newScore = if (quarantined) (peer.reputationScore - 40).coerceAtLeast(0) else 50
        val newTier = TrustTier.fromScore(newScore)

        val updated = peer.copy(
            isQuarantined = quarantined,
            reputationScore = newScore,
            trustTier = newTier.name,
            quarantineReason = if (quarantined) reason else null,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
        reputationDao.update(updated)

        reputationDao.insertAuditLog(
            ReputationAuditLogEntity(
                targetPeerId = peerId,
                eventType = if (quarantined) "MANUAL_QUARANTINE" else "UNQUARANTINED",
                scoreDelta = if (quarantined) -40 else +30,
                newScore = newScore,
                reasonDescription = reason
            )
        )

        if (quarantined) {
            broadcastQuarantineAlert(peerId, reason)
        }
    }

    /**
     * Broadcasts a signed reputation gossip attestation frame over the mesh network.
     */
    suspend fun broadcastAttestationGossip(targetPeerId: String, scoreDelta: Int, reason: String) {
        val attestation = ReputationAttestation(
            reporterNodeId = localNodeId,
            targetNodeId = targetPeerId,
            scoreDelta = scoreDelta,
            reason = reason,
            timestampEpochMs = System.currentTimeMillis()
        )

        // Sign attestation payload with local private key
        val keyPair = identityKeyManager.getOrCreateIdentityKeyPair()
        val signature = EccCryptoEngine.sign(keyPair.private, attestation.toSigningPayload())
        val hexSignature = signature.joinToString("") { byte -> "%02x".format(byte) }
        val signedAttestation = attestation.copy(
            signatureHex = hexSignature
        )

        val payload = serializeAttestation(signedAttestation)
        val frame = TransportFrame(
            opcode = TransportOpcode.OP_REPUTATION_ATTEST,
            sequenceNumber = (System.currentTimeMillis() and 0xFFFF).toInt(),
            payload = payload
        )
        _outboundReputationFrames.emit(frame)
        _securityMetrics.value = _securityMetrics.value.copy(
            gossipAttestationsSent = _securityMetrics.value.gossipAttestationsSent + 1
        )
    }

    /**
     * Ingests an incoming reputation gossip attestation from a remote peer.
     */
    suspend fun ingestAttestationGossip(frame: TransportFrame): Boolean = mutex.withLock {
        val attestation = deserializeAttestation(frame.payload) ?: return@withLock false
        if (attestation.reporterNodeId == localNodeId) return@withLock false // Ignore own

        // Dampen remote score delta to avoid Sybil amplification attack
        val dampenedDelta = (attestation.scoreDelta / 2).coerceIn(-15, 10)
        applyReputationEvent(
            attestation.targetNodeId,
            if (dampenedDelta >= 0) ReputationEvent.ATTESTATION_POSITIVE else ReputationEvent.INVALID_SIGNATURE,
            "Mesh Gossip from ${attestation.reporterNodeId.take(8)}: ${attestation.reason}",
            explicitDelta = dampenedDelta
        )
        _securityMetrics.value = _securityMetrics.value.copy(
            gossipAttestationsReceived = _securityMetrics.value.gossipAttestationsReceived + 1
        )
        return@withLock true
    }

    /**
     * Broadcasts an emergency quarantine alert to warn neighbor nodes of an active flooding/attacker node.
     */
    private suspend fun broadcastQuarantineAlert(targetPeerId: String, reason: String) {
        val payload = "$localNodeId:$targetPeerId:$reason:${System.currentTimeMillis()}".toByteArray(Charsets.UTF_8)
        val frame = TransportFrame(
            opcode = TransportOpcode.OP_QUARANTINE_ALERT,
            sequenceNumber = (System.currentTimeMillis() and 0xFFFF).toInt(),
            payload = payload
        )
        _outboundReputationFrames.emit(frame)
        _securityMetrics.value = _securityMetrics.value.copy(
            quarantinesEnacted = _securityMetrics.value.quarantinesEnacted + 1
        )
    }

    private suspend fun applyReputationEvent(
        peerId: String,
        event: ReputationEvent,
        details: String,
        explicitDelta: Int? = null
    ) {
        val peer = getOrCreatePeerReputation(peerId, "Peer-${peerId.take(8)}")
        val delta = explicitDelta ?: event.scoreDelta
        val newScore = (peer.reputationScore + delta).coerceIn(0, 100)
        val newTier = TrustTier.fromScore(newScore)
        val autoQuarantine = newScore <= TrustTier.QUARANTINED.minScore && !peer.isQuarantined

        val updated = peer.copy(
            reputationScore = newScore,
            trustTier = newTier.name,
            validDeliveriesCount = if (event == ReputationEvent.BUNDLE_DELIVERED_VALID) peer.validDeliveriesCount + 1 else peer.validDeliveriesCount,
            verifiedDelAcksCount = if (event == ReputationEvent.DELACK_CONFIRMED) peer.verifiedDelAcksCount + 1 else peer.verifiedDelAcksCount,
            verifiedPowTokensCount = if (event == ReputationEvent.POW_TOKEN_VERIFIED) peer.verifiedPowTokensCount + 1 else peer.verifiedPowTokensCount,
            rateLimitViolationsCount = if (event == ReputationEvent.RATE_LIMIT_EXCEEDED) peer.rateLimitViolationsCount + 1 else peer.rateLimitViolationsCount,
            replayFloodViolationsCount = if (event == ReputationEvent.DUPLICATE_FLOOD_ATTEMPT) peer.replayFloodViolationsCount + 1 else peer.replayFloodViolationsCount,
            invalidSignatureCount = if (event == ReputationEvent.INVALID_SIGNATURE) peer.invalidSignatureCount + 1 else peer.invalidSignatureCount,
            isQuarantined = peer.isQuarantined || autoQuarantine,
            quarantineReason = if (autoQuarantine) "Reputation score collapsed to $newScore: $details" else peer.quarantineReason,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
        reputationDao.update(updated)

        reputationDao.insertAuditLog(
            ReputationAuditLogEntity(
                targetPeerId = peerId,
                eventType = event.name,
                scoreDelta = delta,
                newScore = newScore,
                reasonDescription = details
            )
        )

        if (autoQuarantine) {
            broadcastQuarantineAlert(peerId, "Auto-quarantined due to repeated violations")
        }
    }

    private suspend fun getOrCreatePeerReputation(peerId: String, peerAlias: String): PeerReputationEntity {
        val existing = reputationDao.getReputationByPeerId(peerId)
        if (existing != null) return existing

        val fresh = PeerReputationEntity(
            peerNodeId = peerId,
            peerAlias = peerAlias,
            reputationScore = 50,
            trustTier = TrustTier.NEUTRAL_VERIFIED.name
        )
        reputationDao.insertOrUpdate(fresh)
        return fresh
    }

    private fun serializeAttestation(attestation: ReputationAttestation): ByteArray {
        val str = "${attestation.reporterNodeId}|${attestation.targetNodeId}|${attestation.scoreDelta}|${attestation.reason}|${attestation.timestampEpochMs}|${attestation.signatureHex}"
        return str.toByteArray(Charsets.UTF_8)
    }

    private fun deserializeAttestation(bytes: ByteArray): ReputationAttestation? {
        return try {
            val str = String(bytes, Charsets.UTF_8)
            val parts = str.split("|")
            if (parts.size >= 6) {
                ReputationAttestation(
                    reporterNodeId = parts[0],
                    targetNodeId = parts[1],
                    scoreDelta = parts[2].toInt(),
                    reason = parts[3],
                    timestampEpochMs = parts[4].toLong(),
                    signatureHex = parts[5]
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }
}

data class TransmissionEvaluation(
    val isAllowed: Boolean,
    val reason: String,
    val requiresPow: Boolean,
    val powChallenge: PowChallenge? = null
)

data class SecurityMetrics(
    val rateLimitDrops: Int = 0,
    val replayDrops: Int = 0,
    val quarantinedDrops: Int = 0,
    val powTokensVerified: Int = 0,
    val quarantinesEnacted: Int = 0,
    val gossipAttestationsSent: Int = 0,
    val gossipAttestationsReceived: Int = 0
)
