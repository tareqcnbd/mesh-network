package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.emergency.EmergencyBeacon
import com.example.core.emergency.EmergencyDistressLevel
import com.example.core.voice.VoiceChannel
import com.example.core.voice.VoiceTransmissionState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeshVoiceTab(
    transmissionState: VoiceTransmissionState,
    selectedChannel: VoiceChannel,
    channels: List<VoiceChannel>,
    activeSpeaker: String?,
    audioLevel: Float,
    waveformSamples: List<Float>,
    isSosActive: Boolean,
    myActiveBeacon: EmergencyBeacon?,
    receivedSosBeacons: List<EmergencyBeacon>,
    hasAudioPermission: Boolean,
    onRequestAudioPermission: () -> Unit,
    onSelectChannel: (VoiceChannel) -> Unit,
    onStartTransmitting: () -> Unit,
    onStopTransmitting: () -> Unit,
    onTriggerSos: (EmergencyDistressLevel, String) -> Unit,
    onCancelSos: () -> Unit,
    onSimulateIncomingVoice: () -> Unit,
    onSimulateIncomingSos: () -> Unit,
    onTestAlarmSiren: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Audio Permission Warning Banner if missing
        if (!hasAudioPermission) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.MicOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Microphone Permission Required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Grant RECORD_AUDIO to capture low-latency mesh walkie-talkie packets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                    Button(
                        onClick = onRequestAudioPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("btn_grant_audio_permission")
                    ) {
                        Text("Grant")
                    }
                }
            }
        }

        // Active Emergency SOS Alert Banner if local node triggered SOS
        if (isSosActive) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CrisisAlert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "EMERGENCY SOS BROADCAST ACTIVE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Continuous high-priority distress beacon broadcasting across all wireless radios and cloud relays.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onError.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onCancelSos,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("btn_cancel_sos_top")
                    ) {
                        Text("CANCEL SOS / SEND ALL-CLEAR", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Channel Selection Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Radio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mesh Voice Channel",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    channels.forEach { ch ->
                        val isSelected = selectedChannel.id == ch.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectChannel(ch) },
                            label = { Text(ch.name) },
                            colors = if (ch.isEmergency) {
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            } else {
                                FilterChipDefaults.filterChipColors()
                            },
                            modifier = Modifier.testTag("chip_channel_${ch.id}")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = selectedChannel.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // PTT Walkie-Talkie Centerpiece
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Header
                Text(
                    text = when (transmissionState) {
                        VoiceTransmissionState.TRANSMITTING -> "TRANSMITTING (LIVE MIC)"
                        VoiceTransmissionState.RECEIVING -> "RECEIVING AUDIO FROM: ${activeSpeaker ?: "PEER"}"
                        VoiceTransmissionState.IDLE -> "PUSH-TO-TALK READY (STANDBY)"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (transmissionState) {
                        VoiceTransmissionState.TRANSMITTING -> MaterialTheme.colorScheme.error
                        VoiceTransmissionState.RECEIVING -> MaterialTheme.colorScheme.primary
                        VoiceTransmissionState.IDLE -> MaterialTheme.colorScheme.onSurface
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Waveform Canvas Visualization
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val activeColor = if (transmissionState == VoiceTransmissionState.TRANSMITTING) {
                        MaterialTheme.colorScheme.error
                    } else primaryColor

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val barCount = waveformSamples.size
                        val totalSpacing = (barCount - 1) * 4.dp.toPx()
                        val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(4f)

                        waveformSamples.forEachIndexed { index, sample ->
                            val barHeight = (size.height * sample.coerceIn(0.08f, 1f))
                            val x = index * (barWidth + 4.dp.toPx())
                            val y = (size.height - barHeight) / 2f

                            drawRoundRect(
                                color = activeColor,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // VU Audio Level Meter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { audioLevel.coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (transmissionState == VoiceTransmissionState.TRANSMITTING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(audioLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Circular PTT Button with Pulse Effect
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = if (transmissionState == VoiceTransmissionState.TRANSMITTING) 1.15f else 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_scale"
                )

                val buttonBgColor by animateColorAsState(
                    targetValue = when (transmissionState) {
                        VoiceTransmissionState.TRANSMITTING -> MaterialTheme.colorScheme.error
                        VoiceTransmissionState.RECEIVING -> MaterialTheme.colorScheme.primary
                        VoiceTransmissionState.IDLE -> MaterialTheme.colorScheme.primaryContainer
                    },
                    label = "btn_bg_color"
                )

                val iconColor by animateColorAsState(
                    targetValue = when (transmissionState) {
                        VoiceTransmissionState.TRANSMITTING -> MaterialTheme.colorScheme.onError
                        VoiceTransmissionState.RECEIVING -> MaterialTheme.colorScheme.onPrimary
                        VoiceTransmissionState.IDLE -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    label = "btn_icon_color"
                )

                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(buttonBgColor)
                        .border(
                            width = 4.dp,
                            color = if (transmissionState == VoiceTransmissionState.TRANSMITTING) Color.Red else Color.Transparent,
                            shape = CircleShape
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onStartTransmitting()
                                    tryAwaitRelease()
                                    onStopTransmitting()
                                }
                            )
                        }
                        .testTag("btn_ptt_main"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = when (transmissionState) {
                                VoiceTransmissionState.TRANSMITTING -> Icons.Default.Mic
                                VoiceTransmissionState.RECEIVING -> Icons.Default.PhoneInTalk
                                VoiceTransmissionState.IDLE -> Icons.Default.Mic
                            },
                            contentDescription = "Push To Talk",
                            tint = iconColor,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (transmissionState == VoiceTransmissionState.TRANSMITTING) "RELEASE" else "HOLD PTT",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = iconColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Press and hold to transmit 16kHz PCM audio across mesh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Simulation Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSimulateIncomingVoice,
                        modifier = Modifier.weight(1f).testTag("btn_simulate_voice")
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulate Call")
                    }

                    OutlinedButton(
                        onClick = onTestAlarmSiren,
                        modifier = Modifier.weight(1f).testTag("btn_test_siren")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Siren")
                    }
                }
            }
        }

        // Emergency SOS Beaconing Section
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CrisisAlert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Emergency SOS & Distress Beacon",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dispatches top-priority (BundlePriority.EMERGENCY) distress beacons carrying GPS coordinates and sounds acoustic siren alarm across all reachable peers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (!isSosActive) {
                    Button(
                        onClick = {
                            onTriggerSos(
                                EmergencyDistressLevel.SOS_CRITICAL,
                                "CRITICAL: Immediate assistance requested. Off-grid SOS beacon."
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("btn_trigger_sos")
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TRIGGER EMERGENCY SOS", fontWeight = FontWeight.Black)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onSimulateIncomingSos,
                        modifier = Modifier.fillMaxWidth().testTag("btn_simulate_incoming_sos")
                    ) {
                        Text("Simulate Peer SOS Beacon (Scout Charlie)")
                    }
                } else {
                    Button(
                        onClick = onCancelSos,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("btn_cancel_sos")
                    ) {
                        Text("CANCEL SOS / BROADCAST ALL-CLEAR", fontWeight = FontWeight.Bold)
                    }
                }

                // Received Peer Beacons List
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Received Distress Beacons (${receivedSosBeacons.size}):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (receivedSosBeacons.isEmpty()) {
                    Text(
                        text = "No active emergency distress signals detected in range.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    receivedSosBeacons.forEach { beacon ->
                        Surface(
                            color = if (beacon.isCancelled) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${beacon.senderAlias} (${beacon.distressLevel.label})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (beacon.isCancelled) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFC62828)
                                    )
                                    Surface(
                                        color = if (beacon.isCancelled) Color(0xFFE0E0E0) else Color(0xFFFFCDD2),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (beacon.isCancelled) "RESOLVED" else "CRITICAL",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (beacon.isCancelled) Color.DarkGray else Color(0xFFB71C1C)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = beacon.distressMessage,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Coordinates: ${beacon.latitude ?: "N/A"}, ${beacon.longitude ?: "N/A"} • Battery: ${beacon.batteryPct}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
