package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WifiManagerApp
import com.example.model.AuthorizedCredential
import com.example.model.Candidate
import com.example.model.ConnectedDevice
import com.example.model.CurrentWifiInfo
import com.example.model.ImportedEntry
import com.example.model.SuccessfulConnectionResult
import com.example.model.WifiConnectionSessionState
import com.example.model.WifiNetwork
import com.example.model.WifiSecurityType
import com.example.service.WifiMonitoringService
import com.example.util.PasswordFileParser
import com.example.wifi.AuthorizedRouterTestManager
import com.example.wifi.WifiConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WifiViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WifiManagerApp
    private val scannerManager = app.wifiScannerManager
    private val connector = app.wifiConnector
    val wifiConnectionRepository = app.wifiConnectionRepository
    private val credentialRepo = app.credentialRepository
    private val prefsRepo = app.preferencesRepository
    private val deviceScanner = app.networkDeviceScanner
    val routerTestManager = app.authorizedRouterTestManager
    val batchRepository = app.passwordBatchRepository

    // Password Batch & Background Streaming State
    val batchUiState: StateFlow<com.example.model.PasswordBatchUiState> = batchRepository.batchUiState

    // Wi-Fi Connection Lifecycle Session State
    val connectionSessionState: StateFlow<WifiConnectionSessionState> = wifiConnectionRepository.sessionState
    val successfulConnectionResult: StateFlow<SuccessfulConnectionResult?> = wifiConnectionRepository.successfulConnectionResult

    // Scanner & Networks
    private val _networks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val networks: StateFlow<List<WifiNetwork>> = _networks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _currentWifiInfo = MutableStateFlow(CurrentWifiInfo())
    val currentWifiInfo: StateFlow<CurrentWifiInfo> = _currentWifiInfo.asStateFlow()

    private val _isWifiEnabled = MutableStateFlow(true)
    val isWifiEnabled: StateFlow<Boolean> = _isWifiEnabled.asStateFlow()

    private val _isLocationEnabled = MutableStateFlow(true)
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()

    // Saved Credentials
    val savedCredentials: StateFlow<List<AuthorizedCredential>> = credentialRepo.allCredentials
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Connection
    val connectionState: StateFlow<WifiConnectionState> = connector.connectionState

    private val _selectedNetwork = MutableStateFlow<WifiNetwork?>(null)
    val selectedNetwork: StateFlow<WifiNetwork?> = _selectedNetwork.asStateFlow()

    // Import File Flow
    private val _importedEntries = MutableStateFlow<List<ImportedEntry>>(emptyList())
    val importedEntries: StateFlow<List<ImportedEntry>> = _importedEntries.asStateFlow()

    private val _importedPreview = MutableStateFlow<List<ImportedEntry>?>(null)
    val importedPreview: StateFlow<List<ImportedEntry>?> = _importedPreview.asStateFlow()

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    // Connected Devices Dashboard State
    private val _connectedDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ConnectedDevice>> = _connectedDevices.asStateFlow()

    private val _isScanningDevices = MutableStateFlow(false)
    val isScanningDevices: StateFlow<Boolean> = _isScanningDevices.asStateFlow()

    private val _deviceScanProgress = MutableStateFlow(0f)
    val deviceScanProgress: StateFlow<Float> = _deviceScanProgress.asStateFlow()

    private val _devicesLastUpdated = MutableStateFlow(System.currentTimeMillis())
    val devicesLastUpdated: StateFlow<Long> = _devicesLastUpdated.asStateFlow()

    private val _autoRefreshDevices = MutableStateFlow(false)
    val autoRefreshDevices: StateFlow<Boolean> = _autoRefreshDevices.asStateFlow()

    private var deviceScanJob: Job? = null
    private var autoRefreshJob: Job? = null

    // Authorized Router Test State
    val routerTestState: StateFlow<AuthorizedRouterTestManager.RouterTestState> = routerTestManager.testState

    // Settings
    val backgroundMonitoringEnabled: StateFlow<Boolean> = prefsRepo.backgroundMonitoring
    val resumeOnBootEnabled: StateFlow<Boolean> = prefsRepo.resumeOnBoot
    val themeMode: StateFlow<String> = prefsRepo.themeMode

    init {
        checkHardwareStates()
        refreshScan()
        observeScanResults()
        refreshConnectedDevices()
    }

    fun checkHardwareStates() {
        _isWifiEnabled.value = scannerManager.isWifiEnabled()
        _isLocationEnabled.value = scannerManager.isLocationEnabled()
        _currentWifiInfo.value = scannerManager.getCurrentWifiInfo()
    }

    private fun observeScanResults() {
        viewModelScope.launch {
            scannerManager.scanResultsFlow().collect { results ->
                val fallbackList = if (results.isEmpty()) getSampleNetworksIfEmpty() else results
                _networks.value = fallbackList
                _currentWifiInfo.value = scannerManager.getCurrentWifiInfo()
                _isScanning.value = false
            }
        }
    }

    fun refreshScan() {
        _isScanning.value = true
        checkHardwareStates()
        scannerManager.triggerScan()
        viewModelScope.launch {
            val updated = scannerManager.getProcessedScanResults()
            _networks.value = if (updated.isEmpty()) getSampleNetworksIfEmpty() else updated
            _currentWifiInfo.value = scannerManager.getCurrentWifiInfo()
            kotlinx.coroutines.delay(800)
            _isScanning.value = false
        }
    }

    fun selectNetwork(network: WifiNetwork?) {
        _selectedNetwork.value = network
    }

    fun connectCandidate(
        ssid: String,
        candidate: Candidate,
        securityType: WifiSecurityType,
        saveToVault: Boolean = false
    ) {
        viewModelScope.launch {
            if (securityType != WifiSecurityType.OPEN &&
                !candidate.source.equals("Imported TXT", ignoreCase = true) &&
                !candidate.source.equals("Manually Entered", ignoreCase = true)
            ) {
                return@launch
            }
            if (saveToVault && candidate.credential.isNotBlank()) {
                credentialRepo.saveCredential(
                    ssid = ssid,
                    plainPassword = candidate.credential,
                    securityType = securityType.name,
                    notes = "Saved during connection",
                    importedFrom = candidate.source
                )
            }
            wifiConnectionRepository.connectCandidate(ssid, candidate, securityType)
        }
    }

    fun connectWithCandidates(
        ssid: String,
        securityType: WifiSecurityType,
        candidates: List<String>
    ) {
        wifiConnectionRepository.connectWithCandidates(ssid, securityType, candidates)
    }

    fun cancelConnection() {
        wifiConnectionRepository.cancel()
        connector.resetState()
    }

    fun dismissConnectModal() {
        _selectedNetwork.value = null
        wifiConnectionRepository.reset()
        connector.resetState()
    }

    fun openWifiSettings() {
        connector.openWifiSettings()
    }

    // Connected Devices Scanner
    fun refreshConnectedDevices() {
        if (_isScanningDevices.value) return
        deviceScanJob?.cancel()

        deviceScanJob = viewModelScope.launch {
            _isScanningDevices.value = true
            _deviceScanProgress.value = 0f

            deviceScanner.scanLocalSubnetFlow().collect { progress ->
                _connectedDevices.value = progress.discoveredDevices
                if (progress.totalCount > 0) {
                    _deviceScanProgress.value = progress.scannedCount.toFloat() / progress.totalCount
                }
                if (progress.isFinished) {
                    _isScanningDevices.value = false
                    _devicesLastUpdated.value = System.currentTimeMillis()
                }
            }
        }
    }

    fun toggleAutoRefreshDevices(enabled: Boolean) {
        _autoRefreshDevices.value = enabled
        autoRefreshJob?.cancel()
        if (enabled) {
            autoRefreshJob = viewModelScope.launch {
                while (isActive) {
                    delay(30000) // 30 seconds interval
                    if (!_isScanningDevices.value && _currentWifiInfo.value.isConnected) {
                        refreshConnectedDevices()
                    }
                }
            }
        }
    }

    // Authorized Router Test Workflow
    fun startAuthorizedRouterTest(
        targetSsid: String,
        securityType: WifiSecurityType,
        candidatePasswords: List<String>
    ) {
        routerTestManager.startTest(targetSsid, securityType, candidatePasswords)
    }

    fun stopAuthorizedRouterTest() {
        routerTestManager.stopTest()
    }

    fun resetAuthorizedRouterTest() {
        routerTestManager.resetState()
    }

    // Password File Import & Background Processing
    fun handleFileImport(uri: Uri) {
        try {
            app.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        var fileName = "passwords.txt"
        try {
            app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (col != -1) {
                        fileName = cursor.getString(col) ?: "passwords.txt"
                    }
                }
            }
        } catch (_: Exception) {}

        // Launch Foreground Service for reliable background file processing
        com.example.service.FileProcessingService.startImport(app, uri, fileName)

        // Also parse first few lines for backwards compatibility with small key-value lists
        viewModelScope.launch(Dispatchers.IO) {
            val result = PasswordFileParser.parseUri(app, uri)
            withContext(Dispatchers.Main) {
                if (result is PasswordFileParser.ParseResult.Success && result.entries.isNotEmpty() && result.entries.size <= 2000) {
                    val combined = (_importedEntries.value + result.entries)
                    _importedEntries.value = combined
                }
            }
        }
    }

    fun pauseBackgroundImport() {
        com.example.service.FileProcessingService.pauseImport(app)
    }

    fun resumeBackgroundImport() {
        val checkpoint = prefsRepo.getImportCheckpoint()
        val uriStr = checkpoint?.uriString ?: batchUiState.value.fileUri
        val fileName = checkpoint?.fileName ?: batchUiState.value.fileName.ifBlank { "passwords.txt" }
        if (uriStr.isNotBlank()) {
            com.example.service.FileProcessingService.resumeImport(
                app,
                android.net.Uri.parse(uriStr),
                fileName
            )
        }
    }

    fun stopBackgroundImport() {
        com.example.service.FileProcessingService.stopImport(app)
    }

    fun loadBatch(batchNumber: Int) {
        viewModelScope.launch {
            batchRepository.loadBatch(batchNumber)
        }
    }

    fun searchBatchStreaming(query: String) {
        viewModelScope.launch {
            batchRepository.searchStreaming(query)
        }
    }

    fun clearBatchSearch() {
        batchRepository.clearSearch()
    }

    fun selectBatchEntry(entry: com.example.model.BatchPasswordEntry?) {
        batchRepository.selectEntry(entry)
    }

    fun clearBatchData() {
        batchRepository.clearAllData()
    }

    fun dismissBatchInfoMessage() {
        batchRepository.dismissInfoMessage()
    }

    fun dismissBatchErrorMessage() {
        batchRepository.dismissErrorMessage()
    }

    fun updateImportedEntry(id: String, newSsid: String, newPassword: String) {
        _importedEntries.value = _importedEntries.value.map { entry ->
            if (entry.id == id) entry.copy(ssid = newSsid.trim(), password = newPassword) else entry
        }
    }

    fun deleteImportedEntry(id: String) {
        _importedEntries.value = _importedEntries.value.filter { it.id != id }
    }

    fun clearAllImportedEntries() {
        _importedEntries.value = emptyList()
    }

    fun updateSavedCredential(id: Long, newSsid: String, newPassword: String, notes: String = "") {
        viewModelScope.launch {
            credentialRepo.updateCredential(id, newSsid, newPassword, notes)
        }
    }

    fun dismissImportPreview() {
        _importedPreview.value = null
    }

    fun clearImportMessage() {
        _importMessage.value = null
    }

    fun saveImportedEntries(entries: List<ImportedEntry>) {
        viewModelScope.launch {
            val pairs = entries.map { it.ssid to it.password }
            credentialRepo.saveBatch(pairs, "Imported File")
            _importedPreview.value = null
            _importMessage.value = "Successfully saved ${entries.size} credentials to Authorized Vault."
        }
    }

    // Vault Actions
    fun getDecryptedPassword(credential: AuthorizedCredential): String {
        return credentialRepo.decryptPassword(credential)
    }

    suspend fun findPasswordForSsid(ssid: String): String? {
        val cred = credentialRepo.getCredentialForSsid(ssid) ?: return null
        return credentialRepo.decryptPassword(cred)
    }

    fun deleteCredential(id: Long) {
        viewModelScope.launch {
            credentialRepo.deleteCredential(id)
        }
    }

    fun clearAllCredentials() {
        viewModelScope.launch {
            credentialRepo.clearAll()
        }
    }


    // Settings
    fun toggleBackgroundMonitoring(enabled: Boolean) {
        prefsRepo.setBackgroundMonitoring(enabled)
        if (enabled) {
            WifiMonitoringService.start(app)
        } else {
            WifiMonitoringService.stop(app)
        }
    }

    fun toggleResumeOnBoot(enabled: Boolean) {
        prefsRepo.setResumeOnBoot(enabled)
    }

    fun setThemeMode(mode: String) {
        prefsRepo.setThemeMode(mode)
    }

    private fun getSampleNetworksIfEmpty(): List<WifiNetwork> {
        return listOf(
            WifiNetwork(
                ssid = "SkyNet_Ultra_5G",
                bssid = "00:11:22:33:44:55",
                capabilities = "[WPA3-SAE-CCMP][ESS]",
                securityType = WifiSecurityType.WPA3_SAE,
                level = -45,
                signalLevel = 4,
                frequency = 5180,
                bandLabel = "5 GHz",
                channel = 36,
                isConnected = false
            ),
            WifiNetwork(
                ssid = "Office_Secure_WiFi",
                bssid = "AA:BB:CC:DD:EE:FF",
                capabilities = "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]",
                securityType = WifiSecurityType.WPA2_PSK,
                level = -62,
                signalLevel = 3,
                frequency = 2437,
                bandLabel = "2.4 GHz",
                channel = 6,
                isConnected = false
            ),
            WifiNetwork(
                ssid = "CoffeeHouse_Guest_Free",
                bssid = "11:22:33:44:55:66",
                capabilities = "[ESS]",
                securityType = WifiSecurityType.OPEN,
                level = -70,
                signalLevel = 2,
                frequency = 2412,
                bandLabel = "2.4 GHz",
                channel = 1,
                isConnected = false
            ),
            WifiNetwork(
                ssid = "Home_Fiber_Pro",
                bssid = "99:88:77:66:55:44",
                capabilities = "[WPA2-PSK-CCMP][WPA3-SAE][ESS]",
                securityType = WifiSecurityType.WPA3_SAE,
                level = -55,
                signalLevel = 4,
                frequency = 5745,
                bandLabel = "5 GHz",
                channel = 149,
                isConnected = false
            ),
            WifiNetwork(
                ssid = "Metro_Express_Pass",
                bssid = "55:66:77:88:99:00",
                capabilities = "[WPA-PSK-TKIP][WPA2-PSK-CCMP][ESS]",
                securityType = WifiSecurityType.WPA_PSK,
                level = -82,
                signalLevel = 1,
                frequency = 2462,
                bandLabel = "2.4 GHz",
                channel = 11,
                isConnected = false
            )
        )
    }
}
