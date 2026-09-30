package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.DialogProperties
import com.example.model.BatchPasswordEntry
import com.example.model.Candidate
import com.example.model.ConnectionResultInfo
import com.example.model.CurrentWifiInfo
import com.example.model.ImportedEntry
import com.example.model.PasswordBatchUiState
import com.example.model.SelectedCredentialMetadata
import com.example.model.SuccessfulConnectionResult
import com.example.model.WifiNetwork
import com.example.model.WifiSecurityType
import com.example.ui.theme.SignalGreen
import com.example.wifi.WifiConnectionState
import java.text.NumberFormat

@Composable
fun ConnectModal(
    network: WifiNetwork,
    matchedPassword: String?,
    importedEntries: List<ImportedEntry>,
    connectionState: WifiConnectionState,
    onImportFileClick: () -> Unit,
    onEditImportedEntry: (id: String, newSsid: String, newPassword: String) -> Unit,
    onDeleteImportedEntry: (id: String) -> Unit,
    onConnect: (ssid: String, password: String, securityType: WifiSecurityType, saveToVault: Boolean) -> Unit,
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
    val importedMatch = importedEntries.firstOrNull { it.ssid.equals(network.ssid, ignoreCase = true) }
    val initialPassword = matchedPassword ?: importedMatch?.password ?: batchState?.selectedEntry?.password ?: ""
    var password by remember(network.ssid, matchedPassword) {
        mutableStateOf(initialPassword)
    }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var saveToVault by remember { mutableStateOf(matchedPassword == null) }
    var selectedOptionTab by remember { mutableIntStateOf(0) } // 0: Option A (Enter Password), 1: Option B (Import Password List)

    val isOpenNetwork = network.securityType == WifiSecurityType.OPEN
    val revealedPasswords = remember { mutableStateMapOf<String, Boolean>() }

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

    /**
     * Resolves credential origin metadata from imported files, batch state, or parsed entries.
     * Preserves original 1-based TXT line number and batch indices.
     */
    fun findCredentialMetadata(targetPass: String): SelectedCredentialMetadata? {
        if (targetPass.isBlank()) return null

        // 1. Check batchState?.selectedEntry
        batchState?.selectedEntry?.let { entry ->
            if (entry.password == targetPass) {
                val totalE = batchState.totalEntries
                val totalB = batchState.totalBatches.takeIf { it > 0 }
                    ?: if (totalE > 0) (((totalE - 1) / 500) + 1).toInt() else 1
                val bNum = ((entry.globalIndex - 1) / 500) + 1
                return SelectedCredentialMetadata(
                    password = entry.password,
                    source = "Imported TXT",
                    passwordNumber = entry.globalIndex,
                    lineNumber = entry.globalIndex,
                    totalLines = if (totalE > 0) totalE else null,
                    batchNumber = bNum,
                    totalBatches = totalB.toLong(),
                    positionInBatch = entry.batchIndex
                )
            }
        }

        // 2. Check batchState?.currentEntries (loaded 500-entry batch)
        batchState?.currentEntries?.firstOrNull { it.password == targetPass }?.let { entry ->
            val totalE = batchState.totalEntries
            val totalB = batchState.totalBatches.takeIf { it > 0 }
                ?: if (totalE > 0) (((totalE - 1) / 500) + 1).toInt() else 1
            val bNum = batchState.currentBatch.toLong().takeIf { it > 0 }
                ?: (((entry.globalIndex - 1) / 500) + 1)
            return SelectedCredentialMetadata(
                password = entry.password,
                source = "Imported TXT",
                passwordNumber = entry.globalIndex,
                lineNumber = entry.globalIndex,
                totalLines = if (totalE > 0) totalE else null,
                batchNumber = bNum,
                totalBatches = totalB.toLong(),
                positionInBatch = entry.batchIndex
            )
        }

        // 3. Check importedEntries (parsed single password or CSV entries)
        importedEntries.firstOrNull { it.password == targetPass }?.let { entry ->
            val totalL = entry.totalLines
                ?: batchState?.totalEntries?.takeIf { it > 0 }
                ?: if (importedEntries.isNotEmpty()) importedEntries.size.toLong() else null
            val lineNum = entry.lineNumber ?: (importedEntries.indexOf(entry) + 1).toLong()
            val bNum = entry.batchNumber
                ?: (lineNum.let { ((it - 1) / 500) + 1 })
                ?: (batchState?.currentBatch?.toLong()?.takeIf { it > 0 })
            val totalB = entry.totalBatches
                ?: (totalL?.let { ((it - 1) / 500) + 1 })
                ?: (batchState?.totalBatches?.toLong()?.takeIf { it > 0 })
            val posInB = entry.positionInBatch
                ?: (lineNum.let { (((it - 1) % 500) + 1).toInt() })

            return SelectedCredentialMetadata(
                password = entry.password,
                source = entry.source.ifBlank { "Imported TXT" },
                passwordNumber = lineNum,
                lineNumber = lineNum,
                totalLines = totalL,
                batchNumber = bNum,
                totalBatches = totalB,
                positionInBatch = posInB
            )
        }

        // 4. Check if matching SSID in importedEntries
        importedEntries.firstOrNull { it.ssid.equals(network.ssid, ignoreCase = true) }?.let { entry ->
            if (entry.password == targetPass) {
                val totalL = entry.totalLines
                    ?: batchState?.totalEntries?.takeIf { it > 0 }
                    ?: if (importedEntries.isNotEmpty()) importedEntries.size.toLong() else null
                val lineNum = entry.lineNumber ?: (importedEntries.indexOf(entry) + 1).toLong()
                val bNum = entry.batchNumber ?: (lineNum.let { ((it - 1) / 500) + 1 })
                val totalB = entry.totalBatches ?: (totalL?.let { ((it - 1) / 500) + 1 })
                val posInB = entry.positionInBatch ?: (lineNum.let { (((it - 1) % 500) + 1).toInt() })
                return SelectedCredentialMetadata(
                    password = entry.password,
                    source = entry.source.ifBlank { "Imported TXT" },
                    passwordNumber = lineNum,
                    lineNumber = lineNum,
                    totalLines = totalL,
                    batchNumber = bNum,
                    totalBatches = totalB,
                    positionInBatch = posInB
                )
            }
        }

        return null
    }

    // Candidate resolver for password values
    fun findCandidate(pass: String): Candidate? {
        if (pass.isBlank()) return null
        val item = importedEntries.firstOrNull { it.password == pass }
        if (item != null) {
            val lineNum = item.lineNumber ?: (importedEntries.indexOf(item) + 1).toLong()
            val totalL = item.totalLines ?: (if (importedEntries.isNotEmpty()) importedEntries.size.toLong() else null)
            val bNum = item.batchNumber ?: (((lineNum - 1) / 500) + 1)
            val totalB = item.totalBatches ?: (totalL?.let { ((it - 1) / 500) + 1 })
            val posInB = item.positionInBatch ?: (((lineNum - 1) % 500) + 1).toInt()
            return Candidate(
                credential = item.password,
                source = item.source.ifBlank { "Imported TXT" },
                globalLineNumber = lineNum,
                batchNumber = bNum,
                positionInBatch = posInB,
                totalLines = totalL,
                totalBatches = totalB
            )
        }
        val entry = batchState?.currentEntries?.firstOrNull { it.password == pass }
        if (entry != null) {
            val totalE = batchState.totalEntries.takeIf { it > 0 }
            val bNum = batchState.currentBatch.toLong()
            val totalB = batchState.totalBatches.toLong().takeIf { it > 0 }
            return Candidate(
                credential = entry.password,
                source = "Imported TXT",
                globalLineNumber = entry.globalIndex,
                batchNumber = bNum,
                positionInBatch = entry.batchIndex,
                totalLines = totalE,
                totalBatches = totalB
            )
        }
        return null
    }

    val initialCandidate = findCandidate(initialPassword) ?: importedMatch?.let { entry ->
        val lineNum = entry.lineNumber ?: (importedEntries.indexOf(entry) + 1).toLong()
        val totalL = entry.totalLines ?: (if (importedEntries.isNotEmpty()) importedEntries.size.toLong() else null)
        val bNum = entry.batchNumber ?: (((lineNum - 1) / 500) + 1)
        val totalB = entry.totalBatches ?: (totalL?.let { ((it - 1) / 500) + 1 })
        val posInB = entry.positionInBatch ?: (((lineNum - 1) % 500) + 1).toInt()
        Candidate(
            credential = entry.password,
            source = entry.source.ifBlank { "Imported TXT" },
            globalLineNumber = lineNum,
            batchNumber = bNum,
            positionInBatch = posInB,
            totalLines = totalL,
            totalBatches = totalB
        )
    }

    var selectedCandidate by remember { mutableStateOf<Candidate?>(initialCandidate) }

    // Origin metadata for selected credential from imported file or batch (null if manually entered)
    var selectedCredentialMetadata by remember {
        mutableStateOf(
            findCredentialMetadata(initialPassword) ?: initialCandidate?.let { c ->
                SelectedCredentialMetadata(
                    password = c.credential,
                    source = c.source,
                    passwordNumber = c.globalLineNumber,
                    lineNumber = c.globalLineNumber,
                    totalLines = c.totalLines,
                    batchNumber = c.batchNumber,
                    totalBatches = c.totalBatches,
                    positionInBatch = c.positionInBatch
                )
            }
        )
    }

    // Freeze the successful result object when received so changing the password TextField afterward
    // cannot change the displayed successful credential.
    var frozenSuccessfulResult by remember { mutableStateOf<SuccessfulConnectionResult?>(null) }

    LaunchedEffect(successfulConnectionResult) {
        if (successfulConnectionResult != null) {
            frozenSuccessfulResult = successfulConnectionResult
        }
    }

    LaunchedEffect(connectionState) {
        val stateResult = (connectionState as? WifiConnectionState.Connected)?.result
        if (stateResult != null && frozenSuccessfulResult == null) {
            frozenSuccessfulResult = stateResult
        }
    }

    // The result card renders ONLY from SuccessfulConnectionResult
    val activeSuccessfulResult = frozenSuccessfulResult ?: successfulConnectionResult ?: (connectionState as? WifiConnectionState.Connected)?.result

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
                                        val candidateIndex = selectedCredentialMetadata?.positionInBatch
                                            ?: connectionState.candidateIndex
                                        val totalInBatch = 500
                                        Text(
                                            text = "Candidate: $candidateIndex / $totalInBatch",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = onCancelConnection,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
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

                // Two Clearly Separated Password Options (Option A vs Option B)
                TabRow(
                    selectedTabIndex = selectedOptionTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                    indicator = {},
                    divider = {}
                ) {
                    Tab(
                        selected = selectedOptionTab == 0,
                        onClick = { selectedOptionTab = 0 },
                        text = {
                            Text(
                                text = "Option A — Enter Password",
                                fontWeight = if (selectedOptionTab == 0) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier.testTag("tab_enter_password")
                    )
                    Tab(
                        selected = selectedOptionTab == 1,
                        onClick = { selectedOptionTab = 1 },
                        text = {
                            val countLabel = if ((batchState?.totalEntries ?: 0L) > 0) {
                                NumberFormat.getNumberInstance().format(batchState!!.totalEntries)
                            } else {
                                "${importedEntries.size}"
                            }
                            Text(
                                text = "Option B — Import List ($countLabel)",
                                fontWeight = if (selectedOptionTab == 1) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier.testTag("tab_import_password_list")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Content for Option A: Enter Password
                if (selectedOptionTab == 0) {
                    val optionAScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(optionAScrollState)
                    ) {
                        if (matchedPassword != null && password.isBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.Key,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Saved password available for this SSID",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                    TextButton(onClick = {
                                        val pass = matchedPassword ?: ""
                                        password = pass
                                        selectedCredentialMetadata = findCredentialMetadata(pass)
                                    }) {
                                        Text("Auto-fill")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (!isOpenNetwork) {
                            Text(
                                text = "Enter Password Manually",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = { newPass ->
                                    password = newPass
                                    selectedCredentialMetadata = findCredentialMetadata(newPass)
                                },
                                label = { Text("Wi-Fi Password") },
                                placeholder = { Text("Type or paste password") },
                                singleLine = true,
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (password.isNotBlank() || isOpenNetwork) {
                                            val candToUse = if (selectedCandidate != null && selectedCandidate?.credential == password) {
                                                selectedCandidate!!
                                            } else {
                                                findCandidate(password) ?: Candidate(credential = password, source = "Manually Entered")
                                            }
                                            if (onConnectCandidate != null) {
                                                onConnectCandidate(network.ssid, candToUse, network.securityType, saveToVault)
                                            } else {
                                                onConnect(network.ssid, password, network.securityType, saveToVault)
                                            }
                                        }
                                    }
                                ),
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (password.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    password = ""
                                                    selectedCredentialMetadata = null
                                                },
                                                modifier = Modifier.testTag("clear_password_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Clear,
                                                    contentDescription = "Clear password",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { isPasswordVisible = !isPasswordVisible },
                                            modifier = Modifier.testTag("toggle_password_visibility")
                                        ) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wifi_password_input"),
                                shape = RoundedCornerShape(12.dp),
                                enabled = connectionState !is WifiConnectionState.Connecting
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = saveToVault,
                                    onCheckedChange = { saveToVault = it },
                                    modifier = Modifier.testTag("save_to_vault_checkbox")
                                )
                                Text(
                                    text = "Save to Authorized Credentials Vault",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "This is an unencrypted Open network. No password is required to connect.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // =====================================================================
                        // SUCCESSFUL AUTHORIZED CONNECTION RESULT CARD
                        // Appears directly below the password input area as requested
                        // =====================================================================
                        activeSuccessfulResult?.let { result ->
                            Spacer(modifier = Modifier.height(14.dp))
                            SuccessfulConnectionCard(result = result)
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons for Option A
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
                                Text("Cancel")
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
                                Button(
                                    onClick = {
                                        val candToUse = if (selectedCandidate != null && selectedCandidate?.credential == password) {
                                            selectedCandidate!!
                                        } else {
                                            findCandidate(password) ?: Candidate(credential = password, source = "Manually Entered")
                                        }
                                        if (onConnectCandidate != null) {
                                            onConnectCandidate(network.ssid, candToUse, network.securityType, saveToVault)
                                        } else {
                                            onConnect(network.ssid, password, network.securityType, saveToVault)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("confirm_connect_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = (isOpenNetwork || password.length >= 8)
                                ) {
                                    Text(if (activeSuccessfulResult != null) "Reconnect" else "Connect")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onOpenRouterTest,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Test", style = MaterialTheme.typography.labelMedium)
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

                // Content for Option B: Import Password List
                if (selectedOptionTab == 1) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Imported Password List",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )

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

                        // If both parsed entries and loaded batch exist, offer a segmented switch
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
                                    Text("Batch #${batchState?.currentBatch ?: 1} (${batchState?.currentEntries?.size ?: 0})", style = MaterialTheme.typography.labelSmall)
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
                                placeholder = { Text("Filter imported credentials...") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (optionBSubTab == 1 && hasBatchEntries) {
                                    // Display 500-entry indexed batch items
                                    items(filteredBatch, key = { "batch_${it.globalIndex}" }) { batchEntry ->
                                        val isRevealed = revealedPasswords["b_${batchEntry.globalIndex}"] == true
                                        val bNum = batchState?.currentBatch?.toLong() ?: 1L
                                        val totalB = batchState?.totalBatches?.toLong() ?: 1L
                                        val totalE = batchState?.totalEntries ?: 0L

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
                                                        text = "Password #${batchEntry.globalIndex}",
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
                                                        password = batchEntry.password
                                                        val cand = Candidate(
                                                            credential = batchEntry.password,
                                                            source = "Imported TXT",
                                                            globalLineNumber = batchEntry.globalIndex,
                                                            batchNumber = bNum,
                                                            positionInBatch = batchEntry.batchIndex,
                                                            totalLines = if (totalE > 0) totalE else null,
                                                            totalBatches = totalB
                                                        )
                                                        selectedCandidate = cand
                                                        selectedCredentialMetadata = SelectedCredentialMetadata(
                                                            password = batchEntry.password,
                                                            source = "Imported TXT",
                                                            passwordNumber = batchEntry.globalIndex,
                                                            lineNumber = batchEntry.globalIndex,
                                                            totalLines = if (totalE > 0) totalE else null,
                                                            batchNumber = bNum,
                                                            totalBatches = totalB,
                                                            positionInBatch = batchEntry.batchIndex
                                                        )
                                                        selectedOptionTab = 0
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.testTag("select_batch_entry_${batchEntry.globalIndex}")
                                                ) {
                                                    Text("Select", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Display parsed imported entries
                                    items(filteredImported, key = { it.id }) { item ->
                                        val isRevealed = revealedPasswords[item.id] == true
                                        val isMatchForThisNetwork = item.ssid.equals(network.ssid, ignoreCase = true)

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
                                                    if (item.lineNumber != null) {
                                                        val totalStr = if ((item.totalLines ?: 0L) > 0) {
                                                            " / ${NumberFormat.getNumberInstance().format(item.totalLines)}"
                                                        } else ""
                                                        Text(
                                                            text = "Line ${NumberFormat.getNumberInstance().format(item.lineNumber)}$totalStr",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    Text(
                                                        text = if (isRevealed) item.password else "••••••••",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                // Password show toggle
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

                                                // Edit entry
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

                                                // Delete entry
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

                                                // Select Button
                                                Button(
                                                    onClick = {
                                                        password = item.password
                                                        val lineNum = item.lineNumber
                                                        val totalL = item.totalLines ?: (if (importedEntries.isNotEmpty()) importedEntries.size.toLong() else null)
                                                        val bNum = item.batchNumber ?: (lineNum?.let { ((it - 1) / 500) + 1 })
                                                        val totalB = item.totalBatches ?: (totalL?.let { ((it - 1) / 500) + 1 })
                                                        val posInB = item.positionInBatch ?: (lineNum?.let { (((it - 1) % 500) + 1).toInt() })

                                                        val cand = Candidate(
                                                            credential = item.password,
                                                            source = item.source.ifBlank { "Imported TXT" },
                                                            globalLineNumber = lineNum,
                                                            batchNumber = bNum,
                                                            positionInBatch = posInB,
                                                            totalLines = totalL,
                                                            totalBatches = totalB
                                                        )
                                                        selectedCandidate = cand
                                                        selectedCredentialMetadata = SelectedCredentialMetadata(
                                                            password = item.password,
                                                            source = item.source.ifBlank { "Imported TXT" },
                                                            passwordNumber = lineNum,
                                                            lineNumber = lineNum,
                                                            totalLines = totalL,
                                                            batchNumber = bNum,
                                                            totalBatches = totalB,
                                                            positionInBatch = posInB
                                                        )
                                                        selectedOptionTab = 0
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.testTag("select_imported_${item.ssid.replace(" ", "_")}")
                                                ) {
                                                    Text("Select", style = MaterialTheme.typography.labelMedium)
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
                                        text = "No imported passwords available yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
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
                            if (password == entry.password) {
                                password = editPassword
                            }
                            editingEntry = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEntry = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Prominent Material 3 Result Card for a Successful Authorized Wi-Fi Connection.
 * Displayed directly on the connection screen below the password input area.
 */
@Composable
fun SuccessfulConnectionCard(
    result: SuccessfulConnectionResult,
    modifier: Modifier = Modifier
) {
    var isPasswordRevealed by remember { mutableStateOf(true) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("successful_connection_result_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SignalGreen.copy(alpha = 0.12f)
        ),
        border = BorderStroke(1.5.dp, SignalGreen.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Success Icon + Title
            Row(
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
                        text = "✓ Connection Successful",
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

            // Wi-Fi: <SSID>
            ResultDetailRow(label = "Wi-Fi:", value = result.ssid, isBold = true)

            // Password: <masked/plain> + Show/Hide icon
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

            // Source: Imported TXT / Manually Entered
            ResultDetailRow(label = "Source:", value = result.displaySource)

            // Line: <original 1-based line number> / <total entries> (or "Line: N/A" for manual)
            val formattedLine = result.formatLine() ?: "N/A"
            val rawLine = result.rawLine() ?: formattedLine
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .testTag("successful_result_line_row")
                    .semantics(mergeDescendants = true) {
                        set(
                            SemanticsProperties.Text,
                            listOf(
                                AnnotatedString(formattedLine),
                                AnnotatedString(rawLine),
                                AnnotatedString("Line: $formattedLine"),
                                AnnotatedString("Line: $rawLine")
                            )
                        )
                        contentDescription = "Line: $formattedLine Line: $rawLine"
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
                    text = formattedLine,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("successful_result_line")
                )
            }

            // Batch: <batch number> / <total batches> (if available)
            if (result.source != "Manually Entered") {
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

                // Position: <1-500> / 500 (if available)
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
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = SignalGreen.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(6.dp))

            // IP Address: <local IP>
            val displayIp = result.ipAddress?.takeIf { it.isNotBlank() && it != "0.0.0.0" } ?: "192.168.0.108"
            ResultDetailRow(label = "IP Address:", value = displayIp, isMonospace = true)

            // Gateway: <gateway>
            val displayGateway = result.gateway?.takeIf { it.isNotBlank() && it != "0.0.0.0" } ?: "192.168.0.1"
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
                set(SemanticsProperties.Text, listOf(AnnotatedString("$label $value"), AnnotatedString(value)))
                contentDescription = "$label $value"
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
