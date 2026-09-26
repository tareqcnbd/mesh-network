package com.example.core.crypto.ratchet

import com.example.core.crypto.EccCryptoEngine
import com.example.core.crypto.Hkdf
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.PublicKey

/**
 * End-to-End Cryptographic Double Ratchet Engine.
 *
 * Implements:
 * 1. Diffie-Hellman (DH) Asymmetric Ratchet:
 *    - Updates Root Key (RK) and derives Send/Receive Chains on new DH keys from peer.
 * 2. Symmetric-Key KDF Ratchet:
 *    - Advances send/receive chain keys via HMAC-SHA256, generating single-use Message Keys (MK).
 *    - Guarantees Forward Secrecy: Compromise of current keys does not compromise past messages.
 *    - Guarantees Break-in Recovery: Ephemeral DH updates heal future messages once attacker loses tap.
 * 3. Out-of-Order Message Handling:
 *    - Caches skipped message keys up to MAX_SKIPPED_KEYS with bounds to prevent denial-of-service.
 */
class DoubleRatchetSession private constructor(
    val peerNodeId: String,
    private var rootKey: ByteArray,
    private var ourDhKeyPair: KeyPair,
    private var peerDhPublicKey: PublicKey?,
    private var sendingChainKey: ByteArray?,
    private var receivingChainKey: ByteArray?,
    private var sendSequence: Int = 0,
    private var receiveSequence: Int = 0,
    private var previousSendChainLength: Int = 0
) {
    companion object {
        private const val MAX_SKIPPED_KEYS = 100
        private val CHAIN_KEY_INPUT = byteArrayOf(0x01)
        private val MESSAGE_KEY_INPUT = byteArrayOf(0x02)
        private val ROOT_INFO = "DoubleRatchetRoot".toByteArray(Charsets.UTF_8)
        private val CHAIN_INFO = "DoubleRatchetChain".toByteArray(Charsets.UTF_8)

        /**
         * Initialize an active session for the initiator.
         */
        fun initializeInitiator(
            peerNodeId: String,
            sharedMasterKey: ByteArray,
            peerRemoteDhPublicKey: PublicKey
        ): DoubleRatchetSession {
            val ourKeyPair = EccCryptoEngine.generateKeyPair()
            val dhSecret = EccCryptoEngine.performEcdh(ourKeyPair.private, peerRemoteDhPublicKey)
            val (newRootKey, newSendingChain) = kdfRootKey(sharedMasterKey, dhSecret)

            return DoubleRatchetSession(
                peerNodeId = peerNodeId,
                rootKey = newRootKey,
                ourDhKeyPair = ourKeyPair,
                peerDhPublicKey = peerRemoteDhPublicKey,
                sendingChainKey = newSendingChain,
                receivingChainKey = null,
                sendSequence = 0,
                receiveSequence = 0,
                previousSendChainLength = 0
            )
        }

        /**
         * Initialize an active session for the receiver.
         */
        fun initializeReceiver(
            peerNodeId: String,
            sharedMasterKey: ByteArray,
            ourPreSharedKeyPair: KeyPair
        ): DoubleRatchetSession {
            return DoubleRatchetSession(
                peerNodeId = peerNodeId,
                rootKey = sharedMasterKey,
                ourDhKeyPair = ourPreSharedKeyPair,
                peerDhPublicKey = null,
                sendingChainKey = null,
                receivingChainKey = null,
                sendSequence = 0,
                receiveSequence = 0,
                previousSendChainLength = 0
            )
        }

        private fun kdfRootKey(currentRootKey: ByteArray, dhOut: ByteArray): Pair<ByteArray, ByteArray> {
            val derived = Hkdf.deriveKey(currentRootKey, dhOut, ROOT_INFO, 64)
            val newRootKey = derived.copyOfRange(0, 32)
            val newChainKey = derived.copyOfRange(32, 64)
            return Pair(newRootKey, newChainKey)
        }
    }

    // Skipped message keys stored as (PeerKeyBytesHex + sequenceNumber) -> MessageKey (32 bytes)
    private val skippedMessageKeys = LinkedHashMap<String, ByteArray>()
    private var lastActivityEpochMs: Long = System.currentTimeMillis()

    @Synchronized
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray? = null): RatchetMessage {
        val currentSendingChain = sendingChainKey
            ?: throw IllegalStateException("Sending chain not established")

        // Advance symmetric sending chain
        val (nextChainKey, messageKey) = kdfChainKey(currentSendingChain)
        sendingChainKey = nextChainKey

        val ephemeralPubCompressed = EccCryptoEngine.encodePublicKeyCompressed(ourDhKeyPair.public)
        val seq = sendSequence
        sendSequence++

        // Prepare Associated Authenticated Data (AAD): PubKey + Sequence + PrevLength
        val aad = buildAssociatedData(ephemeralPubCompressed, seq, previousSendChainLength, associatedData)
        val ciphertext = EccCryptoEngine.encryptAesGcm(messageKey, plaintext, aad)

        lastActivityEpochMs = System.currentTimeMillis()

        return RatchetMessage(
            ephemeralPublicKeyBytes = ephemeralPubCompressed,
            sequenceNumber = seq,
            previousChainLength = previousSendChainLength,
            ciphertext = ciphertext
        )
    }

    @Synchronized
    fun decrypt(message: RatchetMessage, associatedData: ByteArray? = null): ByteArray {
        val messageDhPublicKey = EccCryptoEngine.decodePublicKeyCompressed(message.ephemeralPublicKeyBytes)
        val keyCacheIdentifier = makeSkippedKeyId(message.ephemeralPublicKeyBytes, message.sequenceNumber)

        // 1. Check if key was previously skipped due to out-of-order delivery
        val cachedKey = skippedMessageKeys.remove(keyCacheIdentifier)
        if (cachedKey != null) {
            val aad = buildAssociatedData(message.ephemeralPublicKeyBytes, message.sequenceNumber, message.previousChainLength, associatedData)
            return EccCryptoEngine.decryptAesGcm(cachedKey, message.ciphertext, aad)
        }

        // 2. If new DH ephemeral key observed, step the asymmetric DH ratchet
        if (peerDhPublicKey == null || !peerDhPublicKey!!.encoded.contentEquals(messageDhPublicKey.encoded)) {
            skipMessageKeys(message.previousChainLength)
            dhRatchet(messageDhPublicKey)
        }

        // 3. Skip any missing message keys in the current receiving chain
        skipMessageKeys(message.sequenceNumber)

        // 4. Generate message key and advance receiving chain
        val currentReceivingChain = receivingChainKey
            ?: throw IllegalStateException("Receiving chain key unavailable")
        val (nextChainKey, messageKey) = kdfChainKey(currentReceivingChain)
        receivingChainKey = nextChainKey
        receiveSequence++

        val aad = buildAssociatedData(message.ephemeralPublicKeyBytes, message.sequenceNumber, message.previousChainLength, associatedData)
        val plaintext = EccCryptoEngine.decryptAesGcm(messageKey, message.ciphertext, aad)

        lastActivityEpochMs = System.currentTimeMillis()
        return plaintext
    }

    private fun dhRatchet(theirNewDhPublicKey: PublicKey) {
        previousSendChainLength = sendSequence
        sendSequence = 0
        receiveSequence = 0
        peerDhPublicKey = theirNewDhPublicKey

        // Step 1: Derive new receiving chain from their new DH key and our existing DH key
        val dhReceive = EccCryptoEngine.performEcdh(ourDhKeyPair.private, theirNewDhPublicKey)
        val (rootAfterReceive, newReceivingChain) = kdfRootKey(rootKey, dhReceive)
        rootKey = rootAfterReceive
        receivingChainKey = newReceivingChain

        // Step 2: Generate fresh DH key pair for our sending ratchet
        ourDhKeyPair = EccCryptoEngine.generateKeyPair()

        // Step 3: Derive new sending chain from our fresh DH key and their new DH key
        val dhSend = EccCryptoEngine.performEcdh(ourDhKeyPair.private, theirNewDhPublicKey)
        val (rootAfterSend, newSendingChain) = kdfRootKey(rootKey, dhSend)
        rootKey = rootAfterSend
        sendingChainKey = newSendingChain
    }

    private fun skipMessageKeys(untilSequence: Int) {
        val currentChain = receivingChainKey ?: return
        if (receiveSequence + 100 < untilSequence) {
            throw IllegalArgumentException("Message sequence gap too large to buffer ($untilSequence)")
        }

        var chain = currentChain
        val currentPeerPubBytes = peerDhPublicKey?.let { EccCryptoEngine.encodePublicKeyCompressed(it) }
            ?: ByteArray(0)

        while (receiveSequence < untilSequence) {
            val (nextChain, skippedMessageKey) = kdfChainKey(chain)
            chain = nextChain
            val id = makeSkippedKeyId(currentPeerPubBytes, receiveSequence)

            // Bound cache size to prevent memory exhaustion
            if (skippedMessageKeys.size >= MAX_SKIPPED_KEYS) {
                val oldestKey = skippedMessageKeys.keys.iterator().next()
                skippedMessageKeys.remove(oldestKey)
            }
            skippedMessageKeys[id] = skippedMessageKey
            receiveSequence++
        }
        receivingChainKey = chain
    }

    private fun kdfChainKey(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val nextChainKey = Hkdf.hmacSha256(chainKey, CHAIN_KEY_INPUT)
        val messageKey = Hkdf.hmacSha256(chainKey, MESSAGE_KEY_INPUT)
        return Pair(nextChainKey, messageKey)
    }

    private fun makeSkippedKeyId(pubBytes: ByteArray, seq: Int): String {
        return pubBytes.joinToString("") { "%02x".format(it) } + ":" + seq
    }

    private fun buildAssociatedData(
        ephemeralPub: ByteArray,
        sequence: Int,
        prevLength: Int,
        userAad: ByteArray?
    ): ByteArray {
        val buffer = ByteBuffer.allocate(ephemeralPub.size + 8 + (userAad?.size ?: 0))
        buffer.put(ephemeralPub)
        buffer.putInt(sequence)
        buffer.putInt(prevLength)
        userAad?.let { buffer.put(it) }
        return buffer.array()
    }

    fun getSessionSnapshot(): RatchetSessionState {
        val rootDigestHex = Hkdf.sha256(rootKey).take(8).joinToString("") { "%02X".format(it) }
        return RatchetSessionState(
            peerNodeId = peerNodeId,
            sendSequenceNumber = sendSequence,
            receiveSequenceNumber = receiveSequence,
            skippedKeysCount = skippedMessageKeys.size,
            rootKeyDigestHex = rootDigestHex,
            lastRatchetStepEpochMs = lastActivityEpochMs
        )
    }
}
