package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.radio.RadioHealth
import com.example.core.radio.RadioStatus
import com.example.ui.theme.isMonrDark

/**
 * Signal indicator states for Monr Local Mesh Network
 */
enum class ConnectionSignalState(
    val label: String,
    val indicatorColor: Color,
    val haloColor: Color,
    val defaultDetail: String
) {
    GREEN(
        label = "Connected",
        indicatorColor = Color(0xFF10B981),
        haloColor = Color(0x3310B981),
        defaultDetail = "No internet"
    ),
    AMBER(
        label = "Looking",
        indicatorColor = Color(0xFFF59E0B),
        haloColor = Color(0x33F59E0B),
        defaultDetail = "Searching"
    ),
    DISCONNECTED(
        label = "Offline",
        indicatorColor = Color(0xFFEF4444),
        haloColor = Color(0x33EF4444),
        defaultDetail = "Offline"
    )
}

/**
 * Resolves the operational ConnectionSignalState from RadioStatus and discovered peer count.
 */
fun resolveSignalState(
    status: RadioStatus?,
    peerCount: Int,
    overrideState: ConnectionSignalState? = null
): ConnectionSignalState {
    if (overrideState != null) return overrideState
    if (status == null) {
        return if (peerCount > 0) ConnectionSignalState.GREEN else ConnectionSignalState.AMBER
    }
    return when {
        !status.isServiceRunning -> ConnectionSignalState.DISCONNECTED
        peerCount > 0 -> ConnectionSignalState.GREEN
        status.isMeshOperational -> ConnectionSignalState.GREEN
        status.isScanningActive -> ConnectionSignalState.AMBER
        status.bluetoothHealth == RadioHealth.DISABLED && status.wifiHealth == RadioHealth.DISABLED -> ConnectionSignalState.DISCONNECTED
        else -> ConnectionSignalState.AMBER
    }
}

/**
 * Animated color-coded signal dot indicator.
 */
@Composable
fun SignalIndicatorDot(
    state: ConnectionSignalState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "signal_dot_pulse")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state != ConnectionSignalState.DISCONNECTED) 1.55f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(16.dp)
            .testTag("signal_indicator_${state.name.lowercase()}")
            .semantics { contentDescription = "Signal status: ${state.label}" }
    ) {
        if (state != ConnectionSignalState.DISCONNECTED) {
            Box(
                modifier = Modifier
                    .size((9.dp * haloScale).coerceAtLeast(9.dp))
                    .background(state.haloColor, CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(state.indicatorColor, CircleShape)
        )
    }
}

/**
 * Persistent 'Connection Status' badge component.
 */
@Composable
fun ConnectionStatusBadge(
    modifier: Modifier = Modifier,
    signalState: ConnectionSignalState = ConnectionSignalState.GREEN,
    networkName: String = "Local Network",
    detailText: String? = null,
    showDetailsOnClick: Boolean = true,
    radioStatus: RadioStatus? = null,
    peerCount: Int = 0,
    onStateOverrideChange: ((ConnectionSignalState?) -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    val effectiveDetail = detailText ?: signalState.defaultDetail
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.45f)),
        tonalElevation = 2.dp,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (showDetailsOnClick) {
                    Modifier.clickable { showDialog = true }
                } else Modifier
            )
            .testTag("connection_status_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(max = 220.dp)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            SignalIndicatorDot(state = signalState)

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = networkName,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("txt_network_name")
            )

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(11.dp)
                    .background(scheme.outline.copy(alpha = 0.5f))
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = effectiveDetail,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = when (signalState) {
                    ConnectionSignalState.GREEN -> if (scheme.isMonrDark()) Color(0xFF34D399) else Color(0xFF059669)
                    ConnectionSignalState.AMBER -> if (scheme.isMonrDark()) Color(0xFFFBBF24) else Color(0xFFD97706)
                    ConnectionSignalState.DISCONNECTED -> if (scheme.isMonrDark()) Color(0xFFF87171) else Color(0xFFDC2626)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("txt_network_detail")
            )
        }
    }

    if (showDialog && showDetailsOnClick) {
        ConnectionDetailsDialog(
            currentState = signalState,
            radioStatus = radioStatus,
            peerCount = peerCount,
            onDismiss = { showDialog = false },
            onSelectSimulation = { simulated ->
                onStateOverrideChange?.invoke(simulated)
                showDialog = false
            }
        )
    }
}

/**
 * Overload of ConnectionStatusBadge that automatically resolves signal state from RadioStatus & peers.
 */
@Composable
fun ConnectionStatusBadge(
    status: RadioStatus?,
    peerCount: Int,
    modifier: Modifier = Modifier,
    overrideState: ConnectionSignalState? = null,
    detailText: String? = null,
    isHotspotRunning: Boolean = false,
    onStateOverrideChange: ((ConnectionSignalState?) -> Unit)? = null
) {
    val hotspotMode = isHotspotRunning
    val state = resolveSignalState(status, peerCount, overrideState)
    val internetDetail = if (status?.isInternetReachable == true) "Online" else "No internet"
    val defaultDetail = when {
        hotspotMode -> internetDetail
        state == ConnectionSignalState.AMBER -> if (status?.isScanningActive == true) "Scanning..." else "Searching"
        else -> internetDetail
    }

    ConnectionStatusBadge(
        modifier = modifier,
        signalState = if (hotspotMode) ConnectionSignalState.GREEN else state,
        networkName = if (hotspotMode) "Hotspot" else "Local Network",
        detailText = detailText ?: defaultDetail,
        radioStatus = status,
        peerCount = peerCount,
        onStateOverrideChange = onStateOverrideChange
    )
}

/**
 * Details Dialog triggered when tapping the Connection Status Badge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionDetailsDialog(
    currentState: ConnectionSignalState,
    radioStatus: RadioStatus?,
    peerCount: Int,
    onDismiss: () -> Unit,
    onSelectSimulation: (ConnectionSignalState?) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val internetAvailable = radioStatus?.isInternetReachable == true

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surface),
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("connection_details_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(currentState.haloColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (currentState) {
                                    ConnectionSignalState.GREEN -> Icons.Default.Sensors
                                    ConnectionSignalState.AMBER -> Icons.Default.SignalCellularAlt
                                    ConnectionSignalState.DISCONNECTED -> Icons.Default.WifiOff
                                },
                                contentDescription = null,
                                tint = currentState.indicatorColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Connection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface
                            )
                            Text(
                                text = "Nearby phones",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = scheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = scheme.outline.copy(alpha = 0.35f))
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, currentState.indicatorColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SignalIndicatorDot(state = currentState)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = currentState.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = currentState.indicatorColor
                            )
                            Text(
                                text = when (currentState) {
                                    ConnectionSignalState.GREEN -> "Connected. Nearby phones can message you."
                                    ConnectionSignalState.AMBER -> "Looking for nearby phones."
                                    ConnectionSignalState.DISCONNECTED -> "Offline. Turn on Bluetooth to find people nearby."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                ConnectionMetricRow(
                    label = "Nearby phones",
                    value = if (peerCount == 0) "None yet" else "$peerCount nearby",
                    icon = Icons.Default.CellTower
                )
                ConnectionMetricRow(
                    label = "Internet",
                    value = if (internetAvailable) "Wi-Fi or mobile data" else "Not available",
                    icon = if (internetAvailable) Icons.Default.Wifi else Icons.Default.WifiOff
                )

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Preview status",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SimulatorPill(
                        label = "Green",
                        selected = currentState == ConnectionSignalState.GREEN,
                        color = Color(0xFF10B981),
                        testTag = "btn_sim_green",
                        onClick = { onSelectSimulation(ConnectionSignalState.GREEN) }
                    )
                    SimulatorPill(
                        label = "Amber",
                        selected = currentState == ConnectionSignalState.AMBER,
                        color = Color(0xFFF59E0B),
                        testTag = "btn_sim_amber",
                        onClick = { onSelectSimulation(ConnectionSignalState.AMBER) }
                    )
                    SimulatorPill(
                        label = "Offline",
                        selected = currentState == ConnectionSignalState.DISCONNECTED,
                        color = Color(0xFFEF4444),
                        testTag = "btn_sim_disconnected",
                        onClick = { onSelectSimulation(ConnectionSignalState.DISCONNECTED) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onSelectSimulation(null) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.surfaceVariant,
                        contentColor = scheme.onSurface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_sim_auto")
                ) {
                    Text("Use live status", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SimulatorPill(
    label: String,
    selected: Boolean,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) color.copy(alpha = 0.16f) else Color.Transparent,
            contentColor = color
        ),
        border = BorderStroke(1.dp, color),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .widthIn(min = 88.dp)
            .testTag(testTag)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ConnectionMetricRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            modifier = Modifier.padding(start = 24.dp, top = 2.dp)
        )
    }
}
