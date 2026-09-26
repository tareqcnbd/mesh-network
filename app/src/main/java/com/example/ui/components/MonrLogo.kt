package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MonrCyanAccent
import com.example.ui.theme.MonrCyanLight
import com.example.ui.theme.MonrPrimaryDark
import com.example.ui.theme.MonrTextPrimary
import com.example.ui.theme.MonrTextPrimaryLight

/**
 * Monr Favicon / Brand Symbol:
 * Stylized off-grid mesh constellation icon featuring a central node with orbital connection rings
 * and active glowing satellite nodes.
 */
@Composable
fun MonrFavicon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    isDarkTheme: Boolean = true
) {
    val ringColor = if (isDarkTheme) MonrCyanAccent.copy(alpha = 0.5f) else Color(0xFF0284C7)
    val centerColor = if (isDarkTheme) MonrCyanAccent else Color(0xFF0284C7)
    val satelliteColor = if (isDarkTheme) MonrCyanLight else Color(0xFF38BDF8)

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val outerRadius = w * 0.42f
        val innerRadius = w * 0.22f

        // Outer dashed orbit ring
        drawCircle(
            color = ringColor,
            radius = outerRadius,
            center = center,
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )

        // Connecting lines to satellite nodes
        val angle1 = -Math.PI / 4
        val angle2 = Math.PI * 0.6
        val sat1 = Offset(
            (center.x + outerRadius * Math.cos(angle1)).toFloat(),
            (center.y + outerRadius * Math.sin(angle1)).toFloat()
        )
        val sat2 = Offset(
            (center.x + outerRadius * Math.cos(angle2)).toFloat(),
            (center.y + outerRadius * Math.sin(angle2)).toFloat()
        )

        drawLine(
            color = ringColor.copy(alpha = 0.7f),
            start = center,
            end = sat1,
            strokeWidth = 2f
        )
        drawLine(
            color = ringColor.copy(alpha = 0.7f),
            start = center,
            end = sat2,
            strokeWidth = 2f
        )

        // Center hub node
        drawCircle(
            color = centerColor,
            radius = innerRadius,
            center = center
        )

        // Satellite nodes
        drawCircle(
            color = satelliteColor,
            radius = w * 0.09f,
            center = sat1
        )
        drawCircle(
            color = Color(0xFFF59E0B), // Warm amber second node
            radius = w * 0.08f,
            center = sat2
        )
    }
}

/**
 * Monr Wordmark matching the exact typography seen in the uploaded UI mockups:
 * Clean, bold, modern geometric type.
 */
@Composable
fun MonrWordmark(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    fontSize: TextUnit = 24.sp
) {
    val textColor = if (isDarkTheme) Color.White else MonrPrimaryDark

    Text(
        text = "Monr",
        color = textColor,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        fontFamily = FontFamily.SansSerif,
        letterSpacing = (-0.5).sp,
        modifier = modifier
    )
}

/**
 * Monr Header combining the favicon and bold wordmark
 */
@Composable
fun MonrHeaderLogo(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    showSubtitle: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonrFavicon(size = 28.dp, isDarkTheme = isDarkTheme)
        Spacer(modifier = Modifier.width(10.dp))
        MonrWordmark(isDarkTheme = isDarkTheme, fontSize = 24.sp)
        if (showSubtitle) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "io",
                color = MonrCyanAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
