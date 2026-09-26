package com.example.core.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Handles low-level PCM audio recording and playback for real-time Push-to-Talk.
 * Format: 16,000 Hz, 16-bit Mono PCM (20ms frames).
 */
class VoiceAudioEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "VoiceAudioEngine"
        private const val SAMPLE_RATE = VoicePacket.SAMPLE_RATE_HZ
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var recordJob: Job? = null
    private var playbackJob: Job? = null

    private val isRecording = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)

    val jitterBuffer = JitterBuffer()

    var onPacketCaptured: ((VoicePacket) -> Unit)? = null
    var onLocalRmsCalculated: ((Float) -> Unit)? = null

    private var sequenceCounter = 0L

    @SuppressLint("MissingPermission")
    fun startCapture(channelId: Int, localNodeId: String) {
        if (isRecording.get()) return

        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufSize, VoicePacket.FRAME_SIZE_BYTES * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_IN,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)

            recordJob = scope.launch(Dispatchers.IO) {
                val frameBuffer = ByteArray(VoicePacket.FRAME_SIZE_BYTES)

                while (isActive && isRecording.get()) {
                    var bytesRead = 0
                    while (bytesRead < VoicePacket.FRAME_SIZE_BYTES && isActive && isRecording.get()) {
                        val read = audioRecord?.read(
                            frameBuffer,
                            bytesRead,
                            VoicePacket.FRAME_SIZE_BYTES - bytesRead
                        ) ?: -1

                        if (read > 0) {
                            bytesRead += read
                        } else {
                            break
                        }
                    }

                    if (bytesRead == VoicePacket.FRAME_SIZE_BYTES) {
                        val rms = calculateRms(frameBuffer)
                        onLocalRmsCalculated?.invoke(rms)

                        val packet = VoicePacket(
                            sequenceNumber = sequenceCounter++,
                            channelId = channelId,
                            senderNodeId = localNodeId,
                            timestampMs = System.currentTimeMillis(),
                            rmsEnergy = rms,
                            audioData = frameBuffer.copyOf()
                        )
                        onPacketCaptured?.invoke(packet)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            stopCapture()
        }
    }

    fun stopCapture() {
        isRecording.set(false)
        recordJob?.cancel()
        recordJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }

    fun startPlayback() {
        if (isPlaying.get()) return

        val minBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufSize, VoicePacket.FRAME_SIZE_BYTES * 4)

        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_OUT)
                .setEncoding(AUDIO_FORMAT)
                .build()

            audioTrack = AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            audioTrack?.play()
            isPlaying.set(true)

            playbackJob = scope.launch(Dispatchers.IO) {
                while (isActive && isPlaying.get()) {
                    val packet = jitterBuffer.poll()
                    if (packet != null) {
                        audioTrack?.write(
                            packet.audioData,
                            0,
                            packet.audioData.size,
                            AudioTrack.WRITE_BLOCKING
                        )
                    } else {
                        // Brief wait for jitter buffer fill
                        kotlinx.coroutines.delay(10)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack playback", e)
            stopPlayback()
        }
    }

    fun stopPlayback() {
        isPlaying.set(false)
        playbackJob?.cancel()
        playbackJob = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack", e)
        } finally {
            audioTrack = null
        }
    }

    /**
     * Synthesizes and plays a short frequency tone (e.g. SOS alarm or PTT beep).
     */
    fun playTone(frequencyHz: Double, durationMs: Int, volume: Float = 0.8f) {
        scope.launch(Dispatchers.IO) {
            val numSamples = (durationMs * SAMPLE_RATE) / 1000
            val samples = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val angle = 2.0 * Math.PI * i / (SAMPLE_RATE / frequencyHz)
                samples[i] = (sin(angle) * Short.MAX_VALUE * volume).toInt().toShort()
            }

            try {
                val toneTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_OUT)
                            .build()
                    )
                    .setBufferSizeInBytes(numSamples * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                toneTrack.write(samples, 0, samples.size)
                toneTrack.play()
                kotlinx.coroutines.delay(durationMs.toLong() + 50)
                toneTrack.stop()
                toneTrack.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error playing synthesized tone: ${e.message}")
            }
        }
    }

    private fun calculateRms(buffer: ByteArray): Float {
        var sum = 0.0
        val numShorts = buffer.size / 2
        for (i in 0 until numShorts) {
            val sample = (buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xFF)
            sum += sample * sample
        }
        val rms = sqrt(sum / numShorts)
        // Normalize against 16-bit max range (32768)
        return (rms / 16384.0).coerceIn(0.0, 1.0).toFloat()
    }
}
