package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.model.BatchPasswordEntry
import com.example.model.PasswordBatchStatus
import com.example.model.PasswordBatchUiState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.AuthorizedCredential
import com.example.model.ImportedEntry
import com.example.model.WifiNetwork
import com.example.ui.theme.SignalGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CredentialsScreen(
    credentials: List<AuthorizedCredential>,
    importedEntries: List<ImportedEntry>,
    nearbyNetworks: List<WifiNetwork>,
    batchState: PasswordBatchUiState = PasswordBatchUiState(),
    onImportFileClick: () -> Unit,
    onDecryptPassword: (AuthorizedCredential) -> String,
    onDeleteCredential: (Long) -> Unit,
    onUpdateCredential: (id: Long, newSsid: String, newPassword: String, notes: String) -> Unit,
    onClearAllCredentials: () -> Unit,
    onEditImportedEntry: (id: String, newSsid: String, newPassword: String) -> Unit,
    onDeleteImportedEntry: (id: String) -> Unit,
    onClearAllImported: () -> Unit,
    onSelectToConnect: (ssid: String, password: String) -> Unit,
    onPauseImport: () -> Unit = {},
    onResumeImport: () -> Unit = {},
    onStopImport: () -> Unit = {},
    onLoadBatch: (Int) -> Unit = {},
    onSearchBatchStreaming: (String) -> Unit = {},
    onClearBatchSearch: () -> Unit = {},
    onSelectBatchEntry: (BatchPasswordEntry?) -> Unit = {},
    onClearBatchData: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Imported List, 1: Saved Vault
    var viewMode by remember { mutableStateOf("SINGLE") } // "SINGLE" (1 at a time) or "LIST"
    var currentSingleIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var jumpInput by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Dialog state for editing a saved credential
    var editingCredential by remember { mutableStateOf<AuthorizedCredential?>(null) }
    var editCredSsid by remember { mutableStateOf("") }
    var editCredPassword by remember { mutableStateOf("") }
    var editCredNotes by remember { mutableStateOf("") }

    // Dialog state for editing an imported entry
    var editingImported by remember { mutableStateOf<ImportedEntry?>(null) }
    var editImportSsid by remember { mutableStateOf("") }
    var editImportPassword by remember { mutableStateOf("") }

    var credentialToDelete by remember { mutableStateOf<AuthorizedCredential?>(null) }
    var importedToDelete by remember { mutableStateOf<ImportedEntry?>(null) }

    val revealedPasswords = remember { mutableStateMapOf<String, Boolean>() }
    val decryptedCache = remember { mutableStateMapOf<Long, String>() }

    val nearbyMap = remember(nearbyNetworks) {
        nearbyNetworks.associateBy { it.ssid.lowercase() }
    }

    val filteredSaved = remember(credentials, searchQuery) {
        credentials.filter { cred ->
            searchQuery.isBlank() ||
                    cred.ssid.contains(searchQuery, ignoreCase = true) ||
                    cred.notes.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredImported = remember(importedEntries, searchQuery) {
        importedEntries.filter { entry ->
            searchQuery.isBlank() ||
                    entry.ssid.contains(searchQuery, ignoreCase = true) ||
                    entry.password.contains(searchQuery, ignoreCase = true)
        }
    }

    val safeSingleIndex = if (filteredImported.isNotEmpty()) {
        currentSingleIndex.coerceIn(0, filteredImported.size - 1)
    } else 0

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Wi-Fi Password", text))
        Toast.makeText(context, "Password copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("credentials_screen")
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Hero Card with Action Buttons
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Password List & Vault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${importedEntries.size} imported entries • ${credentials.size} in vault",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onImportFileClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_password_file_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import TXT/CSV")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Background File Processing Card
        if (batchState.status != PasswordBatchStatus.IDLE || batchState.hasResumableCheckpoint || batchState.totalEntries > 0) {
            val stats = batchState.importProgress
            val numberFormat = java.text.NumberFormat.getNumberInstance()
            val processedCount = stats?.processedEntries ?: batchState.totalEntries
            val totalCount = stats?.totalEntries ?: batchState.totalEntries
            val processedFormatted = numberFormat.format(processedCount)
            val totalFormatted = if (totalCount > 0) numberFormat.format(totalCount) else "calculating..."

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("background_file_processing_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (batchState.status) {
                        PasswordBatchStatus.INDEXING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        PasswordBatchStatus.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        PasswordBatchStatus.READY -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        PasswordBatchStatus.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (batchState.status) {
                                            PasswordBatchStatus.INDEXING -> SignalGreen
                                            PasswordBatchStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                                            PasswordBatchStatus.READY -> MaterialTheme.colorScheme.primary
                                            PasswordBatchStatus.ERROR -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.outline
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (batchState.status) {
                                    PasswordBatchStatus.INDEXING -> "Background Processing Active"
                                    PasswordBatchStatus.PAUSED -> "Processing Paused"
                                    PasswordBatchStatus.READY -> "File Indexed & Ready"
                                    PasswordBatchStatus.ERROR -> "Processing Error"
                                    PasswordBatchStatus.STOPPED -> "Import Stopped"
                                    else -> "Checkpoint Available"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = batchState.fileName.ifBlank { "passwords.txt" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress Text per Prompt Requirement: "2,450,000 / 10,000,000 processed"
                    Text(
                        text = "$processedFormatted / $totalFormatted processed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("processed_entries_count_text")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress Bar
                    if (batchState.status == PasswordBatchStatus.INDEXING) {
                        if (stats != null && stats.percentage > 0f) {
                            LinearProgressIndicator(
                                progress = { (stats.percentage / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    } else if (batchState.status == PasswordBatchStatus.PAUSED) {
                        LinearProgressIndicator(
                            progress = { ((stats?.percentage ?: 0f) / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }

                    // Metrics line: Speed and ETA
                    if (stats != null && (stats.speedEntriesPerSec > 0 || stats.etaSeconds > 0 || stats.percentage > 0f)) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", stats.percentage)}% complete",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val speedStr = if (stats.speedEntriesPerSec > 0) "${numberFormat.format(stats.speedEntriesPerSec)} entries/s" else ""
                            val etaStr = if (stats.etaSeconds > 0) "ETA: ${stats.etaSeconds}s" else ""
                            val extra = listOf(speedStr, etaStr).filter { it.isNotBlank() }.joinToString(" • ")
                            if (extra.isNotBlank()) {
                                Text(
                                    text = extra,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Controls row: Pause/Resume, Stop, etc.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (batchState.status == PasswordBatchStatus.INDEXING) {
                            FilledTonalButton(
                                onClick = onPauseImport,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pause_import_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pause")
                            }

                            OutlinedButton(
                                onClick = onStopImport,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stop_import_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop")
                            }
                        } else if (batchState.status == PasswordBatchStatus.PAUSED || batchState.hasResumableCheckpoint) {
                            Button(
                                onClick = onResumeImport,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("resume_import_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume")
                            }

                            OutlinedButton(
                                onClick = onStopImport,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cancel_checkpoint_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear")
                            }
                        } else if (batchState.status == PasswordBatchStatus.READY) {
                            Text(
                                text = "100% indexed in memory-efficient disk index (Batch size: 500).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onClearBatchData,
                                modifier = Modifier.testTag("clear_batch_index_button")
                            ) {
                                Text("Clear Index")
                            }
                        }
                    }

                    if (batchState.status == PasswordBatchStatus.INDEXING) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Safe to minimize: foreground service runs uninterrupted.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Tab Navigation: Imported List vs Saved Vault
        val displayedImportedCount = if (batchState.totalEntries > 0) {
            java.text.NumberFormat.getNumberInstance().format(batchState.totalEntries)
        } else {
            importedEntries.size.toString()
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)),
            indicator = {},
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        text = "Imported List ($displayedImportedCount)",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                modifier = Modifier.testTag("tab_imported_list_screen")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        text = "Saved Vault (${credentials.size})",
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                modifier = Modifier.testTag("tab_saved_vault_screen")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar & Clear All Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(if (selectedTab == 0) "Search passwords..." else "Search saved vault...")
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_credentials_input")
            )

            val hasItemsToClear = if (selectedTab == 0) importedEntries.isNotEmpty() else credentials.isNotEmpty()
            if (hasItemsToClear) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.testTag("clear_all_button")
                ) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = "Clear All Entries",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // TAB 0: IMPORTED LIST SCREEN (Single Entry mode + Full list mode)
        if (selectedTab == 0) {
            if (filteredImported.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.FileDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching passwords" else "No Passwords Loaded",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Import a TXT file (one password per line) or CSV.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = onImportFileClick) {
                            Text("Select Password File")
                        }
                    }
                }
            } else {
                // View Mode selector (Single Entry vs List)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Entries: ${filteredImported.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row {
                        FilterChip(
                            selected = viewMode == "SINGLE",
                            onClick = { viewMode = "SINGLE" },
                            label = { Text("Single (1 / ${filteredImported.size})") },
                            leadingIcon = { Icon(Icons.Filled.ViewCarousel, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = viewMode == "LIST",
                            onClick = { viewMode = "LIST" },
                            label = { Text("List View") },
                            leadingIcon = { Icon(Icons.Filled.ViewList, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (viewMode == "SINGLE") {
                    val entry = filteredImported[safeSingleIndex]
                    val isRevealed = revealedPasswords[entry.id] == true

                    // 1-at-a-time Paginator Card per requirement
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top: Counter (Current: X / Total)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Current: ${safeSingleIndex + 1} / ${filteredImported.size}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                Row {
                                    IconButton(
                                        onClick = {
                                            editingImported = entry
                                            editImportSsid = entry.ssid
                                            editImportPassword = entry.password
                                        }
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit entry")
                                    }
                                    IconButton(
                                        onClick = { importedToDelete = entry }
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete entry", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Password Box
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = entry.ssid,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = if (isRevealed) entry.password else "••••••••••••",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { revealedPasswords[entry.id] = !isRevealed }
                                    ) {
                                        Icon(
                                            imageVector = if (isRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isRevealed) "Hide" else "Show")
                                    }

                                    FilledTonalButton(
                                        onClick = { copyToClipboard(entry.password) }
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copy")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Navigation Controls: First, Prev, Next, Last
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { currentSingleIndex = 0 },
                                    enabled = safeSingleIndex > 0
                                ) {
                                    Icon(Icons.Filled.FastRewind, contentDescription = "First entry")
                                }

                                Button(
                                    onClick = { currentSingleIndex = (safeSingleIndex - 1).coerceAtLeast(0) },
                                    enabled = safeSingleIndex > 0
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Previous")
                                }

                                Button(
                                    onClick = { currentSingleIndex = (safeSingleIndex + 1).coerceAtMost(filteredImported.size - 1) },
                                    enabled = safeSingleIndex < filteredImported.size - 1
                                ) {
                                    Text("Next")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                }

                                IconButton(
                                    onClick = { currentSingleIndex = filteredImported.size - 1 },
                                    enabled = safeSingleIndex < filteredImported.size - 1
                                ) {
                                    Icon(Icons.Filled.FastForward, contentDescription = "Last entry")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Jump to index row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = jumpInput,
                                    onValueChange = { jumpInput = it },
                                    placeholder = { Text("Jump to # (1 - ${filteredImported.size})") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            val target = jumpInput.toIntOrNull()
                                            if (target != null && target in 1..filteredImported.size) {
                                                currentSingleIndex = target - 1
                                                jumpInput = ""
                                            }
                                        }
                                    ),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        val target = jumpInput.toIntOrNull()
                                        if (target != null && target in 1..filteredImported.size) {
                                            currentSingleIndex = target - 1
                                            jumpInput = ""
                                        }
                                    }
                                ) {
                                    Text("Go")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Select for Wi-Fi Connection
                            Button(
                                onClick = { onSelectToConnect(entry.ssid, entry.password) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select for Authorized Wi-Fi Connection")
                            }
                        }
                    }
                } else {
                    // Full List Mode
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredImported.indices.toList()) { idx ->
                            val item = filteredImported[idx]
                            val isRevealed = revealedPasswords[item.id] == true

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${idx + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.width(36.dp)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.ssid,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isRevealed) item.password else "••••••••••••",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
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
                                        onClick = { copyToClipboard(item.password) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            editingImported = item
                                            editImportSsid = item.ssid
                                            editImportPassword = item.password
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                    }

                                    IconButton(
                                        onClick = { importedToDelete = item },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Button(
                                        onClick = { onSelectToConnect(item.ssid, item.password) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Select", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // TAB 1: SAVED VAULT SCREEN
            if (filteredSaved.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching vault credentials" else "No Saved Credentials Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Save credentials during connection or add them manually.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSaved, key = { it.id }) { cred ->
                        val isRevealed = revealedPasswords[cred.id.toString()] == true
                        val nearbyMatch = nearbyMap[cred.ssid.lowercase()]
                        val dateFormatted = remember(cred.createdAt) {
                            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(cred.createdAt))
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (nearbyMatch != null) Icons.Filled.Wifi else Icons.Filled.Key,
                                    contentDescription = null,
                                    tint = if (nearbyMatch != null) SignalGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = cred.ssid,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (nearbyMatch != null) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "In Range",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SignalGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    val displayText = if (isRevealed) {
                                        decryptedCache.getOrPut(cred.id) { onDecryptPassword(cred) }
                                    } else {
                                        "••••••••••••"
                                    }

                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Text(
                                        text = "Saved $dateFormatted • ${cred.importedFrom ?: "Vault"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val newState = !isRevealed
                                        revealedPasswords[cred.id.toString()] = newState
                                        if (newState && !decryptedCache.containsKey(cred.id)) {
                                            decryptedCache[cred.id] = onDecryptPassword(cred)
                                        }
                                    },
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
                                        val decrypted = decryptedCache.getOrPut(cred.id) { onDecryptPassword(cred) }
                                        editingCredential = cred
                                        editCredSsid = cred.ssid
                                        editCredPassword = decrypted
                                        editCredNotes = cred.notes
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                }

                                IconButton(
                                    onClick = { credentialToDelete = cred },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Button(
                                    onClick = {
                                        val pass = decryptedCache.getOrPut(cred.id) { onDecryptPassword(cred) }
                                        onSelectToConnect(cred.ssid, pass)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Select", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Saved Credential Dialog
    editingCredential?.let { cred ->
        AlertDialog(
            onDismissRequest = { editingCredential = null },
            title = { Text("Edit Saved Credential") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editCredSsid,
                        onValueChange = { editCredSsid = it },
                        label = { Text("SSID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editCredPassword,
                        onValueChange = { editCredPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editCredNotes,
                        onValueChange = { editCredNotes = it },
                        label = { Text("Notes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editCredSsid.isNotBlank()) {
                            onUpdateCredential(cred.id, editCredSsid.trim(), editCredPassword, editCredNotes.trim())
                            decryptedCache[cred.id] = editCredPassword
                            editingCredential = null
                        }
                    },
                    enabled = editCredSsid.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCredential = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Imported Entry Dialog
    editingImported?.let { entry ->
        AlertDialog(
            onDismissRequest = { editingImported = null },
            title = { Text("Edit Imported Entry") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editImportSsid,
                        onValueChange = { editImportSsid = it },
                        label = { Text("SSID / Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editImportPassword,
                        onValueChange = { editImportPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editImportSsid.isNotBlank()) {
                            onEditImportedEntry(entry.id, editImportSsid.trim(), editImportPassword)
                            editingImported = null
                        }
                    },
                    enabled = editImportSsid.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingImported = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Saved Confirm Dialog
    credentialToDelete?.let { cred ->
        AlertDialog(
            onDismissRequest = { credentialToDelete = null },
            title = { Text("Delete Credential?") },
            text = { Text("Are you sure you want to delete '${cred.ssid}' from your saved vault?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCredential(cred.id)
                        credentialToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { credentialToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Imported Confirm Dialog
    importedToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { importedToDelete = null },
            title = { Text("Delete Imported Entry?") },
            text = { Text("Are you sure you want to delete '${entry.ssid}' from the imported password list?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteImportedEntry(entry.id)
                        importedToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { importedToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear All Confirm Dialog
    if (showClearConfirm) {
        val clearTarget = if (selectedTab == 0) "all imported password entries" else "all saved vault credentials"
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Entries?") },
            text = { Text("This will permanently remove $clearTarget. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedTab == 0) {
                            onClearAllImported()
                        } else {
                            onClearAllCredentials()
                        }
                        showClearConfirm = false
                    }
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
