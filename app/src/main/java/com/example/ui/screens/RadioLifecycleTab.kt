package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.radio.RadioCapabilities
import com.example.core.radio.RadioHealth
import com.example.core.radio.RadioStatus
import com.example.core.radio.TransportTier

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioLifecycleTab(
    status: RadioStatus,
    capabilities: RadioCapabilities,
    missingPermissions: List<String>,
    meshError: String? = null,
    onRequestPermissions: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onSelectTier: (TransportTier) -> Unit,
    onToggleVoip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Missing Permissions Banner
        if (missingPermissions.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth().testTag("permissions_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Permissions Required",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${missingPermissions.size} Permissions Missing",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Nearby Wi-Fi, BLE, and Notification permissions are required for autonomous delay-tolerant routing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier.testTag("grant_permissions_button")
                    ) {
                        Text("Grant Radio Permissions")
                    }
                }
            }
        }

        if (meshError != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth().testTag("mesh_error_card")
            ) {
                Text(
                    text = meshError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Foreground Service Controller Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier.fillMaxWidth().testTag("service_control_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Mesh Foreground Service",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (status.isServiceRunning) "Running (connectedDevice | dataSync)" else "Stopped",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.isServiceRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (status.isServiceRunning) Color(0xFF4CAF50) else Color.Gray)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!status.isServiceRunning) {
                        Button(
                            onClick = onStartService,
                            modifier = Modifier.testTag("start_service_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Service")
                        }
                    } else {
                        Button(
                            onClick = onStopService,
                            modifier = Modifier.testTag("stop_service_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Service")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Active WakeLocks & Network Locks
                Text(
                    text = "System Execution & Radio Locks:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LockBadge("WakeLock", status.wakeLockHeld)
                    LockBadge("Wi-Fi High-Perf Lock", status.wifiLockHeld)
                    LockBadge("Mic Service Type", status.isVoipActive)
                    LockBadge("WAN Backhaul", status.isInternetReachable)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // VoIP Microphone Toggle Button
                OutlinedButton(
                    onClick = onToggleVoip,
                    modifier = Modifier.fillMaxWidth().testTag("btn_toggle_voip")
                ) {
                    Icon(
                        if (status.isVoipActive) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (status.isVoipActive) "End VoIP Call (Release Mic)" else "Simulate VoIP Call (Escalate Service Type)")
                }
            }
        }

        // Transport Tier Selector & Multi-Radio Link Status
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Transport Tier & Autonomous Link Promotion",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tier 3 Dual-Radio: BLE for discovery + Wi-Fi Aware/Direct for >1KB bulk sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransportTier.entries.forEach { tier ->
                        FilterChip(
                            selected = status.activeTier == tier,
                            onClick = { onSelectTier(tier) },
                            label = { Text(tier.name) }
                        )
                    }
                }
            }
        }

        // Hardware Diagnostics List
        Text(
            text = "Physical Wireless Subsystems",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        RadioStatusRow(
            title = "Bluetooth Low Energy (BLE)",
            subtitle = "GATT & Low-power Beacon Discovery",
            health = status.bluetoothHealth,
            icon = Icons.Default.Bluetooth
        )
        RadioStatusRow(
            title = "Wi-Fi Aware (NAN)",
            subtitle = "Zero-AP Peer-to-Peer Cluster",
            health = status.wifiAwareHealth,
            icon = Icons.Default.WifiTethering
        )
        RadioStatusRow(
            title = "Wi-Fi Direct / Local AP",
            subtitle = "High-bandwidth batch transport",
            health = status.wifiHealth,
            icon = Icons.Default.Wifi
        )
        RadioStatusRow(
            title = "Location Subsystem",
            subtitle = "Required for radio discovery on older APIs",
            health = status.locationHealth,
            icon = Icons.Default.NearMe
        )

        // Hardware Security & Driver Profile
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hardware Security & Driver Profile",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                CapabilityItem("Android StrongBox Keystore", capabilities.hasHardwareStrongBox)
                CapabilityItem("BLE Advertising Driver", capabilities.hasBleAdvertising)
                CapabilityItem("BLE Extended Advertising (>31 bytes)", capabilities.hasBleExtendedAdvertising)
                CapabilityItem("Wi-Fi Aware Chipset Support", capabilities.hasWifiAware)
                CapabilityItem("Wi-Fi Direct (P2P) Driver", capabilities.hasWifiDirect)
                CapabilityItem("Local-Only Hotspot API", capabilities.hasLocalOnlyHotspot)

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Preferred Direct Link: ${capabilities.preferredDirectLink.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun LockBadge(label: String, held: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (held) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (held) Color(0xFF2E7D32) else Color.Gray)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (held) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RadioStatusRow(
    title: String,
    subtitle: String,
    health: RadioHealth,
    icon: ImageVector
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HealthBadge(health)
        }
    }
}

@Composable
fun HealthBadge(health: RadioHealth) {
    val (bgColor, textColor, text) = when (health) {
        RadioHealth.ACTIVE -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "ACTIVE")
        RadioHealth.DISABLED -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "DISABLED")
        RadioHealth.PERMISSION_DENIED -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "PERM REQ")
        RadioHealth.HARDWARE_UNSUPPORTED -> Triple(Color(0xFFEEEEEE), Color(0xFF616161), "NO CHIPSET")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun CapabilityItem(label: String, supported: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (supported) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Supported",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text(
                text = "Unsupported",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
