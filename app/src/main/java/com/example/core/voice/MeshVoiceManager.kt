package com.example.core.voice

import android.content.Context
import android.util.Log
import com.example.core.transport.packet.TransportFrame
import com.example.core.transport.packet.TransportOpcode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.random.Random

/**
 * High-level coordinator for Push-To-Talk (PTT) mesh voice communications.
 * Broadcasts VoicePacket frames over local UDP port 48892 and delivers received audio
 * to the JitterBuffer and VoiceAudioEngine.
 */
class MeshVoiceManager(
    private val context: Context,
    private val localNodeId: String,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "MeshVoiceManager"
        const val VOICE_UDP_PORT = 48892
    }

    private val audioEngine = VoiceAudioEngine(context, scope)

    private val _transmissionState = MutableStateFlow(VoiceTransmissionState.IDLE)
    val transmissionState: StateFlow<VoiceTransmissionState> = _transmissionState.asStateFlow()

    private val _selectedChannel = MutableStateFlow(VoiceChannel.CHANNEL_ALL_CALL)
    val selectedChannel: StateFlow<VoiceChannel> = _selectedChannel.asStateFlow()

    private val _activeSpeakerNodeId = MutableStateFlow<String?>(null)
    val activeSpeakerNodeId: StateFlow<String?> = _activeSpeakerNodeId.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _waveformSamples = MutableStateFlow<List<Float>>(List(16) { 0.05f })
    val waveformSamples: StateFlow<List<Float>> = _waveformSamples.asStateFlow()

    private var udpSocket: DatagramSocket? = null
    private var listenJob: Job? = null
    private var simulationJob: Job? = null

    var onVoipStateChanged: ((Boolean) -> Unit)? = null
    var onFrameGenerated: ((TransportFrame) -> Unit)? = null

    init {
        audioEngine.onPacketCaptured = { packet ->
            broadcastVoicePacket(packet)
        }

        audioEngine.onLocalRmsCalculated = { rms ->
            _audioLevel.value = rms
            pushWaveformSample(rms)
        }

        startListening()
    }

    fun selectChannel(channel: VoiceChannel) {
        _selectedChannel.value = channel
    }

    fun startTransmitting() {
        if (_transmissionState.value == VoiceTransmissionState.TRANSMITTING) return

        _transmissionState.value = VoiceTransmissionState.TRANSMITTING
        _activeSpeakerNodeId.value = localNodeId
        onVoipStateChanged?.invoke(true)

        // Audio PTT Roger beep chime
        audioEngine.playTone(880.0, 60, 0.4f)

        audioEngine.startCapture(
            channelId = _selectedChannel.value.id,
            localNodeId = localNodeId
        )
    }

    fun stopTransmitting() {
        if (_transmissionState.value != VoiceTransmissionState.TRANSMITTING) return

        audioEngine.stopCapture()
        _transmissionState.value = VoiceTransmissionState.IDLE
        _activeSpeakerNodeId.value = null
        _audioLevel.value = 0f
        onVoipStateChanged?.invoke(false)

        // PTT release squelch / roger tone
        audioEngine.playTone(440.0, 40, 0.3f)
    }

    private fun broadcastVoicePacket(packet: VoicePacket) {
        scope.launch(Dispatchers.IO) {
            val payload = VoicePacket.serialize(packet)

            // 1. Physical Radio wire framing
            val frame = TransportFrame(
                opcode = TransportOpcode.OP_VOICE_STREAM,
                sequenceNumber = packet.sequenceNumber.toInt(),
                payload = payload
            )
            onFrameGenerated?.invoke(frame)

            // 2. Direct local subnet UDP broadcast (for Wi-Fi / Hotspot / LAN)
            try {
                val broadcastAddress = InetAddress.getByName("255.255.255.255")
                val datagram = DatagramPacket(payload, payload.size, broadcastAddress, VOICE_UDP_PORT)
                udpSocket?.send(datagram)
            } catch (e: Exception) {
                // Ignore transient socket broadcast errors
            }
        }
    }

    private fun startListening() {
        listenJob?.cancel()
        listenJob = scope.launch(Dispatchers.IO) {
            try {
                udpSocket = DatagramSocket(VOICE_UDP_PORT).apply {
                    broadcast = true
                    reuseAddress = true
                }

                val buffer = ByteArray(2048)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpSocket?.receive(packet)

                    val data = packet.data.copyOfRange(0, packet.length)
                    val voicePacket = VoicePacket.deserialize(data)

                    if (voicePacket != null && voicePacket.senderNodeId != localNodeId) {
                        handleIncomingVoicePacket(voicePacket)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Voice UDP listening stopped or unavailable: ${e.message}")
            }
        }
    }

    fun handleIncomingVoicePacket(packet: VoicePacket) {
        // Filter by channel (emergency channel 9 breaks through all channels)
        if (packet.channelId != _selectedChannel.value.id && packet.channelId != VoiceChannel.CHANNEL_EMERGENCY.id) {
            return
        }

        if (_transmissionState.value != VoiceTransmissionState.TRANSMITTING) {
            _transmissionState.value = VoiceTransmissionState.RECEIVING
            _activeSpeakerNodeId.value = packet.senderNodeId
            _audioLevel.value = packet.rmsEnergy
            pushWaveformSample(packet.rmsEnergy)

            audioEngine.startPlayback()
            audioEngine.jitterBuffer.push(packet)
        }
    }

    private fun pushWaveformSample(sample: Float) {
        val current = _waveformSamples.value.toMutableList()
        if (current.size >= 16) {
            current.removeAt(0)
        }
        current.add(sample.coerceIn(0.05f, 1.0f))
        _waveformSamples.value = current
    }

    /**
     * Simulates receiving an incoming voice transmission from a peer node
     * for verification without a second hardware device.
     */
    fun simulateIncomingVoiceTransmission(peerAlias: String) {
        if (_transmissionState.value != VoiceTransmissionState.IDLE) return

        simulationJob?.cancel()
        simulationJob = scope.launch(Dispatchers.IO) {
            _transmissionState.value = VoiceTransmissionState.RECEIVING
            _activeSpeakerNodeId.value = peerAlias
            audioEngine.startPlayback()

            // Play a short simulated radio contact chime
            audioEngine.playTone(660.0, 80, 0.4f)
            kotlinx.coroutines.delay(100)

            val dummyAudio = ByteArray(VoicePacket.FRAME_SIZE_BYTES)
            for (seq in 0..25) {
                if (!isActive) break
                val simulatedRms = Random.nextFloat() * 0.7f + 0.1f
                _audioLevel.value = simulatedRms
                pushWaveformSample(simulatedRms)

                val packet = VoicePacket(
                    sequenceNumber = seq.toLong(),
                    channelId = _selectedChannel.value.id,
                    senderNodeId = peerAlias,
                    timestampMs = System.currentTimeMillis(),
                    rmsEnergy = simulatedRms,
                    audioData = dummyAudio
                )
                audioEngine.jitterBuffer.push(packet)
                kotlinx.coroutines.delay(20)
            }

            // Simulated transmission end
            audioEngine.playTone(520.0, 60, 0.3f)
            kotlinx.coroutines.delay(100)

            _transmissionState.value = VoiceTransmissionState.IDLE
            _activeSpeakerNodeId.value = null
            _audioLevel.value = 0f
            audioEngine.stopPlayback()
        }
    }

    fun playAcousticAlarm() {
        scope.launch {
            // Alternating two-tone emergency alarm (800Hz / 1000Hz)
            repeat(4) {
                audioEngine.playTone(800.0, 180, 0.8f)
                kotlinx.coroutines.delay(200)
                audioEngine.playTone(1000.0, 180, 0.8f)
                kotlinx.coroutines.delay(200)
            }
        }
    }

    fun release() {
        stopTransmitting()
        listenJob?.cancel()
        simulationJob?.cancel()
        try {
            udpSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing UDP socket", e)
        }
        audioEngine.stopCapture()
        audioEngine.stopPlayback()
    }
}
