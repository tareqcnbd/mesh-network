package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.crypto.ratchet.RatchetSessionState
import com.example.core.dtn.model.ChatMessageEntity
import com.example.core.dtn.model.MeshPeerEntity
import com.example.core.radio.RadioStatus
import com.example.ui.components.ConnectionSignalState
import com.example.ui.components.MonrGlassCard
import com.example.ui.components.MonrInitialsAvatar
import com.example.ui.components.rememberMonrCanvasBrush
import com.example.ui.components.formatRelativeMinutes
import com.example.ui.components.latestMessageByPeer
import com.example.ui.components.peerAccentColor
import com.example.ui.components.peerLinkCaption
import com.example.ui.theme.MonrBgDark
import com.example.ui.theme.MonrBorderDark
import com.example.ui.theme.MonrBubbleIncoming
import com.example.ui.theme.MonrBubbleIncomingText
import com.example.ui.theme.MonrBubbleOutgoing
import com.example.ui.theme.MonrBubbleOutgoingText
import com.example.ui.theme.MonrCyanAccent
import com.example.ui.theme.MonrCyanLight
import com.example.ui.theme.MonrGlassCardBg
import com.example.ui.theme.MonrGlassCardBorder
import com.example.ui.theme.MonrGlassSearchBg
import com.example.ui.theme.MonrGlassSearchBorder
import com.example.ui.theme.MonrSignalGreen
import com.example.ui.theme.MonrSurfaceDark
import com.example.ui.theme.MonrSurfaceVariantDark
import com.example.ui.theme.MonrTextPrimary
import com.example.ui.theme.MonrTextSecondary
import com.example.ui.theme.MonrTextTertiary

data class MonrUIMessage(
    val id: String,
    val isMe: Boolean,
    val text: String,
    val timestamp: String = "12:04",
    val statusCaption: String = "Sent via Direct"
)

/**
 * Monr Chat Screen matching exact Image 4 design:
 * - Clean top bar with Avatar + "Alex" + More menu (•••)
 * - Subheader: "Direct • Strong signal" with two glowing cyan dots (••)
 * - Message Bubbles:
 *   - Incoming (Alex): Deep slate blue bubble (#24364D), crisp white text, "Sent via Direct" caption
 *   - Outgoing (Me): Electric cyan pill bubble (#22D3EE), bold dark text, timestamp "12:04" below
 * - Input bar: Envelope/Attachment button, rounded pill text field "Message", cyan send button
 */
@Composable
fun MonrConversationList(
    peers: List<MeshPeerEntity>,
    chatMessages: List<ChatMessageEntity>,
    onSelectConversation: (peerId: String, peerAlias: String) -> Unit
) {
    val previews = remember(chatMessages, peers) {
        latestMessageByPeer(chatMessages)
            .values
            .sortedByDescending { it.createdAtEpochMs }
    }
    val peerById = remember(peers) { peers.associateBy { it.nodeId } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(rememberMonrCanvasBrush())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Chats",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            if (previews.isEmpty()) {
                item {
                    MonrGlassCard {
                        Text(
                            text = "No chats yet. When someone is nearby, you can message them here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
                        )
                    }
                }
            }
            items(previews, key = { it.messageId }) { message ->
                val peer = peerById[message.peerNodeId]
                MonrGlassCard(
                    onClick = { onSelectConversation(message.peerNodeId, message.peerAlias) },
                    modifier = Modifier.testTag("conversation_${message.peerNodeId}")
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
                                name = message.peerAlias,
                                accent = peerAccentColor(peer?.isDirectNeighbor != false)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = message.peerAlias,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = message.plaintext,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        Text(
                            text = formatRelativeMinutes(message.createdAtEpochMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MonrChatScreen(
    nodeFingerprint: String,
    activeSessions: List<RatchetSessionState>,
    peers: List<MeshPeerEntity>,
    chatMessages: List<ChatMessageEntity>,
    selectedPeerId: String? = null,
    selectedPeerAlias: String? = null,
    onSendMessage: (peerNodeId: String, plaintext: String) -> Unit,
    onBackClick: (() -> Unit)? = null,
    radioStatus: RadioStatus? = null,
    overrideSignalState: ConnectionSignalState? = null,
    onOverrideSignalChange: ((ConnectionSignalState?) -> Unit)? = null
) {
    var selectedPeerIdState by remember(selectedPeerId, peers) {
        mutableStateOf(selectedPeerId ?: peers.firstOrNull()?.nodeId ?: "*")
    }
    var selectedPeerAliasState by remember(selectedPeerAlias, peers) {
        mutableStateOf(selectedPeerAlias ?: peers.firstOrNull()?.alias ?: "Mesh")
    }
    var inputText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    val conversation = remember(chatMessages, selectedPeerIdState) {
        chatMessages
            .filter { it.peerNodeId == selectedPeerIdState }
            .sortedBy { it.createdAtEpochMs }
            .map { entity ->
                MonrUIMessage(
                    id = entity.messageId,
                    isMe = entity.isOutgoing,
                    text = entity.plaintext,
                    timestamp = formatChatTime(entity.createdAtEpochMs),
                    statusCaption = entity.deliveryCaption
                )
            }
    }
    val selectedPeer = remember(peers, selectedPeerIdState) {
        peers.firstOrNull { it.nodeId == selectedPeerIdState }
    }
    val linkCaption = peerLinkCaption(selectedPeer)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(rememberMonrCanvasBrush())
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // --- Top Bar: Avatar + Peer Name ("Alex") + ConnectionStatusBadge + Three Dots Menu (Image 4) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                MonrInitialsAvatar(
                    name = selectedPeerAliasState,
                    accent = peerAccentColor(selectedPeer?.isDirectNeighbor != false),
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = selectedPeerAliasState,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        peers.forEach { peer ->
                            DropdownMenuItem(
                                text = { Text(peer.alias, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    selectedPeerIdState = peer.nodeId
                                    selectedPeerAliasState = peer.alias
                                    showMenu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("All mesh nodes", color = MonrCyanLight) },
                            onClick = {
                                selectedPeerIdState = "*"
                                selectedPeerAliasState = "Mesh"
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("E2EE Ratchet Keys (Secp256r1)", color = MonrCyanLight) },
                            onClick = { showMenu = false }
                        )
                    }
                }
            }
        }

        // --- Sub-Header Strip: "Direct • Strong signal ••" (Image 4) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = linkCaption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            // Two glowing cyan signal dots
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MonrCyanAccent)
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MonrCyanAccent)
                )
            }
        }

        // --- Message Feed matching Image 4 ---
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (conversation.isEmpty()) {
                item {
                    Text(
                        text = if (peers.isEmpty()) {
                            "No chats yet. When someone is nearby, you can message them here."
                        } else {
                            "No messages yet."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
            items(conversation, key = { it.id }) { msg ->
                if (msg.isMe) {
                    // Outgoing Message (Sent by Me): Electric Cyan Pill
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MonrBubbleOutgoing,
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .testTag("outgoing_bubble_${msg.id}")
                        ) {
                            Text(
                                text = msg.text,
                                color = MonrBubbleOutgoingText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = msg.timestamp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                } else {
                    // Incoming Message (Sent by Alex): Deep Slate Blue Bubble
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MonrBubbleIncoming,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3558779C)),
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .testTag("incoming_bubble_${msg.id}")
                        ) {
                            Text(
                                text = msg.text,
                                color = MonrBubbleIncomingText,
                                fontWeight = FontWeight.Normal,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = msg.statusCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }

        // --- Bottom Input Bar (Image 4) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Attachment/Envelope icon
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier
                    .size(46.dp)
                    .clickable { /* attachments not wired for mesh v1 */ }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Mail,
                        contentDescription = "Attach / Mail",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Message text input pill
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Message",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp
                            )
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
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
                                .testTag("chat_input_field")
                        )
                    }
                }
            }

            // Paper plane send button in electric cyan
            Surface(
                shape = CircleShape,
                color = MonrCyanAccent,
                modifier = Modifier
                    .size(46.dp)
                    .clickable {
                        if (inputText.isNotBlank()) {
                            val msg = inputText.trim()
                            inputText = ""
                            onSendMessage(selectedPeerIdState, msg)
                        }
                    }
                    .testTag("btn_send_chat")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = Color(0xFF07121E),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatChatTime(epochMs: Long): String {
    val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(epochMs))
}
