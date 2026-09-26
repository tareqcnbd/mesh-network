package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.dtn.model.MeshPeerEntity
import com.example.core.radio.RadioStatus
import com.example.ui.components.ConnectionSignalState
import com.example.ui.components.MonrCyanPillButton
import com.example.ui.components.rememberMonrCanvasBrush
import com.example.ui.components.MonrGlassCard
import com.example.ui.components.MonrInitialsAvatar
import com.example.ui.components.peerAccentColor
import com.example.ui.theme.MonrAmberLight
import com.example.ui.theme.MonrAmberWarning
import com.example.ui.theme.MonrBgDark
import com.example.ui.theme.MonrBorderDark
import com.example.ui.theme.MonrCyanAccent
import com.example.ui.theme.MonrCyanLight
import com.example.ui.theme.MonrGlassCardBg
import com.example.ui.theme.MonrGlassCardBorder
import com.example.ui.theme.MonrGlassSearchBg
import com.example.ui.theme.MonrGlassSearchBorder
import com.example.ui.theme.MonrSignalGreen
import com.example.ui.theme.MonrSurfaceDark
import com.example.ui.theme.MonrTextPrimary
import com.example.ui.theme.MonrTextSecondary
import com.example.ui.theme.MonrTextTertiary

/**
 * Model representation of a Nearby Peer tailored to the Monr "Peers Nearby" mockup
 */
data class MonrNearbyPeer(
    val id: String,
    val name: String,
    val isDirect: Boolean,
    val signalFraction: Float, // 0.0f to 1.0f
    val distanceEstimate: String, // e.g. "~8m", "~14m", "~22m"
    val linkTag: String, // "Direct", "Relayed", "Weak"
    val accentColor: Color = MonrCyanAccent,
    val hasChatAction: Boolean = true
)

/**
 * Monr "Peers Nearby" Screen matching exact Image 1 design:
 * - Top back arrow + bold title "Peers Nearby"
 * - Rounded search bar capsule: "Scan local network"
 * - Frosted glass cards for Alex, Jordan, Rin, etc.
 *   - Avatar + Name + "Chat" stroked pill / Wi-Fi icon
 *   - Custom horizontal signal distance slider bar
 *   - Distance indicators below bar: "Direct", "~8m", "~14m", "~22m"
 * - Bottom full-width glowing cyan pill button: "Peers" / "Scan Network"
 */
@Composable
fun MonrPeersScreen(
    peers: List<MeshPeerEntity>,
    onStartMesh: () -> Unit,
    onStartChatWithPeer: (peerId: String, peerAlias: String) -> Unit,
    onBackClick: (() -> Unit)? = null,
    radioStatus: RadioStatus? = null,
    overrideSignalState: ConnectionSignalState? = null,
    onOverrideSignalChange: ((ConnectionSignalState?) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }

    // Prepare list matching the mockup (Alex, Jordan, Rin, and real peers)
    val displayPeers = remember(peers, searchQuery) {
        val baseList = peers.map { p ->
            val signalRatio = ((p.rssiDbm + 90).coerceIn(10, 80) / 80f)
            MonrNearbyPeer(
                id = p.nodeId,
                name = p.alias,
                isDirect = p.isDirectNeighbor,
                signalFraction = signalRatio,
                distanceEstimate = if (p.rssiDbm > -60) "~8m" else if (p.rssiDbm > -75) "~14m" else "~22m",
                linkTag = if (p.isDirectNeighbor) "Direct" else "Relayed",
                accentColor = peerAccentColor(p.isDirectNeighbor),
                hasChatAction = true
            )
        }

        if (searchQuery.isBlank()) baseList
        else baseList.filter { it.name.contains(searchQuery, ignoreCase = true) || it.id.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(rememberMonrCanvasBrush())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // --- Top Bar: Back Arrow & Title "Peers Nearby" + ConnectionStatusBadge (Image 1) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "Peers Nearby",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- Search Input Pill: "Scan local network" (Image 1) ---
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Scan local network",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp
                        )
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Peer Cards List matching Image 1 ---
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            if (displayPeers.isEmpty()) {
                item {
                    MonrGlassCard {
                        Text(
                            text = "Looking for other Monr phones. Create or join a room to start.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                        )
                    }
                }
            }
            items(displayPeers, key = { it.id }) { peer ->
                MonrGlassCard(
                    onClick = { onStartChatWithPeer(peer.id, peer.name) },
                    modifier = Modifier.testTag("peer_card_${peer.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // Top row: Avatar + Name + Action (Wi-Fi icon OR "Chat" pill)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MonrInitialsAvatar(
                                    name = peer.name,
                                    accent = peer.accentColor
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = peer.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 18.sp
                                )
                            }

                            // Right action item: "Chat" pill button or Wi-Fi icon (matching Image 1)
                            if (peer.hasChatAction) {
                                OutlinedButton(
                                    onClick = { onStartChatWithPeer(peer.id, peer.name) },
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.Transparent
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = "Chat",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Wifi,
                                            contentDescription = "Wi-Fi Signal",
                                            tint = MonrCyanAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Custom Horizontal Signal & Distance Progress Bar (Image 1)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF132236))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(peer.signalFraction)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                peer.accentColor.copy(alpha = 0.7f),
                                                peer.accentColor
                                            )
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Distance tags line below progress bar: [pin] Direct   ~8m   ~14m   ~22m (Image 1)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = peer.linkTag,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Text(
                                text = "~8m",
                                color = if (peer.distanceEstimate == "~8m") MonrCyanLight else MonrTextTertiary,
                                fontSize = 12.sp,
                                fontWeight = if (peer.distanceEstimate == "~8m") FontWeight.Bold else FontWeight.Normal
                            )

                            Text(
                                text = "~14m",
                                color = if (peer.distanceEstimate == "~14m") MonrCyanLight else MonrTextTertiary,
                                fontSize = 12.sp,
                                fontWeight = if (peer.distanceEstimate == "~14m") FontWeight.Bold else FontWeight.Normal
                            )

                            Text(
                                text = "~22m",
                                color = if (peer.distanceEstimate == "~22m") MonrAmberLight else MonrTextTertiary,
                                fontSize = 12.sp,
                                fontWeight = if (peer.distanceEstimate == "~22m") FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // --- Bottom Glowing Cyan Pill Button: "Peers" / "Encounter New Peer" (Image 1) ---
        if (radioStatus?.isServiceRunning != true) {
            MonrCyanPillButton(
                text = "Start Mesh",
                onClick = onStartMesh,
                glowing = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_peers_action")
            )
        }
    }
}
