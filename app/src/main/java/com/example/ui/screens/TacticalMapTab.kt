package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.geo.BreadcrumbTrackPoint
import com.example.core.geo.GeoMath
import com.example.core.geo.NodeLocationTelemetry
import com.example.core.geo.TacticalMarker
import com.example.core.geo.TacticalMarkerType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TacticalMapTab(
    myLocation: NodeLocationTelemetry,
    markers: List<TacticalMarker>,
    breadcrumbs: List<BreadcrumbTrackPoint>,
    peerLocations: Map<String, NodeLocationTelemetry>,
    selectedMarker: TacticalMarker?,
    onSelectMarker: (TacticalMarker?) -> Unit,
    onCreateMarker: (type: TacticalMarkerType, title: String, desc: String, lat: Double, lon: Double) -> Unit,
    onToggleResolve: (String) -> Unit,
    onDeleteMarker: (String) -> Unit,
    onSimulateStep: () -> Unit,
    onSimulatePeers: () -> Unit,
    onClearBreadcrumbs: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Map Viewport state: Center Lat/Lon and Zoom scale
    var centerLat by remember { mutableDoubleStateOf(myLocation.latitude) }
    var centerLon by remember { mutableDoubleStateOf(myLocation.longitude) }
    // Zoom factor: pixels per meter (0.5 = 1m is 0.5px, 2.0 = 1m is 2px)
    var zoomScale by remember { mutableFloatStateOf(1.2f) }

    var isAddMarkerDialogOpen by remember { mutableStateOf(false) }
    var filterType by remember { mutableStateOf<TacticalMarkerType?>(null) }
    var showRangeRings by remember { mutableStateOf(true) }
    var showBreadcrumbs by remember { mutableStateOf(true) }

    // Pulsing animation for SOS / Emergency markers
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val sosPulseAnim by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sosPulse"
    )

    val filteredMarkers = remember(markers, filterType) {
        if (filterType == null) markers else markers.filter { it.type == filterType }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        // 1. Fullscreen Custom Tactical Vector Canvas
        TacticalVectorCanvas(
            centerLat = centerLat,
            centerLon = centerLon,
            zoomScale = zoomScale,
            myLocation = myLocation,
            markers = filteredMarkers,
            breadcrumbs = if (showBreadcrumbs) breadcrumbs else emptyList(),
            peerLocations = peerLocations.values.toList(),
            selectedMarker = selectedMarker,
            sosPulseRadius = sosPulseAnim,
            showRangeRings = showRangeRings,
            onPan = { dx, dy ->
                // Convert pixel drag to lat/lon shift
                // 1 deg lat ~ 111,111 meters
                val metersPerPx = 1.0 / zoomScale
                val metersY = -dy * metersPerPx
                val metersX = dx * metersPerPx
                val deltaLat = metersY / 111111.0
                val deltaLon = metersX / (111111.0 * cos(Math.toRadians(centerLat)))
                centerLat += deltaLat
                centerLon += deltaLon
            },
            onMarkerClick = { marker ->
                onSelectMarker(marker)
            }
        )

        // 2. Top Header HUD with Coordinates & Compass Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(12.dp)
        ) {
            TacticalTelemetryHeader(
                myLocation = myLocation,
                activeMarkerCount = markers.size,
                peerCount = peerLocations.size
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Layer / Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                item {
                    FilterChip(
                        selected = filterType == null,
                        onClick = { filterType = null },
                        label = { Text("All (${markers.size})", fontSize = 11.sp) }
                    )
                }
                items(TacticalMarkerType.entries) { type ->
                    val count = markers.count { it.type == type }
                    if (count > 0 || type == TacticalMarkerType.HAZARD || type == TacticalMarkerType.SOS_DISTRESS) {
                        FilterChip(
                            selected = filterType == type,
                            onClick = { filterType = if (filterType == type) null else type },
                            label = { Text("${type.label} ($count)", fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // 3. Right-hand Map Viewport Controls (Zoom In, Zoom Out, Recenter, Rings Toggle)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FloatingActionButton(
                onClick = { zoomScale = (zoomScale * 1.3f).coerceAtMost(5.0f) },
                modifier = Modifier.size(44.dp).testTag("btn_zoom_in"),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }

            FloatingActionButton(
                onClick = { zoomScale = (zoomScale / 1.3f).coerceAtLeast(0.2f) },
                modifier = Modifier.size(44.dp).testTag("btn_zoom_out"),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }

            FloatingActionButton(
                onClick = {
                    centerLat = myLocation.latitude
                    centerLon = myLocation.longitude
                },
                modifier = Modifier.size(44.dp).testTag("btn_recenter"),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Recenter on Me")
            }

            FloatingActionButton(
                onClick = { showRangeRings = !showRangeRings },
                modifier = Modifier.size(44.dp).testTag("btn_toggle_rings"),
                containerColor = if (showRangeRings) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (showRangeRings) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(Icons.Default.Layers, contentDescription = "Toggle Rings")
            }
        }

        // 4. Bottom Controls / Simulation & Marker Placement Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Selected Marker Inspection Card (if active)
            AnimatedVisibility(visible = selectedMarker != null) {
                selectedMarker?.let { marker ->
                    TacticalMarkerDetailCard(
                        marker = marker,
                        myLocation = myLocation,
                        onClose = { onSelectMarker(null) },
                        onToggleResolve = { onToggleResolve(marker.markerId) },
                        onDelete = { onDeleteMarker(marker.markerId) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action toolbar: Drop Marker, Walk Step Simulation, Peer Simulation
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { isAddMarkerDialogOpen = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_drop_marker")
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Drop Marker", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onSimulateStep,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_sim_step")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Walk Step", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onSimulatePeers,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_sim_peers")
                    ) {
                        Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sim Peers", fontSize = 11.sp)
                    }

                    IconButton(
                        onClick = onClearBreadcrumbs,
                        modifier = Modifier.size(36.dp).testTag("btn_clear_trail")
                    ) {
                        Icon(Icons.Default.Timeline, contentDescription = "Clear Trail", tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }

    // Add Marker Dialog
    if (isAddMarkerDialogOpen) {
        AddTacticalMarkerDialog(
            currentLat = myLocation.latitude,
            currentLon = myLocation.longitude,
            onDismiss = { isAddMarkerDialogOpen = false },
            onConfirm = { type, title, desc, lat, lon ->
                onCreateMarker(type, title, desc, lat, lon)
                isAddMarkerDialogOpen = false
            }
        )
    }
}

/**
 * Custom Canvas vector map renderer with tactile grid, range rings, breadcrumbs, peers, and markers.
 */
@Composable
private fun TacticalVectorCanvas(
    centerLat: Double,
    centerLon: Double,
    zoomScale: Float,
    myLocation: NodeLocationTelemetry,
    markers: List<TacticalMarker>,
    breadcrumbs: List<BreadcrumbTrackPoint>,
    peerLocations: List<NodeLocationTelemetry>,
    selectedMarker: TacticalMarker?,
    sosPulseRadius: Float,
    showRangeRings: Boolean,
    onPan: (dx: Float, dy: Float) -> Unit,
    onMarkerClick: (TacticalMarker) -> Unit
) {
    // Keep a list of projected marker bounding boxes for tap detection
    val markerHitBoxes = remember { mutableListOf<Pair<TacticalMarker, Offset>>() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onPan(dragAmount.x, dragAmount.y)
                }
            }
            .pointerInput(markers, zoomScale, centerLat, centerLon) {
                detectTapGestures { tapOffset ->
                    // Check if tapped near any marker (within 28dp radius)
                    val clicked = markerHitBoxes.firstOrNull { (_, center) ->
                        val dx = tapOffset.x - center.x
                        val dy = tapOffset.y - center.y
                        (dx * dx + dy * dy) <= (28 * 28)
                    }
                    if (clicked != null) {
                        onMarkerClick(clicked.first)
                    }
                }
            }
    ) {
        markerHitBoxes.clear()
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerX = canvasWidth / 2f
        val centerY = canvasHeight / 2f

        // Projection Helper: Convert Lat/Lon to Canvas Screen (X, Y) relative to centerLat, centerLon
        fun projectToScreen(lat: Double, lon: Double): Offset {
            val metersY = (lat - centerLat) * 111111.0
            val metersX = (lon - centerLon) * (111111.0 * cos(Math.toRadians(centerLat)))
            val screenX = centerX + (metersX * zoomScale).toFloat()
            val screenY = centerY - (metersY * zoomScale).toFloat() // Invert Y for screen
            return Offset(screenX, screenY)
        }

        // 1. Draw Background Grid Lines (100m tactical coordinate grid)
        val gridStepMeters = 100f
        val gridStepPx = gridStepMeters * zoomScale
        val gridPaintColor = Color(0xFF1E293B)

        // Draw Vertical Grid Lines
        if (gridStepPx > 15f) {
            val offsetX = (centerX % gridStepPx)
            var x = offsetX
            while (x < canvasWidth) {
                drawLine(
                    color = gridPaintColor,
                    start = Offset(x, 0f),
                    end = Offset(x, canvasHeight),
                    strokeWidth = 1f
                )
                x += gridStepPx
            }

            // Draw Horizontal Grid Lines
            val offsetY = (centerY % gridStepPx)
            var y = offsetY
            while (y < canvasHeight) {
                drawLine(
                    color = gridPaintColor,
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = 1f
                )
                y += gridStepPx
            }
        }

        // 2. Draw Range Rings around My Location (50m, 100m, 250m, 500m)
        val myScreenPos = projectToScreen(myLocation.latitude, myLocation.longitude)

        if (showRangeRings) {
            val ringDistances = listOf(50f, 100f, 250f, 500f)
            val ringColors = listOf(
                Color(0x3338BDF8),
                Color(0x2838BDF8),
                Color(0x2038BDF8),
                Color(0x1838BDF8)
            )

            ringDistances.forEachIndexed { idx, distMeters ->
                val radiusPx = distMeters * zoomScale
                if (radiusPx > 10f && radiusPx < (canvasWidth * 1.5f)) {
                    drawCircle(
                        color = ringColors[idx],
                        radius = radiusPx,
                        center = myScreenPos,
                        style = Stroke(
                            width = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )
                    )
                }
            }
        }

        // 3. Draw Breadcrumb Trail (Connected polyline of past GPS coordinates)
        if (breadcrumbs.size >= 2) {
            val trailPath = Path()
            val firstPt = projectToScreen(breadcrumbs.first().latitude, breadcrumbs.first().longitude)
            trailPath.moveTo(firstPt.x, firstPt.y)

            for (i in 1 until breadcrumbs.size) {
                val pt = projectToScreen(breadcrumbs[i].latitude, breadcrumbs[i].longitude)
                trailPath.lineTo(pt.x, pt.y)
            }

            drawPath(
                path = trailPath,
                color = Color(0xFF38BDF8).copy(alpha = 0.6f),
                style = Stroke(
                    width = 3f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
                )
            )

            // Draw small track points
            breadcrumbs.forEach { pt ->
                val screenPt = projectToScreen(pt.latitude, pt.longitude)
                drawCircle(
                    color = Color(0xFF0284C7),
                    radius = 3f,
                    center = screenPt
                )
            }
        }

        // 4. Draw Peer Nodes
        peerLocations.forEach { peer ->
            val peerPos = projectToScreen(peer.latitude, peer.longitude)

            // Outer peer ring
            drawCircle(
                color = Color(0xFF10B981).copy(alpha = 0.25f),
                radius = 18f,
                center = peerPos
            )
            // Inner peer dot
            drawCircle(
                color = Color(0xFF10B981),
                radius = 7f,
                center = peerPos
            )
            // Peer heading line
            val rad = Math.toRadians(peer.bearingDegrees.toDouble())
            val headingEnd = Offset(
                peerPos.x + (16f * sin(rad)).toFloat(),
                peerPos.y - (16f * cos(rad)).toFloat()
            )
            drawLine(
                color = Color(0xFF34D399),
                start = peerPos,
                end = headingEnd,
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
        }

        // 5. Draw Tactical Markers
        markers.forEach { marker ->
            val markerPos = projectToScreen(marker.latitude, marker.longitude)
            markerHitBoxes.add(marker to markerPos)

            val isSelected = selectedMarker?.markerId == marker.markerId
            val markerColor = when (marker.type) {
                TacticalMarkerType.SOS_DISTRESS -> Color(0xFFEF4444)
                TacticalMarkerType.HAZARD -> Color(0xFFF97316)
                TacticalMarkerType.SUPPLY_DEPOT -> Color(0xFF10B981)
                TacticalMarkerType.MEETING_POINT -> Color(0xFF06B6D4)
                TacticalMarkerType.RALLY_POINT -> Color(0xFFA855F7)
                TacticalMarkerType.COMM_RELAY -> Color(0xFFF59E0B)
                TacticalMarkerType.PEER_POSITION -> Color(0xFF3B82F6)
                TacticalMarkerType.CUSTOM_NOTE -> Color(0xFF94A3B8)
            }

            // Pulsing circle for SOS distress beacons
            if (marker.type == TacticalMarkerType.SOS_DISTRESS && !marker.isResolved) {
                drawCircle(
                    color = Color(0xFFEF4444).copy(alpha = 0.35f),
                    radius = sosPulseRadius,
                    center = markerPos
                )
            }

            // Selection glow
            if (isSelected) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = 16f,
                    center = markerPos,
                    style = Stroke(width = 3f)
                )
            }

            // Outer badge
            drawCircle(
                color = if (marker.isResolved) Color(0xFF64748B) else markerColor,
                radius = 11f,
                center = markerPos
            )
            // Inner center dot
            drawCircle(
                color = Color.White,
                radius = 4f,
                center = markerPos
            )
        }

        // 6. Draw My Node (Directional cone and icon)
        // Accuracy radius circle
        val accuracyPx = myLocation.accuracyMeters * zoomScale
        drawCircle(
            color = Color(0x2238BDF8),
            radius = accuracyPx.coerceAtLeast(16f),
            center = myScreenPos
        )

        // Directional Heading Cone
        val headingRad = Math.toRadians(myLocation.bearingDegrees.toDouble())
        val coneSpreadRad = Math.toRadians(35.0)
        val coneLen = 32f

        val conePath = Path().apply {
            moveTo(myScreenPos.x, myScreenPos.y)
            lineTo(
                myScreenPos.x + (coneLen * sin(headingRad - coneSpreadRad)).toFloat(),
                myScreenPos.y - (coneLen * cos(headingRad - coneSpreadRad)).toFloat()
            )
            lineTo(
                myScreenPos.x + (coneLen * sin(headingRad + coneSpreadRad)).toFloat(),
                myScreenPos.y - (coneLen * cos(headingRad + coneSpreadRad)).toFloat()
            )
            close()
        }
        drawPath(
            path = conePath,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.6f), Color.Transparent),
                center = myScreenPos,
                radius = coneLen
            )
        )

        // My Node Center Circle
        drawCircle(
            color = Color.White,
            radius = 9f,
            center = myScreenPos
        )
        drawCircle(
            color = Color(0xFF0284C7),
            radius = 7f,
            center = myScreenPos
        )

        // 7. Center Target Crosshair (when map is panned away from self)
        val distToCenter = kotlin.math.sqrt(
            (myScreenPos.x - centerX) * (myScreenPos.x - centerX) +
            (myScreenPos.y - centerY) * (myScreenPos.y - centerY)
        )
        if (distToCenter > 60f) {
            val crossColor = Color(0x5594A3B8)
            val crossSize = 12f
            drawLine(crossColor, Offset(centerX - crossSize, centerY), Offset(centerX + crossSize, centerY), 1.5f)
            drawLine(crossColor, Offset(centerX, centerY - crossSize), Offset(centerX, centerY + crossSize), 1.5f)
        }
    }
}

@Composable
private fun TacticalTelemetryHeader(
    myLocation: NodeLocationTelemetry,
    activeMarkerCount: Int,
    peerCount: Int
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GPS LOCK • ±${myLocation.accuracyMeters.toInt()}m",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = GeoMath.formatDms(myLocation.latitude, myLocation.longitude),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${myLocation.bearingDegrees.toInt()}° HDG",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$activeMarkerCount Markers • $peerCount Peers",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TacticalMarkerDetailCard(
    marker: TacticalMarker,
    myLocation: NodeLocationTelemetry,
    onClose: () -> Unit,
    onToggleResolve: () -> Unit,
    onDelete: () -> Unit
) {
    val distMeters = GeoMath.haversineDistanceMeters(
        myLocation.latitude, myLocation.longitude,
        marker.latitude, marker.longitude
    )
    val bearing = GeoMath.initialBearing(
        myLocation.latitude, myLocation.longitude,
        marker.latitude, marker.longitude
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth().testTag("card_marker_detail")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (marker.isResolved) Color(0xFF64748B) else when (marker.type) {
                            TacticalMarkerType.SOS_DISTRESS -> Color(0xFFEF4444)
                            TacticalMarkerType.HAZARD -> Color(0xFFF97316)
                            TacticalMarkerType.SUPPLY_DEPOT -> Color(0xFF10B981)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                when (marker.type) {
                                    TacticalMarkerType.SOS_DISTRESS -> Icons.Default.Warning
                                    TacticalMarkerType.HAZARD -> Icons.Default.WarningAmber
                                    TacticalMarkerType.COMM_RELAY -> Icons.Default.Radio
                                    TacticalMarkerType.RALLY_POINT -> Icons.Default.Flag
                                    else -> Icons.Default.Place
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = marker.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${marker.type.label} • By ${marker.creatorAlias}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            if (marker.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = marker.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Navigation metrics: Distance and Bearing
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Range: ${if (distMeters < 1000) "${distMeters.toInt()}m" else "%.2f km".format(distMeters / 1000.0)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Bearing: ${bearing.toInt()}°",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (marker.isResolved) "RESOLVED" else "ACTIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (marker.isResolved) Color(0xFF64748B) else Color(0xFF10B981)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Card Action Buttons (Resolve & Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onToggleResolve,
                    modifier = Modifier.testTag("btn_toggle_resolve")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (marker.isResolved) "Reactivate" else "Mark Resolved", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onDelete, modifier = Modifier.testTag("btn_delete_marker")) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AddTacticalMarkerDialog(
    currentLat: Double,
    currentLon: Double,
    onDismiss: () -> Unit,
    onConfirm: (type: TacticalMarkerType, title: String, desc: String, lat: Double, lon: Double) -> Unit
) {
    var selectedType by remember { mutableStateOf(TacticalMarkerType.HAZARD) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Drop Tactical Marker", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Broadcasts marker to all mesh peers within radio & DTN range.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Marker Category:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(TacticalMarkerType.entries.filter { it != TacticalMarkerType.PEER_POSITION }) { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Marker Title") },
                    placeholder = { Text(selectedType.label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_marker_title")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Details / Instructions") },
                    placeholder = { Text("e.g. Blocked road, medical aid needed...") },
                    modifier = Modifier.fillMaxWidth().testTag("input_marker_desc")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Coordinates: ${GeoMath.formatDms(currentLat, currentLon)}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        selectedType,
                        title.ifBlank { selectedType.label },
                        description,
                        currentLat,
                        currentLon
                    )
                },
                modifier = Modifier.testTag("btn_confirm_drop_marker")
            ) {
                Text("Broadcast Marker")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
