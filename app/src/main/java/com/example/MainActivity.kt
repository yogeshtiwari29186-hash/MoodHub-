package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.ConnectModal
import com.example.ui.components.ImportPreviewDialog
import com.example.ui.screens.CredentialsScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.NearbyWifiScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.WifiManagerTheme
import com.example.ui.viewmodel.WifiViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: WifiViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            WifiManagerTheme(themePreference = themeMode) {
                WifiManagerAppRoot(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiManagerAppRoot(viewModel: WifiViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }

    // State collections
    val networks by viewModel.networks.collectAsStateWithLifecycle()
    val currentInfo by viewModel.currentWifiInfo.collectAsStateWithLifecycle()
    val savedCredentials by viewModel.savedCredentials.collectAsStateWithLifecycle()
    val importedEntries by viewModel.importedEntries.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isWifiEnabled by viewModel.isWifiEnabled.collectAsStateWithLifecycle()
    val isLocationEnabled by viewModel.isLocationEnabled.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val selectedNetwork by viewModel.selectedNetwork.collectAsStateWithLifecycle()
    val importedPreview by viewModel.importedPreview.collectAsStateWithLifecycle()
    val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()

    val backgroundMonitoringEnabled by viewModel.backgroundMonitoringEnabled.collectAsStateWithLifecycle()
    val resumeOnBootEnabled by viewModel.resumeOnBootEnabled.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    // Permissions check
    fun checkPermissionsGranted(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return fineLocation || nearbyGranted
    }

    var hasPermissions by remember { mutableStateOf(checkPermissionsGranted()) }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    // Permission Launchers
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermissions = checkPermissionsGranted()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = result[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPermission
        }
        viewModel.refreshScan()
    }

    fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    // Document Picker Launcher for TXT / CSV file import
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.handleFileImport(it) }
    }

    fun openDocumentPicker() {
        documentPickerLauncher.launch(
            arrayOf(
                "text/plain",
                "text/csv",
                "text/comma-separated-values",
                "application/csv",
                "application/vnd.ms-excel",
                "*/*"
            )
        )
    }

    // Snackbar notifications
    LaunchedEffect(importMessage) {
        importMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearImportMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "WiFi Manager"
                            1 -> "Password List"
                            2 -> "Diagnostics"
                            else -> "Settings"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.Wifi else Icons.Outlined.Wifi,
                            contentDescription = "Nearby Wi-Fi"
                        )
                    },
                    label = { Text("Nearby") },
                    modifier = Modifier.testTag("nav_tab_nearby")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Filled.Key else Icons.Outlined.Key,
                            contentDescription = "Password List"
                        )
                    },
                    label = { Text("Passwords") },
                    modifier = Modifier.testTag("nav_tab_passwords")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Filled.NetworkCheck else Icons.Outlined.NetworkCheck,
                            contentDescription = "Diagnostics"
                        )
                    },
                    label = { Text("Diagnostics") },
                    modifier = Modifier.testTag("nav_tab_diagnostics")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> NearbyWifiScreen(
                    networks = networks,
                    currentInfo = currentInfo,
                    savedCredentials = savedCredentials,
                    isScanning = isScanning,
                    isWifiEnabled = isWifiEnabled,
                    isLocationEnabled = isLocationEnabled,
                    hasPermissions = hasPermissions,
                    backgroundMonitoringEnabled = backgroundMonitoringEnabled,
                    onToggleBackgroundMonitoring = { enabled -> viewModel.toggleBackgroundMonitoring(enabled) },
                    onRequestPermissions = { requestRequiredPermissions() },
                    onRefreshScan = { viewModel.refreshScan() },
                    onSelectNetwork = { network -> viewModel.selectNetwork(network) },
                    onImportFileClick = { openDocumentPicker() },
                    onOpenWifiSettings = { viewModel.openWifiSettings() }
                )
                1 -> CredentialsScreen(
                    credentials = savedCredentials,
                    importedEntries = importedEntries,
                    nearbyNetworks = networks,
                    onImportFileClick = { openDocumentPicker() },
                    onDecryptPassword = { cred -> viewModel.getDecryptedPassword(cred) },
                    onDeleteCredential = { id -> viewModel.deleteCredential(id) },
                    onUpdateCredential = { id, ssid, pass, notes -> viewModel.updateSavedCredential(id, ssid, pass, notes) },
                    onClearAllCredentials = { viewModel.clearAllCredentials() },
                    onEditImportedEntry = { id, ssid, pass -> viewModel.updateImportedEntry(id, ssid, pass) },
                    onDeleteImportedEntry = { id -> viewModel.deleteImportedEntry(id) },
                    onClearAllImported = { viewModel.clearAllImportedEntries() },
                    onManualAdd = { ssid, pass, notes -> viewModel.saveManualCredential(ssid, pass, notes) },
                    onSelectToConnect = { targetSsid, pass ->
                        val foundNet = networks.find { it.ssid.equals(targetSsid, ignoreCase = true) }
                            ?: com.example.model.WifiNetwork(
                                ssid = targetSsid,
                                bssid = "",
                                capabilities = "[WPA2-PSK-CCMP]",
                                securityType = com.example.model.WifiSecurityType.WPA2_PSK,
                                level = -60,
                                signalLevel = 3,
                                frequency = 2437,
                                bandLabel = "2.4 GHz",
                                channel = 6
                            )
                        viewModel.selectNetwork(foundNet)
                    }
                )
                2 -> DiagnosticsScreen(
                    currentInfo = currentInfo,
                    nearbyNetworks = networks,
                    onOpenWifiSettings = { viewModel.openWifiSettings() }
                )
                3 -> SettingsScreen(
                    backgroundMonitoringEnabled = backgroundMonitoringEnabled,
                    resumeOnBootEnabled = resumeOnBootEnabled,
                    themeMode = themeMode,
                    hasNotificationPermission = hasNotificationPermission,
                    onToggleBackgroundMonitoring = { enabled -> viewModel.toggleBackgroundMonitoring(enabled) },
                    onToggleResumeOnBoot = { enabled -> viewModel.toggleResumeOnBoot(enabled) },
                    onSetThemeMode = { mode -> viewModel.setThemeMode(mode) },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        }
                    },
                    onClearAllData = {
                        viewModel.clearAllCredentials()
                        viewModel.clearAllImportedEntries()
                        viewModel.toggleBackgroundMonitoring(false)
                        viewModel.toggleResumeOnBoot(false)
                    }
                )
            }

            // Connection Modal Dialog
            selectedNetwork?.let { network ->
                var matchedPassword by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(network.ssid) {
                    matchedPassword = viewModel.findPasswordForSsid(network.ssid)
                }

                ConnectModal(
                    network = network,
                    matchedPassword = matchedPassword,
                    importedEntries = importedEntries,
                    connectionState = connectionState,
                    onImportFileClick = { openDocumentPicker() },
                    onEditImportedEntry = { id, ssid, pass -> viewModel.updateImportedEntry(id, ssid, pass) },
                    onDeleteImportedEntry = { id -> viewModel.deleteImportedEntry(id) },
                    onConnect = { ssid, password, securityType, saveToVault ->
                        viewModel.connectToNetwork(ssid, password, securityType, saveToVault)
                    },
                    onOpenSettings = { viewModel.openWifiSettings() },
                    onDismiss = { viewModel.dismissConnectModal() }
                )
            }

            // Import Preview Dialog
            importedPreview?.let { entries ->
                ImportPreviewDialog(
                    entries = entries,
                    nearbyNetworks = networks,
                    onSaveToVault = { validEntries ->
                        viewModel.saveImportedEntries(validEntries)
                    },
                    onDismiss = { viewModel.dismissImportPreview() }
                )
            }
        }
    }
}
