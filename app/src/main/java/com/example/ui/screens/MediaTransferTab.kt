package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.media.MediaTransferStats
import com.example.core.media.compression.CompressionEngine
import com.example.core.media.db.MediaTransferEntity
import com.example.core.media.model.BandwidthProfile
import com.example.core.media.model.ChunkBitfield
import com.example.core.media.model.ChunkInfo
import com.example.core.media.model.MediaType
import com.example.core.media.model.TransferDirection
import com.example.core.media.model.TransferStatus

@Composable
fun MediaTransferTab(
    transfers: List<MediaTransferEntity>,
    selectedTransfer: MediaTransferEntity?,
    selectedChunks: List<ChunkInfo>,
    currentProfile: BandwidthProfile,
    stats: MediaTransferStats,
    onSelectTransfer: (MediaTransferEntity?) -> Unit,
    onSendPresetMedia: (MediaType, BandwidthProfile?) -> Unit,
    onSendCustomMedia: (String, ByteArray, MediaType, BandwidthProfile?) -> Unit,
    onPauseTransfer: (String) -> Unit,
    onResumeTransfer: (String) -> Unit,
    onCancelTransfer: (String) -> Unit,
    onDeleteTransfer: (String) -> Unit,
    onRequestSelectiveRepair: (String) -> Unit,
    onSetBandwidthProfile: (BandwidthProfile) -> Unit
) {
    var showSendDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var inspectingChunk by remember { mutableStateOf<Pair<Int, String>?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_media_transfer"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Bandwidth Adaptation Banner & Metrics
        item {
            BandwidthAdaptiveHeaderCard(
                profile = currentProfile,
                stats = stats,
                onConfigureProfile = { showProfileDialog = true },
                onOpenSendDialog = { showSendDialog = true }
            )
        }

        // 2. Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active & Historical Transfers (${transfers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Resumable Multi-Hop",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 3. Transfers List
        if (transfers.isEmpty()) {
            item {
                EmptyTransfersCard(onSendClick = { showSendDialog = true })
            }
        } else {
            items(transfers, key = { it.transferId }) { transfer ->
                val isSelected = selectedTransfer?.transferId == transfer.transferId
                MediaTransferCard(
                    transfer = transfer,
                    isSelected = isSelected,
                    onCardClick = { onSelectTransfer(if (isSelected) null else transfer) },
                    onPause = { onPauseTransfer(transfer.transferId) },
                    onResume = { onResumeTransfer(transfer.transferId) },
                    onCancel = { onCancelTransfer(transfer.transferId) },
                    onDelete = { onDeleteTransfer(transfer.transferId) },
                    onSelectiveRepair = { onRequestSelectiveRepair(transfer.transferId) },
                    onChunkClick = { idx, hash -> inspectingChunk = idx to hash }
                )
            }
        }
    }

    // Dialog: Send Media / File Transfer
    if (showSendDialog) {
        SendMediaDialog(
            currentProfile = currentProfile,
            onDismiss = { showSendDialog = false },
            onSendPreset = { type, forcedProfile ->
                onSendPresetMedia(type, forcedProfile)
                showSendDialog = false
            },
            onSendCustom = { name, bytes, type, forcedProfile ->
                onSendCustomMedia(name, bytes, type, forcedProfile)
                showSendDialog = false
            }
        )
    }

    // Dialog: Bandwidth Profile Selector
    if (showProfileDialog) {
        BandwidthProfileSelectorDialog(
            current = currentProfile,
            onSelect = {
                onSetBandwidthProfile(it)
                showProfileDialog = false
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    // Dialog: Inspect Individual Chunk SHA-256
    inspectingChunk?.let { (idx, hash) ->
        ChunkInspectionDialog(
            chunkIndex = idx,
            sha256 = hash,
            onDismiss = { inspectingChunk = null }
        )
    }
}

@Composable
private fun BandwidthAdaptiveHeaderCard(
    profile: BandwidthProfile,
    stats: MediaTransferStats,
    onConfigureProfile: () -> Unit,
    onOpenSendDialog: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_bandwidth_adaptive_header"),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Bandwidth-Adaptive Media Sharing",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automatic chunk sizing & SHA-256 verification",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onConfigureProfile,
                    modifier = Modifier.testTag("btn_configure_bandwidth")
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "Configure Profile")
                }
            }

            // Current Profile Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Active Profile: ${profile.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = profile.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Text(
                        text = "${profile.chunkSizeBytes}B",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMetricBox(
                    label = "Active",
                    value = "${stats.activeTransfers}",
                    modifier = Modifier.weight(1f)
                )
                StatMetricBox(
                    label = "Completed",
                    value = "${stats.completedTransfers}",
                    modifier = Modifier.weight(1f)
                )
                StatMetricBox(
                    label = "Transferred",
                    value = formatBytes(stats.totalTransferredBytes),
                    modifier = Modifier.weight(1.2f)
                )
                StatMetricBox(
                    label = "GZIP Saved",
                    value = formatBytes(stats.compressionSavingsBytes),
                    modifier = Modifier.weight(1.2f),
                    valueColor = MaterialTheme.colorScheme.tertiary
                )
            }

            // Action row
            Button(
                onClick = onOpenSendDialog,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_send_media_dialog")
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send File / Media Payload")
            }
        }
    }
}

@Composable
private fun StatMetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaTransferCard(
    transfer: MediaTransferEntity,
    isSelected: Boolean,
    onCardClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSelectiveRepair: () -> Unit,
    onChunkClick: (Int, String) -> Unit
) {
    val bitfield = remember(transfer.bitfieldHex, transfer.totalChunks) {
        ChunkBitfield.fromHex(transfer.bitfieldHex, transfer.totalChunks)
    }

    val missingCount = bitfield.getMissingIndices().size
    val isComplete = transfer.transferStatus == TransferStatus.COMPLETED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("transfer_card_${transfer.transferId}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Icon + Name + Direction Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    MediaTypeIcon(mediaType = transfer.parsedMediaType)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = transfer.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${transfer.senderAlias} → ${if (transfer.receiverNodeId == "*") "All Nodes" else transfer.receiverNodeId.take(8)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DirectionPill(direction = transfer.transferDirection)
            }

            // Size & Compression comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatBytes(transfer.transferredSizeBytes)} / ${formatBytes(transfer.originalSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )

                if (transfer.isCompressed && transfer.originalSizeBytes > transfer.transferredSizeBytes) {
                    val savingsPct = ((1f - (transfer.transferredSizeBytes.toFloat() / transfer.originalSizeBytes.toFloat())) * 100).toInt()
                    Text(
                        text = "GZIP -$savingsPct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }

                StatusPill(status = transfer.transferStatus)
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { transfer.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when (transfer.transferStatus) {
                    TransferStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    TransferStatus.PAUSED -> MaterialTheme.colorScheme.secondary
                    TransferStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
            )

            // Chunk status caption & Root SHA-256
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${transfer.verifiedChunksCount}/${transfer.totalChunks} Chunks (${transfer.chunkSizeBytes}B slice)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "SHA-256: ${transfer.sha256FileHash.take(10)}...",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Visual Chunk Block Matrix (interactive chunk verification visualizer)
            Text(
                text = "Chunk Integrity Matrix (Tap block to inspect):",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 0 until transfer.totalChunks) {
                    val isChunkDone = bitfield.isComplete(i)
                    ChunkBlockSquare(
                        index = i,
                        isDone = isChunkDone,
                        isTransferring = transfer.transferStatus == TransferStatus.TRANSFERRING,
                        onClick = {
                            val dummyHash = "${transfer.sha256FileHash.take(8)}${i.toString(16)}"
                            onChunkClick(i, dummyHash)
                        }
                    )
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (missingCount > 0 && transfer.transferStatus != TransferStatus.CANCELLED) {
                    OutlinedButton(
                        onClick = onSelectiveRepair,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_repair_${transfer.transferId}")
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Repair Missing ($missingCount)", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (transfer.transferStatus == TransferStatus.TRANSFERRING) {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.secondary)
                    }
                } else if (transfer.transferStatus == TransferStatus.PAUSED) {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ChunkBlockSquare(
    index: Int,
    isDone: Boolean,
    isTransferring: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "chunk_pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val color = when {
        isDone -> MaterialTheme.colorScheme.primary
        isTransferring -> MaterialTheme.colorScheme.tertiary.copy(alpha = alphaAnim)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$index",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDone) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MediaTypeIcon(mediaType: MediaType) {
    val icon = when (mediaType) {
        MediaType.IMAGE -> Icons.Default.Image
        MediaType.DOCUMENT -> Icons.Default.Description
        MediaType.TACTICAL_MAP -> Icons.Default.Map
        MediaType.AUDIO_RECORDING -> Icons.Default.Audiotrack
        MediaType.SYSTEM_ARCHIVE -> Icons.Default.Archive
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = mediaType.label,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DirectionPill(direction: TransferDirection) {
    val isOutgoing = direction == TransferDirection.OUTGOING
    val label = if (isOutgoing) "OUT" else "IN"
    val color = if (isOutgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun StatusPill(status: TransferStatus) {
    val (label, color) = when (status) {
        TransferStatus.COMPLETED -> "Verified" to MaterialTheme.colorScheme.primary
        TransferStatus.TRANSFERRING -> "Streaming" to MaterialTheme.colorScheme.tertiary
        TransferStatus.PAUSED -> "Paused" to MaterialTheme.colorScheme.secondary
        TransferStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
        TransferStatus.PENDING -> "Pending" to MaterialTheme.colorScheme.outline
        TransferStatus.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.outline
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun EmptyTransfersCard(onSendClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No File Transfers Active",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Transmit compressed images, situational reports, tactical maps, or custom files across the mesh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onSendClick) {
                Text("Start File Transfer")
            }
        }
    }
}

@Composable
private fun SendMediaDialog(
    currentProfile: BandwidthProfile,
    onDismiss: () -> Unit,
    onSendPreset: (MediaType, BandwidthProfile?) -> Unit,
    onSendCustom: (String, ByteArray, MediaType, BandwidthProfile?) -> Unit
) {
    var selectedPreset by remember { mutableStateOf(MediaType.IMAGE) }
    var isCustomMode by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("field_note.txt") }
    var customText by remember { mutableStateOf("URGENT: All units switch to alternate evacuation rendezvous point.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Send Bandwidth-Adaptive Media", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Select tactical preset payload or enter custom text to chunk and transfer:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Presets buttons
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetOptionRow(
                        title = "Tactical Recon Image",
                        subtitle = "JPEG ~28 KB (Heavy raster matrix)",
                        icon = Icons.Default.Image,
                        isSelected = !isCustomMode && selectedPreset == MediaType.IMAGE,
                        onClick = { isCustomMode = false; selectedPreset = MediaType.IMAGE }
                    )

                    PresetOptionRow(
                        title = "Situational Report (SITREP)",
                        subtitle = "Text Document ~18 KB (GZIP compress)",
                        icon = Icons.Default.Description,
                        isSelected = !isCustomMode && selectedPreset == MediaType.DOCUMENT,
                        onClick = { isCustomMode = false; selectedPreset = MediaType.DOCUMENT }
                    )

                    PresetOptionRow(
                        title = "Topographic Vector Grid Map",
                        subtitle = "GeoJSON ~16 KB (Waypoints & Polygons)",
                        icon = Icons.Default.Map,
                        isSelected = !isCustomMode && selectedPreset == MediaType.TACTICAL_MAP,
                        onClick = { isCustomMode = false; selectedPreset = MediaType.TACTICAL_MAP }
                    )

                    PresetOptionRow(
                        title = "Custom Text / Document",
                        subtitle = "Enter custom offline field message",
                        icon = Icons.Default.Build,
                        isSelected = isCustomMode,
                        onClick = { isCustomMode = true }
                    )
                }

                if (isCustomMode) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Filename") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        label = { Text("Document Content") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isCustomMode) {
                        onSendCustom(
                            customName,
                            customText.toByteArray(Charsets.UTF_8),
                            MediaType.DOCUMENT,
                            null
                        )
                    } else {
                        onSendPreset(selectedPreset, null)
                    }
                },
                modifier = Modifier.testTag("btn_confirm_send_media")
            ) {
                Text("Compress & Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PresetOptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BandwidthProfileSelectorDialog(
    current: BandwidthProfile,
    onSelect: (BandwidthProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val profiles = listOf(
        BandwidthProfile.BLE_NARROW,
        BandwidthProfile.WIFI_AWARE_BALANCED,
        BandwidthProfile.WIFI_DIRECT_HIGH_THROUGHPUT,
        BandwidthProfile.CLOUD_WAN_ADAPTIVE
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bandwidth Adaptation Override", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Override automatic tier detection with a forced chunk sizing & rate profile:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                profiles.forEach { p ->
                    val isSel = p.name == current.name
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(p) }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = p.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = p.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ChunkInspectionDialog(
    chunkIndex: Int,
    sha256: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Chunk #$chunkIndex Verification", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Individual Fragment Cryptographic Verification:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "SHA-256 Checksum:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = sha256,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Fragment verified against wire CRC & SHA-256",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024) {
        return "%.1f KB".format(kb)
    }
    val mb = kb / 1024.0
    return "%.2f MB".format(mb)
}
