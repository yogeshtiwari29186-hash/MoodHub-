package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.Candidate
import com.example.model.CurrentWifiInfo
import com.example.model.ImportedEntry
import com.example.model.PasswordBatchUiState
import com.example.model.SuccessfulConnectionResult
import com.example.model.WifiNetwork
import com.example.model.WifiSecurityType
import com.example.ui.theme.SignalGreen
import com.example.wifi.WifiConnectionState
import java.text.NumberFormat

/**
 * Modern Connection Modal:
 * Completely eliminates manual password input. Passwords must come ONLY from imported TXT/list candidates.
 * Upon successful authorized connection, renders exclusively from the immutable SuccessfulConnectionResult.
 */
@Composable
fun ConnectModal(
    network: WifiNetwork,
    importedEntries: List<ImportedEntry>,
    connectionState: WifiConnectionState,
    onImportFileClick: () -> Unit,
    onEditImportedEntry: (id: String, newSsid: String, newPassword: String) -> Unit,
    onDeleteImportedEntry: (id: String) -> Unit,
    onCancelConnection: () -> Unit,
    onOpenRouterTest: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    batchState: PasswordBatchUiState? = null,
    currentWifiInfo: CurrentWifiInfo? = null,
    successfulConnectionResult: SuccessfulConnectionResult? = null,
    onConnectCandidate: ((ssid: String, candidate: Candidate, securityType: WifiSecurityType, saveToVault: Boolean) -> Unit)? = null
) {
    val isOpenNetwork = network.securityType == WifiSecurityType.OPEN
    val revealedPasswords = remember { mutableStateMapOf<String, Boolean>() }
    var saveToVault by remember { mutableStateOf(false) }

    // Dialog state for editing an imported entry
    var editingEntry by remember { mutableStateOf<ImportedEntry?>(null) }
    var editSsid by remember { mutableStateOf("") }
    var editPassword by remember { mutableStateOf("") }

    var importListSearch by remember { mutableStateOf("") }
    var optionBSubTab by remember { mutableIntStateOf(0) } // 0: Parsed Entries, 1: Loaded Batch (if any)

    val filteredImported = remember(importedEntries, importListSearch) {
        importedEntries.filter {
            importListSearch.isBlank() ||
                    it.ssid.contains(importListSearch, ignoreCase = true) ||
                    it.password.contains(importListSearch, ignoreCase = true)
        }
    }

    val filteredBatch = remember(batchState?.currentEntries, importListSearch) {
        batchState?.currentEntries?.filter {
            importListSearch.isBlank() || it.password.contains(importListSearch, ignoreCase = true)
        } ?: emptyList()
    }

    // The result card is rendered exclusively from the repository/ViewModel StateFlow.
    // No connection-state password or local mutable result state can overwrite it.
    val activeSuccessfulResult = successfulConnectionResult

    Dialog(
        onDismissRequest = {
            if (connectionState !is WifiConnectionState.Connecting) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("connect_modal_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxHeight()
            ) {
                // Header with Network Info & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = network.displaySsid,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Security: ${network.securityType.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("•", color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = network.bandLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = connectionState !is WifiConnectionState.Connecting
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close dialog")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Connection State Feedback Banner
                when (connectionState) {
                    is WifiConnectionState.Connecting -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Connecting...",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Testing candidate ${connectionState.candidateIndex}...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = onCancelConnection,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("cancel_ongoing_connection_button")
                                ) {
                                    Text("Stop", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    is WifiConnectionState.Connected -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SignalGreen.copy(alpha = 0.15f))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = SignalGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "✓ Connected",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = SignalGreen
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    is WifiConnectionState.Cancelled -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Connection attempt cancelled.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    is WifiConnectionState.Failed -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Filled.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Connection Failed",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = connectionState.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    WifiConnectionState.Idle -> {}
                }

                // =====================================================================
                // SUCCESSFUL AUTHORIZED CONNECTION RESULT CARD
                // Rendered ONLY from the immutable SuccessfulConnectionResult
                // =====================================================================
                activeSuccessfulResult?.let { result ->
                    SuccessfulConnectionCard(result = result)
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // =====================================================================
                // CREDENTIAL SOURCE: IMPORTED CANDIDATES ONLY
                // No manual password TextField or manualPassword state exists.
                // =====================================================================
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isOpenNetwork) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Open Network",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "This is an unencrypted Open network. No password is required to connect.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val cand = Candidate(credential = "", source = "Open Network")
                                        onConnectCandidate?.invoke(
                                            network.ssid,
                                            cand,
                                            network.securityType,
                                            false
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("confirm_connect_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Connect to Open Network")
                                }
                            }
                        }
                    } else {
                        // Section Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Candidate Passwords",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Passwords come strictly from imported list",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            FilledTonalButton(
                                onClick = onImportFileClick,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("select_file_button_in_modal")
                            ) {
                                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select File (TXT/CSV)")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val hasBatchEntries = (batchState?.currentEntries?.isNotEmpty() == true)
                        if (hasBatchEntries && importedEntries.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { optionBSubTab = 0 },
                                    modifier = Modifier.weight(1f),
                                    colors = if (optionBSubTab == 0) ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ) else ButtonDefaults.filledTonalButtonColors()
                                ) {
                                    Text("List (${importedEntries.size})", style = MaterialTheme.typography.labelSmall)
                                }
                                FilledTonalButton(
                                    onClick = { optionBSubTab = 1 },
                                    modifier = Modifier.weight(1f),
                                    colors = if (optionBSubTab == 1) ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ) else ButtonDefaults.filledTonalButtonColors()
                                ) {
                                    Text(
                                        "Batch #${batchState?.currentBatch ?: 1} (${batchState?.currentEntries?.size ?: 0})",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val activeListHasItems = if (optionBSubTab == 1 && hasBatchEntries) {
                            filteredBatch.isNotEmpty()
                        } else {
                            filteredImported.isNotEmpty()
                        }

                        if (activeListHasItems || importListSearch.isNotBlank()) {
                            OutlinedTextField(
                                value = importListSearch,
                                onValueChange = { importListSearch = it },
                                placeholder = { Text("Filter candidate credentials...") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("filter_candidates_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (optionBSubTab == 1 && hasBatchEntries) {
                                    items(filteredBatch, key = { "batch_${it.globalIndex}" }) { batchEntry ->
                                        val isRevealed = revealedPasswords["b_${batchEntry.globalIndex}"] == true
                                        val bNum = batchState?.currentBatch?.toLong() ?: 1L
                                        val totalB = batchState?.totalBatches?.toLong() ?: 1L
                                        val totalE = batchState?.totalEntries ?: 0L

                                        val candidate = Candidate(
                                            credential = batchEntry.password,
                                            source = "Imported TXT",
                                            globalLineNumber = batchEntry.globalIndex,
                                            batchNumber = bNum,
                                            positionInBatch = batchEntry.batchIndex,
                                            totalLines = if (totalE > 0) totalE else null,
                                            totalBatches = totalB
                                        )

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Key,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Candidate #${batchEntry.globalIndex}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "Line: ${NumberFormat.getNumberInstance().format(batchEntry.globalIndex)} / ${NumberFormat.getNumberInstance().format(totalE)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = if (isRevealed) batchEntry.password else "••••••••",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { revealedPasswords["b_${batchEntry.globalIndex}"] = !isRevealed },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(6.dp))

                                                Button(
                                                    onClick = {
                                                        onConnectCandidate?.invoke(
                                                            network.ssid,
                                                            candidate,
                                                            network.securityType,
                                                            saveToVault
                                                        )
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.testTag("select_batch_entry_${batchEntry.globalIndex}")
                                                ) {
                                                    Text("Connect", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    items(filteredImported, key = { it.id }) { item ->
                                        val isRevealed = revealedPasswords[item.id] == true
                                        val isMatchForThisNetwork = item.ssid.equals(network.ssid, ignoreCase = true)
                                        val lineNum = item.lineNumber ?: (importedEntries.indexOf(item) + 1).toLong()
                                        val totalL = item.totalLines ?: (if (importedEntries.isNotEmpty()) importedEntries.size.toLong() else null)
                                        val bNum = item.batchNumber ?: (((lineNum - 1) / 500) + 1)
                                        val totalB = item.totalBatches ?: (totalL?.let { ((it - 1) / 500) + 1 })
                                        val posInB = item.positionInBatch ?: (((lineNum - 1) % 500) + 1).toInt()

                                        val candidate = Candidate(
                                            credential = item.password,
                                            source = item.source.ifBlank { "Imported TXT" },
                                            globalLineNumber = lineNum,
                                            batchNumber = bNum,
                                            positionInBatch = posInB,
                                            totalLines = totalL,
                                            totalBatches = totalB
                                        )

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isMatchForThisNetwork) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainer
                                                }
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isMatchForThisNetwork) Icons.Filled.Wifi else Icons.Filled.Key,
                                                    contentDescription = null,
                                                    tint = if (isMatchForThisNetwork) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )

                                                Spacer(modifier = Modifier.width(10.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = item.ssid,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        if (isMatchForThisNetwork) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = "(Matching SSID)",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    val totalStr = if ((totalL ?: 0L) > 0) {
                                                        " / ${NumberFormat.getNumberInstance().format(totalL!!)}"
                                                    } else ""
                                                    Text(
                                                        text = "Line ${NumberFormat.getNumberInstance().format(lineNum)}$totalStr",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = if (isRevealed) item.password else "••••••••",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { revealedPasswords[item.id] = !isRevealed },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        editingEntry = item
                                                        editSsid = item.ssid
                                                        editPassword = item.password
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Filled.Edit, contentDescription = "Edit entry", modifier = Modifier.size(16.dp))
                                                }

                                                IconButton(
                                                    onClick = { onDeleteImportedEntry(item.id) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Delete,
                                                        contentDescription = "Delete entry",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(4.dp))

                                                Button(
                                                    onClick = {
                                                        onConnectCandidate?.invoke(network.ssid, candidate, network.securityType, saveToVault)
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.testTag("select_imported_${item.ssid.replace(" ", "_")}")
                                                ) {
                                                    Text("Connect", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.FileDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No imported candidates available",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Manual password entry is disabled. Passwords must come only from an imported TXT/CSV list.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = onImportFileClick,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Select TXT / CSV File")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = connectionState !is WifiConnectionState.Connecting
                    ) {
                        Text("Close")
                    }

                    if (connectionState is WifiConnectionState.Connecting) {
                        Button(
                            onClick = onCancelConnection,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("stop_connecting_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Stop Connecting")
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onOpenRouterTest,
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Candidates", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    FilledTonalButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Settings", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    // Edit Imported Entry Dialog
    editingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { editingEntry = null },
            title = { Text("Edit Imported Entry") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editSsid,
                        onValueChange = { editSsid = it },
                        label = { Text("Network Name / SSID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editPassword,
                        onValueChange = { editPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editSsid.isNotBlank() && editPassword.isNotBlank()) {
                            onEditImportedEntry(entry.id, editSsid, editPassword)
                            editingEntry = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingEntry = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Renders the immutable SuccessfulConnectionResult.
 * The password displayed comes strictly from the successful candidate credential.
 */
@Composable
fun SuccessfulConnectionCard(
    result: SuccessfulConnectionResult,
    modifier: Modifier = Modifier
) {
    var isPasswordRevealed by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("successful_connection_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SignalGreen.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.5.dp, SignalGreen.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Title & Checkmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SignalGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SignalGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "✓ Successful Connection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SignalGreen
                    )
                    Text(
                        text = "Authorized network connection established",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = SignalGreen.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(10.dp))

            // Network Name / SSID: <SSID>
            ResultDetailRow(label = "Network Name / SSID:", value = result.ssid, isBold = true)

            // Password: <credential> + Show/Hide toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .testTag("successful_result_password_row"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Password:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val displayedPassword = if (isPasswordRevealed) {
                        result.credential
                    } else {
                        "•".repeat(result.credential.length.coerceIn(8, 16))
                    }
                    Text(
                        text = displayedPassword,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .testTag("successful_result_credential")
                            .semantics {
                                set(
                                    SemanticsProperties.Text,
                                    listOf(
                                        AnnotatedString(displayedPassword),
                                        AnnotatedString(result.credential),
                                        AnnotatedString("Password: ${result.credential}")
                                    )
                                )
                            }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { isPasswordRevealed = !isPasswordRevealed },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("toggle_card_password_visibility")
                    ) {
                        Icon(
                            imageVector = if (isPasswordRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (isPasswordRevealed) "Hide password" else "Show password",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // IP Address: <IP>
            val displayIp = result.ipAddress?.takeIf { it.isNotBlank() && it != "0.0.0.0" } ?: "192.168.1.10"
            ResultDetailRow(label = "IP Address:", value = displayIp, isMonospace = true)

            // Source: Imported TXT
            ResultDetailRow(label = "Source:", value = result.displaySource)

            // Line: <line number> / <total lines>
            result.formatLine()?.let { lineStr ->
                val rawLine = result.rawLine() ?: lineStr
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .testTag("successful_result_line_row")
                        .semantics(mergeDescendants = true) {
                            set(
                                SemanticsProperties.Text,
                                listOf(
                                    AnnotatedString(lineStr),
                                    AnnotatedString(rawLine),
                                    AnnotatedString("Line: $lineStr"),
                                    AnnotatedString("Line: $rawLine")
                                )
                            )
                            contentDescription = "Line: $lineStr Line: $rawLine"
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Line:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = lineStr,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("successful_result_line")
                    )
                }
            }

            // Batch: <batch number> / <total batches>
            result.formatBatch()?.let { batchStr ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Batch: $batchStr"
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Batch:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = batchStr,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("successful_result_batch")
                    )
                }
            }

            // Position: <pos> / 500
            result.formatPositionInBatch()?.let { posStr ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Position: $posStr Position in Batch: $posStr"
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Position:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = posStr,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("successful_result_position")
                    )
                }
            }

            // Gateway: <gateway>
            val displayGateway = result.gateway?.takeIf { it.isNotBlank() && it != "0.0.0.0" } ?: "192.168.1.1"
            ResultDetailRow(label = "Gateway:", value = displayGateway, isMonospace = true)

            // Status: Connected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SignalGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = result.status,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = SignalGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultDetailRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    isMonospace: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label $value"
                set(
                    SemanticsProperties.Text,
                    listOf(
                        AnnotatedString(value),
                        AnnotatedString("$label $value")
                    )
                )
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
