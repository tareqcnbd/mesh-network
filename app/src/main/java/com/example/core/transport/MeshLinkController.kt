package com.example.core.transport

import android.util.Log
import com.example.core.crypto.EccCryptoEngine
import com.example.core.crypto.IdentityKeyManager
import com.example.core.crypto.MeshSessionCrypto
import com.example.core.dtn.BloomFilter
import com.example.core.dtn.db.ChatMessageDao
import com.example.core.dtn.model.BundlePriority
import com.example.core.dtn.model.BundleStatus
import com.example.core.dtn.model.ChatMessageEntity
import com.example.core.dtn.model.DtnBundleEntity
import com.example.core.dtn.model.MeshPeerEntity
import com.example.core.dtn.sync.DtnSyncEngine
import com.example.core.radio.DirectLinkType
import com.example.core.transport.packet.BundleCodec
import com.example.core.transport.packet.BundlePayloadRouter
import com.example.core.transport.packet.BundleSignatureVerifier
import com.example.core.transport.packet.ChatPayload
import com.example.core.transport.packet.HandshakePayload
import com.example.core.transport.packet.TransportFrame
import com.example.core.transport.packet.TransportOpcode
import com.example.core.transport.packet.hexToByteArray
import com.example.core.transport.packet.toHexString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.KeyPair
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Control-plane for a live mesh link: identity handshake, Bloom anti-entropy,
 * DTN bundle flood, and verified payload dispatch.
 */
class MeshLinkController(
    private val localNodeId: String,
    private val localAlias: String,
    private val identityKeyManager: IdentityKeyManager,
    private val syncEngine: DtnSyncEngine,
    private val chatDao: ChatMessageDao,
    private val transportSwitcher: TransportSwitcher,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "MeshLinkController"
        private const val HANDSHAKE_RETRIES = 4
        private const val HANDSHAKE_RETRY_MS = 400L
        const val BROADCAST_DEST = "*"
        const val CAPTION_SENT = "Sent via Mesh"
        const val CAPTION_WAITING = "Waiting for peer"
        const val CAPTION_RECEIVED = "Received via Mesh"
    }

    private val ephemeralKeyPair: KeyPair = EccCryptoEngine.generateKeyPair()
    private val identityPublic = identityKeyManager.getIdentityPublicKeyCompressed()

    private val transportToIdentity = ConcurrentHashMap<String, String>()
    private val identityToTransport = ConcurrentHashMap<String, String>()
    private val sessionKeys = ConcurrentHashMap<String, ByteArray>()
    private val handshakeSent = ConcurrentHashMap.newKeySet<String>()
    private val peerAliases = ConcurrentHashMap<String, String>()
    private val peerIdentityKeys = ConcurrentHashMap<String, String>()

    var onMeshError: ((String) -> Unit)? = null
    var payloadRouter: BundlePayloadRouter? = null

    fun attach() {
        transportSwitcher.packetListener = { fromPeerId, frame, transportType ->
            scope.launch {
                handleFrame(fromPeerId, frame, transportType)
            }
        }
        transportSwitcher.peerLifecycleListener = { peer, connected ->
            if (connected) {
                scope.launch { sendHandshakeWithRetry(peer.peerId) }
            } else {
                val identity = transportToIdentity.remove(peer.peerId)
                if (identity != null) {
                    identityToTransport.remove(identity)
                }
                handshakeSent.remove(peer.peerId)
            }
        }
    }

    fun sessionKeyFor(identityNodeId: String): ByteArray? = sessionKeys[identityNodeId]

    fun identityForTransport(transportPeerId: String): String? = transportToIdentity[transportPeerId]

    fun transportForIdentity(identityNodeId: String): String? = identityToTransport[identityNodeId]

    suspend fun sendChatMessage(peerNodeId: String, plaintext: String): Boolean {
        val dest = peerNodeId.ifBlank { BROADCAST_DEST }
        val isBroadcast = dest == BROADCAST_DEST
        val payloadBytes = ChatPayload.encode(plaintext)
        val signature = identityKeyManager.signWithIdentity(payloadBytes)
        val sessionKey = sessionKeys[dest]

        if (!isBroadcast && sessionKey == null) {
            val bundle = buildBundle(dest, payloadBytes, signature, isBroadcast)
            syncEngine.storeLocalBundle(bundle)
            persistChat(
                bundleId = bundle.bundleId,
                peerNodeId = dest,
                peerAlias = peerAliases[dest] ?: dest,
                isOutgoing = true,
                plaintext = plaintext,
                caption = CAPTION_WAITING
            )
            return false
        }

        val wirePayload = if (!isBroadcast && sessionKey != null) {
            EccCryptoEngine.encryptAesGcm(
                key = sessionKey,
                plaintext = payloadBytes,
                associatedData = dest.toByteArray(Charsets.UTF_8)
            )
        } else {
            payloadBytes
        }
        val bundle = buildBundle(dest, wirePayload, signature, isBroadcast)
        syncEngine.storeLocalBundle(bundle)
        val flooded = floodBundle(bundle, excludeTransportPeerId = null)
        persistChat(
            bundleId = bundle.bundleId,
            peerNodeId = dest,
            peerAlias = peerAliases[dest] ?: dest,
            isOutgoing = true,
            plaintext = plaintext,
            caption = if (flooded) CAPTION_SENT else CAPTION_WAITING
        )
        return flooded
    }

    suspend fun floodBundle(bundle: DtnBundleEntity, excludeTransportPeerId: String?): Boolean {
        val payload = BundleCodec.encode(bundle)
        val targets = transportSwitcher.connectedPeers.value.keys.filter { it != excludeTransportPeerId }
        if (targets.isEmpty()) {
            Log.i(TAG, "No connected radios to flood bundle ${bundle.bundleId.take(8)}")
            return false
        }
        var any = false
        for (transportPeerId in targets) {
            val ok = transportSwitcher.sendFrame(
                targetPeerId = transportPeerId,
                opcode = TransportOpcode.OP_BUNDLE_COMPLETE,
                payload = payload
            )
            if (ok) any = true
        }
        return any
    }

    private fun buildBundle(
        dest: String,
        wirePayload: ByteArray,
        signature: ByteArray,
        isBroadcast: Boolean
    ): DtnBundleEntity {
        return DtnBundleEntity(
            bundleId = UUID.randomUUID().toString(),
            sourceNodeId = localNodeId,
            destinationNodeId = dest,
            isBroadcast = isBroadcast,
            hopCount = 0,
            maxHops = 8,
            priority = BundlePriority.HIGH,
            expiresAtEpochMs = System.currentTimeMillis() + 86_400_000L,
            payloadSizeBytes = wirePayload.size.toLong(),
            encryptedPayloadHex = wirePayload.toHexString(),
            senderSignatureHex = signature.toHexString(),
            status = BundleStatus.PENDING_CARRIED
        )
    }

    private suspend fun sendHandshakeWithRetry(transportPeerId: String) {
        if (!handshakeSent.add(transportPeerId)) return
        val payload = buildHandshake()
        val bytes = payload.toByteArray()
        repeat(HANDSHAKE_RETRIES) { attempt ->
            val ok = transportSwitcher.sendFrame(
                targetPeerId = transportPeerId,
                opcode = TransportOpcode.OP_HANDSHAKE_IK,
                payload = bytes
            )
            if (ok) {
                Log.i(TAG, "Handshake sent to $transportPeerId (attempt ${attempt + 1})")
                return
            }
            delay(HANDSHAKE_RETRY_MS)
        }
        handshakeSent.remove(transportPeerId)
        val message = "Handshake failed to reach neighbor $transportPeerId"
        Log.w(TAG, message)
        onMeshError?.invoke(message)
    }

    private fun buildHandshake(): HandshakePayload {
        val ephPub = EccCryptoEngine.encodePublicKeyCompressed(ephemeralKeyPair.public)
        val toSign = HandshakePayload.signedBytes(localNodeId, localAlias, identityPublic, ephPub)
        val signature = identityKeyManager.signWithIdentity(toSign)
        return HandshakePayload(
            nodeId = localNodeId,
            alias = localAlias,
            identityPublicKey = identityPublic,
            ephemeralPublicKey = ephPub,
            signature = signature
        )
    }

    private suspend fun handleFrame(
        transportPeerId: String,
        frame: TransportFrame,
        transportType: DirectLinkType
    ) {
        when (frame.opcode) {
            TransportOpcode.OP_HANDSHAKE_IK -> handleHandshake(transportPeerId, frame.payload, transportType)
            TransportOpcode.OP_BLOOM_FILTER -> handleBloom(transportPeerId, frame.payload)
            TransportOpcode.OP_BUNDLE_COMPLETE -> handleBundle(transportPeerId, frame.payload)
            TransportOpcode.OP_BUNDLE_OFFER,
            TransportOpcode.OP_BUNDLE_REQUEST,
            TransportOpcode.OP_BUNDLE_CHUNK,
            TransportOpcode.OP_DELIVERY_ACK,
            TransportOpcode.OP_HEARTBEAT -> {
                Log.d(TAG, "Ignoring opcode ${frame.opcode} from $transportPeerId")
            }
            else -> Log.d(TAG, "Unhandled opcode ${frame.opcode} from $transportPeerId")
        }
    }

    private suspend fun handleHandshake(
        transportPeerId: String,
        payload: ByteArray,
        transportType: DirectLinkType
    ) {
        val handshake = HandshakePayload.fromByteArray(payload) ?: run {
            Log.w(TAG, "Invalid handshake from $transportPeerId")
            return
        }
        if (handshake.nodeId == localNodeId) return
        val peerPublic = try {
            EccCryptoEngine.decodePublicKeyCompressed(handshake.identityPublicKey)
        } catch (e: Exception) {
            Log.w(TAG, "Bad identity key in handshake", e)
            return
        }
        val verified = EccCryptoEngine.verify(peerPublic, handshake.signedBytes(), handshake.signature)
        if (!verified) {
            Log.w(TAG, "Handshake signature failed for ${handshake.nodeId}")
            return
        }
        val peerEph = try {
            EccCryptoEngine.decodePublicKeyCompressed(handshake.ephemeralPublicKey)
        } catch (e: Exception) {
            Log.w(TAG, "Bad ephemeral key in handshake", e)
            return
        }
        val sessionKey = MeshSessionCrypto.deriveSessionKey(ephemeralKeyPair, peerEph)
        sessionKeys[handshake.nodeId] = sessionKey
        transportToIdentity[transportPeerId] = handshake.nodeId
        identityToTransport[handshake.nodeId] = transportPeerId
        peerAliases[handshake.nodeId] = handshake.alias
        peerIdentityKeys[handshake.nodeId] = handshake.identityPublicKey.toHexString()

        val peerInfo = transportSwitcher.connectedPeers.value[transportPeerId]
        syncEngine.recordPeerContact(
            MeshPeerEntity(
                nodeId = handshake.nodeId,
                alias = handshake.alias,
                lastSeenEpochMs = System.currentTimeMillis(),
                rssiDbm = peerInfo?.rssi ?: -60,
                directLinkType = transportType.name,
                isDirectNeighbor = true,
                identityPublicKeyHex = handshake.identityPublicKey.toHexString()
            )
        )

        if (transportPeerId !in handshakeSent) {
            sendHandshakeWithRetry(transportPeerId)
        }
        sendBloom(transportPeerId)
        floodQueuedUnicast(handshake.nodeId)
    }

    private suspend fun floodQueuedUnicast(identityNodeId: String) {
        val sessionKey = sessionKeys[identityNodeId] ?: return
        val pending = syncEngine.getPendingBundlesForDestination(identityNodeId)
        for (bundle in pending) {
            if (bundle.sourceNodeId != localNodeId) continue
            val stored = try {
                bundle.encryptedPayloadHex.hexToByteArray()
            } catch (_: Exception) {
                continue
            }
            val wirePayload = if (ChatPayload.isChat(stored) || looksLikeJson(stored)) {
                EccCryptoEngine.encryptAesGcm(
                    key = sessionKey,
                    plaintext = stored,
                    associatedData = identityNodeId.toByteArray(Charsets.UTF_8)
                )
            } else {
                stored
            }
            val updated = bundle.copy(encryptedPayloadHex = wirePayload.toHexString())
            if (wirePayload !== stored) {
                syncEngine.replaceBundle(updated)
            }
            val flooded = floodBundle(updated, excludeTransportPeerId = null)
            if (flooded) {
                chatDao.updateCaption(bundle.bundleId, CAPTION_SENT)
            }
        }
    }

    private fun looksLikeJson(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val first = bytes[0].toInt().toChar()
        return first == '{' || first == '['
    }

    private suspend fun sendBloom(transportPeerId: String) {
        val filter = syncEngine.generateLocalInventoryFilter()
        transportSwitcher.sendFrame(
            targetPeerId = transportPeerId,
            opcode = TransportOpcode.OP_BLOOM_FILTER,
            payload = filter.toByteArray()
        )
    }

    private suspend fun handleBloom(transportPeerId: String, payload: ByteArray) {
        val peerFilter = BloomFilter.fromByteArray(payload)
        val missing = syncEngine.computeMissingBundlesForPeer(peerFilter)
        for (bundle in missing) {
            transportSwitcher.sendFrame(
                targetPeerId = transportPeerId,
                opcode = TransportOpcode.OP_BUNDLE_COMPLETE,
                payload = BundleCodec.encode(bundle)
            )
        }
    }

    private suspend fun handleBundle(transportPeerId: String, payload: ByteArray) {
        val incoming = BundleCodec.decode(payload) ?: return
        if (incoming.sourceNodeId == localNodeId) return

        val dest = incoming.destinationNodeId
        val forUs = dest == localNodeId || dest == BROADCAST_DEST
        val plaintext = plaintextPayload(incoming) ?: return

        if (forUs) {
            val publicKeyHex = peerIdentityKeys[incoming.sourceNodeId]
                ?: syncEngine.getPeerByNodeId(incoming.sourceNodeId)?.identityPublicKeyHex
            if (!BundleSignatureVerifier.verify(plaintext, incoming.senderSignatureHex, publicKeyHex)) {
                Log.w(TAG, "Rejecting bundle ${incoming.bundleId.take(8)}: signature failed")
                return
            }
        }

        val inserted = syncEngine.ingestIncomingBundle(incoming, senderPeerId = transportToIdentity[transportPeerId])
        if (!inserted) return

        if (forUs) {
            payloadRouter?.dispatch(incoming, plaintext)
        }

        if (!forUs || dest == BROADCAST_DEST) {
            val forwarded = incoming.copy(hopCount = incoming.hopCount + 1)
            if (forwarded.hopCount < forwarded.maxHops) {
                floodBundle(forwarded, excludeTransportPeerId = transportPeerId)
            }
        }
    }

    private fun plaintextPayload(bundle: DtnBundleEntity): ByteArray? {
        val cipherBytes = try {
            bundle.encryptedPayloadHex.hexToByteArray()
        } catch (_: Exception) {
            return null
        }
        if (bundle.destinationNodeId == localNodeId) {
            val sessionKey = sessionKeys[bundle.sourceNodeId]
            if (sessionKey != null) {
                try {
                    return EccCryptoEngine.decryptAesGcm(
                        key = sessionKey,
                        combinedCiphertext = cipherBytes,
                        associatedData = localNodeId.toByteArray(Charsets.UTF_8)
                    )
                } catch (_: Exception) {
                    // Fall through to raw bytes (broadcast-style payload).
                }
            }
        }
        return cipherBytes
    }

    suspend fun ingestChatMessage(bundle: DtnBundleEntity, plaintext: String) {
        persistIncomingChat(bundle, plaintext)
    }

    private suspend fun persistIncomingChat(bundle: DtnBundleEntity, plaintext: String) {
        if (chatDao.existsForBundle(bundle.bundleId)) return
        persistChat(
            bundleId = bundle.bundleId,
            peerNodeId = if (bundle.destinationNodeId == BROADCAST_DEST) BROADCAST_DEST else bundle.sourceNodeId,
            peerAlias = if (bundle.destinationNodeId == BROADCAST_DEST) {
                "Mesh"
            } else {
                peerAliases[bundle.sourceNodeId] ?: bundle.sourceNodeId
            },
            isOutgoing = false,
            plaintext = plaintext,
            caption = CAPTION_RECEIVED
        )
    }

    private suspend fun persistChat(
        bundleId: String,
        peerNodeId: String,
        peerAlias: String,
        isOutgoing: Boolean,
        plaintext: String,
        caption: String
    ) {
        chatDao.insert(
            ChatMessageEntity(
                messageId = UUID.randomUUID().toString(),
                bundleId = bundleId,
                peerNodeId = peerNodeId,
                peerAlias = peerAlias,
                isOutgoing = isOutgoing,
                plaintext = plaintext,
                deliveryCaption = caption
            )
        )
    }
}
