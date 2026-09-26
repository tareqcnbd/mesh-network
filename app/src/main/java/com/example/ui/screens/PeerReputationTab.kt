package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.reputation.SecurityMetrics
import com.example.core.reputation.db.PeerReputationEntity
import com.example.core.reputation.db.ReputationAuditLogEntity
import com.example.core.reputation.model.TrustTier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeerReputationTab(
    localNodeId: String,
    reputations: List<PeerReputationEntity>,
    auditLogs: List<ReputationAuditLogEntity>,
    metrics: SecurityMetrics,
    onToggleQuarantine: (peerId: String, quarantined: Boolean, reason: String) -> Unit,
    onBroadcastGossip: (targetPeerId: String, delta: Int, reason: String) -> Unit,
    onSimulateFloodAttack: (peerId: String) -> Unit,
    onSimulateSolvePow: (peerId: String) -> Unit,
    onSimulatePositiveEndorsement: (peerId: String) -> Unit
) {
    var selectedPeer by remember { mutableStateOf<PeerReputationEntity?>(null) }
    var showGossipDialog by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf<TrustTier?>(null) }

    val filteredReputations = remember(reputations, activeFilter) {
        if (activeFilter == null) reputations
        else reputations.filter { it.trustTier == activeFilter?.name }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tab_peer_reputation"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Security Overview Header Card
        item {
            ReputationMetricsHeaderCard(
                localNodeId = localNodeId,
                metrics = metrics,
                totalPeers = reputations.size,
                quarantinedCount = reputations.count { it.isQuarantined }
            )
        }

        // 2. Trust Tier Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Peer Trust Classification (${reputations.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = activeFilter == null,
                        onClick = { activeFilter = null },
                        label = { Text("All (${reputations.size})") }
                    )
                    TrustTier.entries.forEach { tier ->
                        val count = reputations.count { it.trustTier == tier.name }
                        FilterChip(
                            selected = activeFilter == tier,
                            onClick = { activeFilter = if (activeFilter == tier) null else tier },
                            label = { Text("${tier.label} ($count)") }
                        )
                    }
                }
            }
        }

        // 3. Peer Reputation List
        if (filteredReputations.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (activeFilter == null) "No mesh peers registered yet in local reputation DB."
                            else "No peers currently classified as ${activeFilter?.label}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(filteredReputations, key = { it.peerNodeId }) { peer ->
                PeerReputationCard(
                    peer = peer,
                    onToggleQuarantine = { quarantined, reason ->
                        onToggleQuarantine(peer.peerNodeId, quarantined, reason)
                    },
                    onSimulateAttack = { onSimulateFloodAttack(peer.peerNodeId) },
                    onSimulatePow = { onSimulateSolvePow(peer.peerNodeId) },
                    onSimulateEndorse = { onSimulatePositiveEndorsement(peer.peerNodeId) },
                    onOpenGossip = {
                        selectedPeer = peer
                        showGossipDialog = true
                    }
                )
            }
        }

        // 4. Security Audit Trail & Replay/Flooding Logs
        item {
            SecurityAuditTrailSection(auditLogs = auditLogs)
        }
    }

    if (showGossipDialog && selectedPeer != null) {
        GossipAttestationDialog(
            targetPeer = selectedPeer!!,
            onDismiss = { showGossipDialog = false },
            onConfirm = { delta, reason ->
                onBroadcastGossip(selectedPeer!!.peerNodeId, delta, reason)
                showGossipDialog = false
            }
        )
    }
}

@Composable
fun ReputationMetricsHeaderCard(
    localNodeId: String,
    metrics: SecurityMetrics,
    totalPeers: Int,
    quarantinedCount: Int
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Anti-Spam & Gossip Consensus",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Local Node: ${localNodeId.take(12)}...",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (quarantinedCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (quarantinedCount > 0) "$quarantinedCount Quarantined" else "Mesh Secure",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (quarantinedCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Grid of Security Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(label = "Replay Drops", value = "${metrics.replayDrops}", icon = Icons.Default.Block, isWarning = metrics.replayDrops > 0)
                MetricItem(label = "Rate-Limit Drops", value = "${metrics.rateLimitDrops}", icon = Icons.Default.Speed, isWarning = metrics.rateLimitDrops > 0)
                MetricItem(label = "PoW Solved", value = "${metrics.powTokensVerified}", icon = Icons.Default.Key, isWarning = false)
                MetricItem(label = "Gossip Sent", value = "${metrics.gossipAttestationsSent}", icon = Icons.Default.Security, isWarning = false)
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isWarning: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun PeerReputationCard(
    peer: PeerReputationEntity,
    onToggleQuarantine: (quarantined: Boolean, reason: String) -> Unit,
    onSimulateAttack: () -> Unit,
    onSimulatePow: () -> Unit,
    onSimulateEndorse: () -> Unit,
    onOpenGossip: () -> Unit
) {
    val tier = TrustTier.valueOf(peer.trustTier)
    val tierColor = when (tier) {
        TrustTier.TRUSTED_CORE -> Color(0xFF2E7D32)
        TrustTier.NEUTRAL_VERIFIED -> Color(0xFF1976D2)
        TrustTier.SUSPICIOUS -> Color(0xFFF57C00)
        TrustTier.QUARANTINED -> Color(0xFFD32F2F)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_peer_${peer.peerNodeId.take(6)}"),
        colors = CardDefaults.cardColors(
            containerColor = if (peer.isQuarantined) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title & Trust Tier Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = peer.peerAlias,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ID: ${peer.peerNodeId.take(16)}...",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = tierColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, tierColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = tier.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tierColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Score Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { peer.reputationScore / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = tierColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${peer.reputationScore}/100",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tierColor
                )
            }

            if (peer.isQuarantined) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = peer.quarantineReason ?: "Blacklisted: repeated rate-limit/flood attacks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stat counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Valid: ${peer.validDeliveriesCount} | DelAcks: ${peer.verifiedDelAcksCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "Violations: ${peer.rateLimitViolationsCount} | Replays: ${peer.replayFloodViolationsCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (peer.rateLimitViolationsCount > 0 || peer.replayFloodViolationsCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onToggleQuarantine(!peer.isQuarantined, "Operator toggle")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (peer.isQuarantined) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = if (peer.isQuarantined) Icons.Default.CheckCircleOutline else Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (peer.isQuarantined) "Unblock" else "Quarantine", fontSize = 12.sp)
                }

                Button(
                    onClick = onOpenGossip,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gossip", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Simulation Playground Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onSimulateAttack,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Sim Spam", fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = onSimulatePow,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Sim PoW", fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = onSimulateEndorse,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("+ Trust", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun SecurityAuditTrailSection(auditLogs: List<ReputationAuditLogEntity>) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Anti-Spam Security Audit Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }

        if (auditLogs.isEmpty()) {
            Text(
                text = "No security events recorded yet. Run 'Sim Spam' or 'Sim PoW' to test audit trails.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    auditLogs.take(15).forEach { log ->
                        val isNegative = log.scoreDelta < 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (isNegative) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = log.eventType,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isNegative) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = dateFormat.format(Date(log.timestampEpochMs)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Text(
                                    text = log.reasonDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (log.scoreDelta >= 0) "+${log.scoreDelta}" else "${log.scoreDelta}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isNegative) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GossipAttestationDialog(
    targetPeer: PeerReputationEntity,
    onDismiss: () -> Unit,
    onConfirm: (delta: Int, reason: String) -> Unit
) {
    var delta by remember { mutableStateOf(5) }
    var reason by remember { mutableStateOf("Verified direct bundle relay and reliable link") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Broadcast Gossip Attestation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Gossip signed reputation rating to all neighboring mesh nodes for ${targetPeer.peerAlias} (${targetPeer.peerNodeId.take(8)}):",
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = delta == 5,
                        onClick = {
                            delta = 5
                            reason = "Verified direct bundle relay and reliable link"
                        },
                        label = { Text("+5 Positive") }
                    )
                    FilterChip(
                        selected = delta == -15,
                        onClick = {
                            delta = -15
                            reason = "Observed rate-limiting violations and packet flooding"
                        },
                        label = { Text("-15 Penalty") }
                    )
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Attestation Audit Reason") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reason.isNotBlank()) {
                        onConfirm(delta, reason)
                    }
                }
            ) {
                Text("Sign & Gossip")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
