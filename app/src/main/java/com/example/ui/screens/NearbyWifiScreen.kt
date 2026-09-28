package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AuthorizedCredential
import com.example.model.CurrentWifiInfo
import com.example.model.WifiNetwork
import com.example.ui.components.ConnectedWifiCard
import com.example.ui.components.NetworkListItem

@Composable
fun NearbyWifiScreen(
    networks: List<WifiNetwork>,
    currentInfo: CurrentWifiInfo,
    savedCredentials: List<AuthorizedCredential>,
    isScanning: Boolean,
    isWifiEnabled: Boolean,
    isLocationEnabled: Boolean,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onRefreshScan: () -> Unit,
    onSelectNetwork: (WifiNetwork) -> Unit,
    onImportFileClick: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, 5GHZ, 2.4GHZ, OPEN, SECURED

    val savedSsidSet = remember(savedCredentials) {
        savedCredentials.map { it.ssid.lowercase() }.toSet()
    }

    val filteredNetworks = remember(networks, searchQuery, selectedFilter) {
        networks.filter { network ->
            val matchesQuery = searchQuery.isBlank() ||
                    network.displaySsid.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "5GHZ" -> network.bandLabel.contains("5")
                "2.4GHZ" -> network.bandLabel.contains("2.4")
                "OPEN" -> !network.securityType.isSecure
                "SECURED" -> network.securityType.isSecure
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("nearby_wifi_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Active Wi-Fi Status Hero Card
        ConnectedWifiCard(
            currentInfo = currentInfo,
            onOpenSettings = onOpenWifiSettings
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Permission & Location Warnings
        if (!hasPermissions) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("permissions_warning_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Permissions Required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Nearby devices & Location access are required by Android to scan Wi-Fi networks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier.testTag("grant_permissions_button")
                    ) {
                        Text("Grant")
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        } else if (!isLocationEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Location Services Turned Off",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Android requires device Location to be turned ON for Wi-Fi scanner results.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            } catch (_: Exception) {}
                        }
                    ) {
                        Text("Enable")
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Section Title, Refresh Button, and Import Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Nearby Networks",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${filteredNetworks.size} networks detected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = onImportFileClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("import_file_quick_button")
                ) {
                    Icon(
                        Icons.Filled.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import File", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onRefreshScan,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("refresh_scan_button")
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh Wi-Fi Scan",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search & Filter Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search Wi-Fi by SSID...") },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_wifi_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { selectedFilter = "ALL" },
                label = { Text("All") }
            )
            FilterChip(
                selected = selectedFilter == "5GHZ",
                onClick = { selectedFilter = "5GHZ" },
                label = { Text("5 GHz") }
            )
            FilterChip(
                selected = selectedFilter == "2.4GHZ",
                onClick = { selectedFilter = "2.4GHZ" },
                label = { Text("2.4 GHz") }
            )
            FilterChip(
                selected = selectedFilter == "SECURED",
                onClick = { selectedFilter = "SECURED" },
                label = { Text("Secured") }
            )
            FilterChip(
                selected = selectedFilter == "OPEN",
                onClick = { selectedFilter = "OPEN" },
                label = { Text("Open") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Network List
        if (filteredNetworks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No networks match '$searchQuery'" else "No Wi-Fi Networks Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap refresh or verify Wi-Fi and Location are active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(onClick = onRefreshScan) {
                        Text("Rescan Networks")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredNetworks, key = { it.bssid.ifBlank { it.ssid + it.frequency } }) { network ->
                    val hasSaved = savedSsidSet.contains(network.ssid.lowercase())
                    NetworkListItem(
                        network = network,
                        hasSavedCredential = hasSaved,
                        onSelectNetwork = onSelectNetwork
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
