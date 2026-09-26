package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.dtn.model.ChatMessageEntity
import com.example.core.dtn.model.MeshPeerEntity
import com.example.ui.theme.MonrAmberLight
import com.example.ui.theme.MonrCyanAccent
import com.example.ui.theme.MonrGlassCardBg
import com.example.ui.theme.MonrGlassCardBgLight
import com.example.ui.theme.MonrGlassCardBorder
import com.example.ui.theme.MonrGlassCardBorderLight
import com.example.ui.theme.isMonrDark

val MonrCanvasGradient = Brush.verticalGradient(
    listOf(
        Color(0xFF070F1B),
        Color(0xFF0C1728),
        Color(0xFF09121F)
    )
)

val MonrCanvasGradientLight = Brush.verticalGradient(
    listOf(
        Color(0xFFF8FAFC),
        Color(0xFFEEF4F8),
        Color(0xFFE8F0F6)
    )
)

@Composable
fun rememberMonrCanvasBrush(): Brush {
    val isDark = MaterialTheme.colorScheme.isMonrDark()
    return remember(isDark) {
        if (isDark) MonrCanvasGradient else MonrCanvasGradientLight
    }
}

@Composable
fun MonrScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(rememberMonrCanvasBrush()),
        content = content
    )
}

@Composable
fun MonrGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.isMonrDark()
    Surface(
        color = if (isDark) MonrGlassCardBg else MonrGlassCardBgLight,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, if (isDark) MonrGlassCardBorder else MonrGlassCardBorderLight),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        content()
    }
}

@Composable
fun MonrCyanPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glowing: Boolean = false
) {
    val shape = RoundedCornerShape(26.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MonrCyanAccent,
            contentColor = Color(0xFF07121E),
            disabledContainerColor = MonrCyanAccent.copy(alpha = 0.4f)
        ),
        shape = shape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        modifier = modifier
            .height(52.dp)
            .then(
                if (glowing) {
                    Modifier.shadow(
                        elevation = 18.dp,
                        shape = shape,
                        ambientColor = MonrCyanAccent.copy(alpha = 0.55f),
                        spotColor = MonrCyanAccent.copy(alpha = 0.85f)
                    )
                } else Modifier
            )
    ) {
        Text(
            text = text,
            color = Color(0xFF07121E),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MonrOutlinedPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val height = if (compact) 36.dp else 52.dp
    val fontSize = if (compact) 12.sp else 15.sp
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.isMonrDark()
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(if (compact) 18.dp else 26.dp),
        border = BorderStroke(1.2.dp, if (isDark) Color(0xFF385270) else scheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isDark) Color(0xFF0D1B2D) else scheme.surface,
            contentColor = scheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = if (compact) 14.dp else 16.dp, vertical = 0.dp),
        modifier = modifier.height(height)
    ) {
        Text(
            text = text,
            color = scheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MonrInitialsAvatar(
    name: String,
    accent: Color = MonrCyanAccent,
    size: Dp = 44.dp,
    fontSize: TextUnit = 14.sp
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (scheme.isMonrDark()) Color(0xFF1E324A) else scheme.surfaceVariant,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            val initials = peerInitials(name)
            if (initials.isBlank()) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(size * 0.5f)
                )
            } else {
                Text(
                    text = initials,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize
                )
            }
        }
    }
}

fun peerInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> ""
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

fun latestMessageByPeer(messages: List<ChatMessageEntity>): Map<String, ChatMessageEntity> {
    return messages
        .groupBy { it.peerNodeId }
        .mapValues { (_, list) -> list.maxBy { it.createdAtEpochMs } }
}

fun formatRelativeMinutes(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val minutes = ((nowMs - epochMs).coerceAtLeast(0L) / 60_000L)
    return when {
        minutes < 1L -> "now"
        minutes < 60L -> "${minutes}m"
        minutes < 1_440L -> "${minutes / 60L}h"
        else -> "${minutes / 1_440L}d"
    }
}

fun peerLinkCaption(peer: MeshPeerEntity?): String {
    if (peer == null) return "Mesh • Waiting"
    val hop = if (peer.isDirectNeighbor) "Direct" else "Relayed"
    val signal = when {
        peer.rssiDbm > -65 -> "Strong signal"
        peer.rssiDbm > -80 -> "Fair signal"
        else -> "Weak"
    }
    return "$hop • $signal"
}

fun peerAccentColor(isDirect: Boolean): Color {
    return if (isDirect) MonrCyanAccent else MonrAmberLight
}

fun peerActivitySubtitle(
    peer: MeshPeerEntity,
    lastMessage: ChatMessageEntity?
): String {
    return lastMessage?.plaintext
        ?: if (peer.isDirectNeighbor) "Direct" else "Relay active"
}
