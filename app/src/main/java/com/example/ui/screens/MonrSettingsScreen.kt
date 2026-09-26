package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.radio.RadioStatus
import com.example.core.radio.TransportTier
import com.example.ui.components.MonrFavicon
import com.example.ui.components.MonrGlassCard
import com.example.ui.components.MonrOutlinedPillButton
import com.example.ui.components.MonrWordmark
import com.example.ui.components.rememberMonrCanvasBrush
import com.example.ui.theme.MonrAmberWarning
import com.example.ui.theme.MonrBgDark
import com.example.ui.theme.MonrBorderDark
import com.example.ui.theme.MonrCyanAccent
import com.example.ui.theme.MonrCyanGlow
import com.example.ui.theme.MonrCyanLight
import com.example.ui.theme.MonrGlassCardBg
import com.example.ui.theme.MonrGlassCardBorder
import com.example.ui.theme.MonrPrimaryContainer
import com.example.ui.theme.MonrSignalGreen
import com.example.ui.theme.MonrSurfaceDark
import com.example.ui.theme.MonrSurfaceVariantDark
import com.example.ui.theme.MonrTextPrimary
import com.example.ui.theme.MonrTextSecondary
import com.example.ui.theme.MonrTextTertiary

@Composable
fun MonrSettingsScreen(
    nodeFingerprint: String,
    radioStatus: RadioStatus,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    onRunMaintenance: () -> Unit,
    onSelectTier: (TransportTier) -> Unit,
    onOpenAdvancedTab: (tabIndex: Int) -> Unit
) {
    var stealthModeEnabled by remember { mutableStateOf(false) }
    var backgroundMeshRunning by remember { mutableStateOf(true) }
    var batterySaverMesh by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(rememberMonrCanvasBrush())
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Monr Brand & Website Hero Card ---
        MonrGlassCard(modifier = Modifier.testTag("monr_brand_card")) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MonrFavicon(size = 38.dp, isDarkTheme = isDarkTheme)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            MonrWordmark(fontSize = 24.sp, isDarkTheme = isDarkTheme)
                            Text(
                                text = "monr.io",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MonrCyanAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        color = Color(0x3522D3EE),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MonrCyanAccent)
                    ) {
                        Text(
                            text = "Private",
                            style = MaterialTheme.typography.labelSmall,
                            color = MonrCyanLight,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Talk nearby without the internet. Messages stay on phones until they can be delivered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- 1. Profile & Cryptographic Identity ---
        SettingsSectionHeader("Your ID")

        MonrGlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MonrCyanAccent, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Your device ID",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "A unique ID stored securely on this phone.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = nodeFingerprint,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MonrCyanLight,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // --- 2. Network & Radio Settings ---
        SettingsSectionHeader("Connection")

        MonrGlassCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsSwitchRow(
                    icon = Icons.Default.Sensors,
                    title = "Stay available in the background",
                    subtitle = "Keep looking for nearby phones when the screen is off.",
                    checked = backgroundMeshRunning,
                    onCheckedChange = { backgroundMeshRunning = it }
                )

                SettingsSwitchRow(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "Save battery while searching",
                    subtitle = "Search less often when the battery is low.",
                    checked = batterySaverMesh,
                    onCheckedChange = { batterySaverMesh = it }
                )

                SettingsActionRow(
                    title = "How you're connecting",
                    subtitle = "Now: ${radioStatus.activeTier.displayName}",
                    actionLabel = "Change",
                    onClick = { onOpenAdvancedTab(3) }
                )
            }
        }

        SettingsSectionHeader("Privacy")

        MonrGlassCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsSwitchRow(
                    icon = Icons.Default.Shield,
                    title = "Hidden mode",
                    subtitle = "Don't advertise this phone; only answer people you already know.",
                    checked = stealthModeEnabled,
                    onCheckedChange = { stealthModeEnabled = it }
                )

                SettingsActionRow(
                    title = "Block spam",
                    subtitle = "See who you trust and who is blocked.",
                    actionLabel = "Trust list",
                    onClick = { onOpenAdvancedTab(6) }
                )

                SettingsActionRow(
                    title = "Clear old offline data",
                    subtitle = "Free space by removing leftover stored messages.",
                    actionLabel = "Clear",
                    onClick = onRunMaintenance,
                    actionTestTag = "btn_settings_purge"
                )
            }
        }

        SettingsSectionHeader("Display")

        MonrGlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsSwitchRow(
                    icon = Icons.Default.DarkMode,
                    title = "Dark mode",
                    subtitle = "Easier at night and uses less power.",
                    checked = isDarkTheme,
                    onCheckedChange = { onToggleDarkTheme() }
                )
            }
        }

        SettingsSectionHeader("More tools")

        MonrGlassCard {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModuleQuickItem("Voice & SOS", "Talk nearby or send an emergency alert", 0, onOpenAdvancedTab)
                ModuleQuickItem("Map", "See places and people around you", 1, onOpenAdvancedTab)
                ModuleQuickItem("File sharing", "Send photos and files over the mesh", 2, onOpenAdvancedTab)
                ModuleQuickItem("Connection modes", "Internet, hotspot, or nearby phones", 3, onOpenAdvancedTab)
                ModuleQuickItem("Offline queue", "Messages waiting to be delivered", 4, onOpenAdvancedTab)
                ModuleQuickItem("Encryption", "How chats are locked on this phone", 5, onOpenAdvancedTab)
                ModuleQuickItem("Trust list", "People you trust and people you blocked", 6, onOpenAdvancedTab)
                ModuleQuickItem("Radios & permissions", "Bluetooth, location, and notifications", 7, onOpenAdvancedTab)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MonrCyanAccent,
        letterSpacing = 1.sp
    )
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MonrCyanAccent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MonrBgDark,
                checkedTrackColor = MonrCyanAccent,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit,
    actionTestTag: String? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            MonrOutlinedPillButton(
                text = actionLabel,
                onClick = onClick,
                compact = true,
                modifier = if (actionTestTag != null) Modifier.testTag(actionTestTag) else Modifier
            )
        }
    }
}

@Composable
fun ModuleQuickItem(
    title: String,
    description: String,
    targetIndex: Int,
    onNavigate: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate(targetIndex) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("→", color = MonrCyanAccent, fontWeight = FontWeight.Bold)
        }
    }
}
