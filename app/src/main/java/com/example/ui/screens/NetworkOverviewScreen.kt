package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.dtn.model.ChatMessageEntity
import com.example.core.dtn.model.MeshPeerEntity
import com.example.core.radio.RadioStatus
import com.example.core.radio.TransportTier
import com.example.ui.components.ConnectionSignalState
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.components.MonrCyanPillButton
import com.example.ui.components.rememberMonrCanvasBrush
import com.example.ui.components.MonrGlassCard
import com.example.ui.components.MonrInitialsAvatar
import com.example.ui.components.MonrOutlinedPillButton
import com.example.ui.components.MonrWordmark
import com.example.ui.components.formatRelativeMinutes
import com.example.ui.components.latestMessageByPeer
import com.example.ui.components.peerAccentColor
import com.example.ui.components.peerActivitySubtitle
import com.example.ui.components.peerInitials
import com.example.ui.theme.MonrAmberLight
import com.example.ui.theme.MonrAmberWarning
import com.example.ui.theme.MonrBorderDark
import com.example.ui.theme.MonrCyanAccent
import com.example.ui.theme.MonrCyanLight
import com.example.ui.theme.MonrGlassCardBg
import com.example.ui.theme.MonrGlassCardBorder
import com.example.ui.theme.MonrSignalGreen
import com.example.ui.theme.MonrTextSecondary
import com.example.ui.theme.MonrTextTertiary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Node display representation for the constellation graph
 */
data class GraphNode(
    val id: String,
    val name: String,
    val isLocal: Boolean = false,
    val isDirect: Boolean = true,
    val hops: Int = 1,
    val rssiDbm: Int = -55,
    val linkType: String = "Wi-Fi Direct",
    val accentColor: Color = MonrCyanAccent
)

/**
 * Monr Signature Screen: Network Overview
 * Designed precisely according to the Monr Off-Grid Communication UI mockups:
 * - Top brand bar: "Monr" with "Local Network | No Internet" or "Hotspot Active" pill
 * - Centerpiece: Interconnected mesh constellation with "This Device" / "You" at center,
 *   satellite peer nodes (Alex, Jordan, Rin, Kai, Luma), orbital guide rings, and animated packet dots
 * - Quick Encounter cards: "Alex - Meet at the gate", "Jordan - Relay active"
 * - Action pill buttons: "Create Room" (cyan glowing pill) & "Join Network" (stroked pill)
 */
@Composable
fun NetworkOverviewScreen(
    localNodeId: String,
    status: RadioStatus,
    peers: List<MeshPeerEntity>,
    pendingBundlesCount: Int,
    cryptoSessionsCount: Int,
    onNavigateToChat: (peerId: String, peerAlias: String) -> Unit,
    onNavigateToPeers: () -> Unit,
    onStartMesh: () -> Unit,
    onSwitchTier: (TransportTier) -> Unit,
    meshError: String? = null,
    chatMessages: List<ChatMessageEntity> = emptyList(),
    isHotspotRunning: Boolean = false,
    isDarkTheme: Boolean = true,
    overrideSignalState: ConnectionSignalState? = null,
    onOverrideSignalChange: ((ConnectionSignalState?) -> Unit)? = null
) {
    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    var showCreateRoomDialog by remember { mutableStateOf(false) }
    var showJoinNetworkDialog by remember { mutableStateOf(false) }
    var roomNameInput by remember { mutableStateOf("Monr-Emergency-Mesh") }

    // Pulse animation for central glowing node & particle animation along mesh edges
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAnim by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAnim"
    )
    val particleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleProgress"
    )

    // Build the constellation node list (using real peers or mock default constellation)
    val graphPeers = remember(peers) {
        peers.map { p ->
            GraphNode(
                id = p.nodeId,
                name = p.alias,
                isLocal = false,
                isDirect = p.isDirectNeighbor,
                hops = p.distanceHops,
                rssiDbm = p.rssiDbm,
                linkType = p.directLinkType,
                accentColor = peerAccentColor(p.isDirectNeighbor)
            )
        }
    }
    val latestByPeer = remember(chatMessages) { latestMessageByPeer(chatMessages) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(rememberMonrCanvasBrush())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // --- Top Header: Monr Logo + Network Status Pill (Image 2 & 3) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonrWordmark(fontSize = 28.sp, isDarkTheme = isDarkTheme)

            // Persistent Connection Status Badge: "Local Network | No Internet" / Signal Indicator
            ConnectionStatusBadge(
                status = status,
                peerCount = peers.size,
                isHotspotRunning = isHotspotRunning,
                overrideState = overrideSignalState,
                onStateOverrideChange = onOverrideSignalChange
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // --- Centerpiece: Interactive Mesh Constellation Canvas (Images 2 & 3) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .testTag("signature_node_graph")
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = constraints.maxWidth.toFloat()
                val canvasHeight = constraints.maxHeight.toFloat()
                val centerX = canvasWidth / 2f
                val centerY = canvasHeight / 2f
                val minDim = minOf(canvasWidth, canvasHeight)
                val orbitRadius = minDim * 0.36f

                // Compute peer node coordinates around the center in a regular constellation
                val nodePositions = remember(graphPeers, canvasWidth, canvasHeight) {
                    val count = graphPeers.size
                    graphPeers.mapIndexed { index, peer ->
                        val angle = (2 * Math.PI * index / count) - (Math.PI / 2)
                        val px = centerX + (orbitRadius * cos(angle)).toFloat()
                        val py = centerY + (orbitRadius * sin(angle)).toFloat()
                        Pair(peer, Offset(px, py))
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(nodePositions) {
                            detectTapGestures { tapOffset ->
                                val hit = nodePositions.firstOrNull { (_, pos) ->
                                    (tapOffset - pos).getDistance() < 70f
                                }
                                selectedNode = hit?.first
                            }
                        }
                ) {
                    // 1. Subtle radial background mesh glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x2222D3EE), Color.Transparent),
                            center = Offset(centerX, centerY),
                            radius = orbitRadius * 1.3f
                        ),
                        radius = orbitRadius * 1.3f,
                        center = Offset(centerX, centerY)
                    )

                    // 2. Dashed Orbital Guide Circle
                    drawCircle(
                        color = Color(0x2422D3EE),
                        radius = orbitRadius,
                        center = Offset(centerX, centerY),
                        style = Stroke(
                            width = 1.2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f)
                        )
                    )

                    // 3. Draw inter-peer mesh lines (creating the multi-hop mesh polygon shown in Image 2 & 3)
                    val n = nodePositions.size
                    if (n > 1) {
                        for (i in 0 until n) {
                            val currentPos = nodePositions[i].second
                            val nextPos = nodePositions[(i + 1) % n].second
                            drawLine(
                                color = Color(0x3564748B),
                                start = currentPos,
                                end = nextPos,
                                strokeWidth = 1.2f
                            )
                        }
                    }

                    // 4. Draw Radial lines from Center ("This Device") to Each Peer Node
                    nodePositions.forEach { (peer, peerPos) ->
                        val isSelected = selectedNode?.id == peer.id
                        val lineColor = if (isSelected) MonrCyanAccent else if (peer.isDirect) Color(0x6022D3EE) else Color(0x55F59E0B)

                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(MonrCyanAccent.copy(alpha = 0.8f), lineColor),
                                start = Offset(centerX, centerY),
                                end = peerPos
                            ),
                            start = Offset(centerX, centerY),
                            end = peerPos,
                            strokeWidth = if (isSelected) 2.6f else 1.6f
                        )

                        // 5. Animated Data Packet Dots travelling along the lines
                        val currentProgress = (particleProgress + (peer.name.hashCode() % 100) / 100f) % 1f
                        val packetX = centerX + (peerPos.x - centerX) * currentProgress
                        val packetY = centerY + (peerPos.y - centerY) * currentProgress
                        val packetColor = if (peer.isDirect) MonrCyanLight else MonrAmberLight

                        drawCircle(
                            color = packetColor,
                            radius = 3.5f,
                            center = Offset(packetX, packetY)
                        )
                        // Glow around packet
                        drawCircle(
                            color = packetColor.copy(alpha = 0.35f),
                            radius = 7f,
                            center = Offset(packetX, packetY)
                        )
                    }

                    // 6. Center Node: Radiant glow halo + "This Device" (Image 2) or "You" (Image 3)
                    drawCircle(
                        color = Color(0x3322D3EE),
                        radius = 62f * pulseAnim,
                        center = Offset(centerX, centerY)
                    )
                    drawCircle(
                        color = Color(0xFF0F1E30),
                        radius = 42f,
                        center = Offset(centerX, centerY)
                    )
                    drawCircle(
                        color = MonrCyanAccent,
                        radius = 42f,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2.8f)
                    )
                    drawCircle(
                        color = MonrCyanAccent,
                        radius = 9f,
                        center = Offset(centerX, centerY)
                    )
                }

                // Center Node Label Overlay ("You")
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "You",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                // Overlay Peer Nodes as Interactive Circles with Avatar Silhouette & Name
                val densityVal = LocalDensity.current.density
                nodePositions.forEach { (peer, pos) ->
                    val isSelected = selectedNode?.id == peer.id
                    val nodeBorderColor = if (isSelected) MonrCyanAccent else peer.accentColor

                    Box(
                        modifier = Modifier
                            .offset(
                                x = (pos.x / densityVal - 36f).dp,
                                y = (pos.y / densityVal - 36f).dp
                            )
                            .size(72.dp)
                            .clickable { selectedNode = peer },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0E1A2B),
                            border = androidx.compose.foundation.BorderStroke(2.dp, nodeBorderColor),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = peerInitials(peer.name).ifBlank { peer.name.take(1).uppercase() },
                                        color = nodeBorderColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = peer.name.take(8),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }

                        if (!peer.isDirect) {
                            Surface(
                                color = Color(0xFF0F1A28),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MonrAmberWarning),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = (-8).dp)
                            ) {
                                Text(
                                    text = "1 hop",
                                    color = MonrAmberLight,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Top right simulation badge / add peer
                IconButton(
                    onClick = onStartMesh,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MonrGlassCardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MonrGlassCardBorder),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Sensors, contentDescription = "Start mesh", tint = MonrCyanAccent, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (graphPeers.isEmpty()) {
                    Text(
                        text = if (status.isServiceRunning) {
                            "No one nearby yet. Keep Bluetooth on."
                        } else {
                            "No one nearby yet. Start the mesh and keep Bluetooth on."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .padding(horizontal = 24.dp)
                    )
                }
            }
        }

        // --- Recent Encounters ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (meshError != null) {
                Text(
                    text = meshError,
                    color = MonrAmberLight,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (peers.isEmpty()) {
                MonrGlassCard {
                    Text(
                        text = "Looking for other Monr phones. Create or join a room to start.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            } else {
                peers.take(2).forEach { peer ->
                    val lastMessage = latestByPeer[peer.nodeId]
                    MonrGlassCard(
                        onClick = { onNavigateToChat(peer.nodeId, peer.alias) },
                        modifier = Modifier.testTag("recent_chat_${peer.nodeId}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MonrInitialsAvatar(
                                    name = peer.alias,
                                    accent = peerAccentColor(peer.isDirectNeighbor)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = peer.alias,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = peerActivitySubtitle(peer, lastMessage),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = lastMessage?.let { formatRelativeMinutes(it.createdAtEpochMs) }
                                    ?: if (peer.isDirectNeighbor) "Direct" else "Relay",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Bottom Action Pill Buttons: "Create Room" & "Join Network" (From Image 3) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MonrCyanPillButton(
                text = "Create Room",
                onClick = { showCreateRoomDialog = true },
                glowing = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_create_room")
            )

            MonrOutlinedPillButton(
                text = "Join Network",
                onClick = { showJoinNetworkDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_join_network")
            )
        }
    }

    // --- Node Tap Inspector Dialog ---
    if (selectedNode != null) {
        val node = selectedNode!!
        AlertDialog(
            onDismissRequest = { selectedNode = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1E324A),
                        border = androidx.compose.foundation.BorderStroke(2.dp, node.accentColor),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = node.accentColor, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(node.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Text(
                            if (node.isDirect) "Direct Neighbor (Zero Infrastructure)" else "Multi-Hop Relayed Peer",
                            color = MonrCyanLight,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Transport Link:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text(node.linkType, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Signal Strength:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text("${node.rssiDbm} dBm", color = if (node.rssiDbm > -65) MonrSignalGreen else MonrAmberLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Hop Distance:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text("${node.hops} Hop(s)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val n = node
                        selectedNode = null
                        onNavigateToChat(n.id, n.name)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MonrCyanAccent),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Open Chat", color = Color(0xFF07121E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedNode = null }) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // --- Create Room Dialog ---
    if (showCreateRoomDialog) {
        AlertDialog(
            onDismissRequest = { showCreateRoomDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text("Create Off-Grid Mesh Room", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Broadcast a secure peer-to-peer room via Wi-Fi Direct & BLE without cellular or internet connectivity.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = roomNameInput,
                        onValueChange = { roomNameInput = it },
                        label = { Text("Room Name", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MonrCyanAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateRoomDialog = false
                        onStartMesh()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MonrCyanAccent),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Start Broadcasting", color = Color(0xFF07121E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateRoomDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // --- Join Network Dialog ---
    if (showJoinNetworkDialog) {
        AlertDialog(
            onDismissRequest = { showJoinNetworkDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text("Scan Local Mesh Radios", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Scanning for local BLE advertisements from nearby Monr nodes...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.WifiTethering, contentDescription = null, tint = MonrCyanAccent)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Nearby mesh radios", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("BLE GATT • identity handshake", color = MonrSignalGreen, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showJoinNetworkDialog = false
                        onStartMesh()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MonrCyanAccent),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Connect", color = Color(0xFF07121E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinNetworkDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}
