package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MonrNavDestination
import com.example.ui.theme.MonrCyanAccent
import com.example.ui.theme.isMonrDark

/**
 * Custom Monr Bottom Navigation Bar:
 * Designed to replicate the bottom dock in the Monr reference designs:
 * - Rounded top corners (28.dp)
 * - Deep midnight navy surface
 * - Balanced vertical padding so icons are comfortably separated from the top border
 * - Mock-1 icons: Home, Chat, People, Gear with electric cyan when selected
 * - Electric Cyan active highlight without clipping oval indicator pills
 */
@Composable
fun MonrBottomBar(
    selectedNav: MonrNavDestination,
    onSelectNav: (MonrNavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val navItems = listOf(
        MonrNavDestination.NETWORK,
        MonrNavDestination.CHAT,
        MonrNavDestination.PEERS,
        MonrNavDestination.SETTINGS
    )

    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.isMonrDark()

    Surface(
        color = if (isDark) Color(0xFF0F1A2A) else scheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        border = BorderStroke(1.dp, if (isDark) Color(0x284E6F94) else scheme.outline.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("root_navigation_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 10.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                val isSelected = selectedNav == item ||
                        (item == MonrNavDestination.SETTINGS && selectedNav == MonrNavDestination.MORE)

                MonrBottomNavItem(
                    destination = item,
                    isSelected = isSelected,
                    onClick = { onSelectNav(item) }
                )
            }
        }
    }
}

@Composable
private fun MonrBottomNavItem(
    destination: MonrNavDestination,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val activeColor = MonrCyanAccent
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    val tintColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 200),
        label = "navTint"
    )

    val testTag = when (destination) {
        MonrNavDestination.NETWORK -> "nav_network"
        MonrNavDestination.CHAT -> "nav_chats"
        MonrNavDestination.PEERS -> "nav_peers"
        MonrNavDestination.SETTINGS -> "nav_settings"
        else -> "nav_more"
    }

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 28.dp),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .testTag("nav_item_${destination.label.lowercase()}"),
            contentAlignment = Alignment.Center
        ) {
            when (destination) {
                MonrNavDestination.NETWORK -> {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = "Network",
                        tint = tintColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                MonrNavDestination.CHAT -> {
                    Icon(
                        imageVector = Icons.Filled.Chat,
                        contentDescription = "Chats",
                        tint = tintColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                MonrNavDestination.PEERS -> {
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = "Peers",
                        tint = tintColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                MonrNavDestination.SETTINGS -> {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = tintColor,
                        modifier = Modifier.size(23.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                        tint = tintColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = if (destination == MonrNavDestination.CHAT) "Chats" else destination.label,
            color = tintColor,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/**
 * Hexagon icon with stacked double chevrons, exactly matching the "Chats" icon in the Monr reference design.
 */
@Composable
fun HexagonDoubleChevronIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Vertical Hexagon points
        val hexPath = Path().apply {
            moveTo(w * 0.50f, h * 0.05f)
            lineTo(w * 0.92f, h * 0.28f)
            lineTo(w * 0.92f, h * 0.72f)
            lineTo(w * 0.50f, h * 0.95f)
            lineTo(w * 0.08f, h * 0.72f)
            lineTo(w * 0.08f, h * 0.28f)
            close()
        }

        // Draw hexagon outline
        drawPath(
            path = hexPath,
            color = tint,
            style = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Upper Chevron
        val upperChevron = Path().apply {
            moveTo(w * 0.35f, h * 0.40f)
            lineTo(w * 0.50f, h * 0.49f)
            lineTo(w * 0.65f, h * 0.40f)
        }
        drawPath(
            path = upperChevron,
            color = tint,
            style = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Lower Chevron
        val lowerChevron = Path().apply {
            moveTo(w * 0.35f, h * 0.55f)
            lineTo(w * 0.50f, h * 0.64f)
            lineTo(w * 0.65f, h * 0.55f)
        }
        drawPath(
            path = lowerChevron,
            color = tint,
            style = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * Hexagon icon with single chevron, exactly matching the "Peers" icon in the Monr reference design.
 */
@Composable
fun HexagonSingleChevronIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Vertical Hexagon points
        val hexPath = Path().apply {
            moveTo(w * 0.50f, h * 0.05f)
            lineTo(w * 0.92f, h * 0.28f)
            lineTo(w * 0.92f, h * 0.72f)
            lineTo(w * 0.50f, h * 0.95f)
            lineTo(w * 0.08f, h * 0.72f)
            lineTo(w * 0.08f, h * 0.28f)
            close()
        }

        // Draw hexagon outline
        drawPath(
            path = hexPath,
            color = tint,
            style = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Center single Chevron
        val chevron = Path().apply {
            moveTo(w * 0.34f, h * 0.47f)
            lineTo(w * 0.50f, h * 0.58f)
            lineTo(w * 0.66f, h * 0.47f)
        }
        drawPath(
            path = chevron,
            color = tint,
            style = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
