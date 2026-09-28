package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
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
    onImportFileClick: () -> Unit,
    onDecryptPassword: (AuthorizedCredential) -> String,
    onDeleteCredential: (Long) -> Unit,
    onUpdateCredential: (id: Long, newSsid: String, newPassword: String, notes: String) -> Unit,
    onClearAllCredentials: () -> Unit,
    onEditImportedEntry: (id: String, newSsid: String, newPassword: String) -> Unit,
    onDeleteImportedEntry: (id: String) -> Unit,
    onClearAllImported: () -> Unit,
    onManualAdd: (ssid: String, password: String, notes: String) -> Unit,
    onSelectToConnect: (ssid: String, password: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Imported List, 1: Saved Vault
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
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
                    entry.ssid.contains(searchQuery, ignoreCase = true)
        }
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
                            text = "Password List & Credentials",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${importedEntries.size} imported • ${credentials.size} in local vault",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                        Text("Import File")
                    }

                    FilledTonalButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_credential_manually_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Manual")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Security Notice Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Zero automated scanning. Passwords are only used when you explicitly choose to connect.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tab Navigation: Imported List vs Saved Vault
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
                        text = "Imported List (${importedEntries.size})",
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
                    Text(if (selectedTab == 0) "Search imported entries..." else "Search saved vault...")
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

        // Tab Content
        if (selectedTab == 0) {
            // TAB 0: IMPORTED LIST SCREEN
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
                            text = if (searchQuery.isNotBlank()) "No matching imported entries" else "No Imported Password File",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Import a TXT or CSV file with 'SSID,Password' format.",
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
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredImported, key = { it.id }) { item ->
                        val isRevealed = revealedPasswords[item.id] == true
                        val nearbyMatch = nearbyMap[item.ssid.lowercase()]

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (nearbyMatch != null) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                }
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
                                    tint = if (nearbyMatch != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.ssid,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (nearbyMatch != null) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(SignalGreen.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "In Range (${nearbyMatch.level} dBm)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SignalGreen,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = if (isRevealed) item.password else "••••••••••••",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Password visibility toggle
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

                                // Edit button
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

                                // Delete button
                                IconButton(
                                    onClick = { importedToDelete = item },
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

                                // Select / Connect button
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

                                // Password reveal toggle
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

                                // Edit button
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

                                // Delete button
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

                                // Select / Connect button
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

    // Manual Add Dialog
    if (showAddDialog) {
        var ssidInput by remember { mutableStateOf("") }
        var passwordInput by remember { mutableStateOf("") }
        var notesInput by remember { mutableStateOf("") }
        var showPassword by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Wi-Fi Credential") },
            text = {
                Column {
                    OutlinedTextField(
                        value = ssidInput,
                        onValueChange = { ssidInput = it },
                        label = { Text("SSID / Network Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Notes (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (ssidInput.isNotBlank()) {
                            onManualAdd(ssidInput.trim(), passwordInput, notesInput.trim())
                            showAddDialog = false
                        }
                    },
                    enabled = ssidInput.isNotBlank()
                ) {
                    Text("Save to Vault")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
                        label = { Text("SSID") },
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
