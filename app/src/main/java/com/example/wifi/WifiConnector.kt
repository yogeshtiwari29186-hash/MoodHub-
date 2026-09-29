package com.example.wifi

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.provider.Settings
import com.example.model.WifiSecurityType
import com.example.util.SafeWifiLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address

sealed class WifiConnectionState {
    object Idle : WifiConnectionState()
    data class Connecting(
        val ssid: String,
        val message: String = "Negotiating connection...",
        val candidateIndex: Int = 1,
        val totalCandidates: Int = 1,
        val candidateMasked: String = ""
    ) : WifiConnectionState()
    data class Connected(
        val ssid: String,
        val message: String = "Successfully connected!",
        val ipAddress: String? = null,
        val gateway: String? = null
    ) : WifiConnectionState()
    data class Failed(val ssid: String, val reason: String, val canOpenSettings: Boolean = true) : WifiConnectionState()
    data class Cancelled(val ssid: String, val message: String = "Connection cancelled") : WifiConnectionState()
}

data class NetworkVerificationResult(
    val isVerified: Boolean,
    val ipAddress: String?,
    val gateway: String?,
    val linkSpeedMbps: Int? = null,
    val details: String = ""
)

sealed class CandidateConnectionOutcome {
    data class Success(val ipAddress: String?, val gateway: String?, val linkSpeedMbps: Int?) : CandidateConnectionOutcome()
    data class Failed(val reason: String, val canOpenSettings: Boolean = true) : CandidateConnectionOutcome()
    object Cancelled : CandidateConnectionOutcome()
}

class WifiConnector(private val context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _connectionState = MutableStateFlow<WifiConnectionState>(WifiConnectionState.Idle)
    val connectionState: StateFlow<WifiConnectionState> = _connectionState.asStateFlow()

    private var activeCallback: ConnectivityManager.NetworkCallback? = null
    private var activeNetwork: Network? = null
    private var timeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Legacy asynchronous single-connect interface for backwards compatibility.
     */
    fun connect(
        ssid: String,
        password: String,
        securityType: WifiSecurityType
    ) {
        val cm = connectivityManager
        if (cm == null) {
            _connectionState.value = WifiConnectionState.Failed(
                ssid = ssid,
                reason = "Connectivity Service unavailable on device."
            )
            return
        }

        disconnectCurrent()
        _connectionState.value = WifiConnectionState.Connecting(
            ssid = ssid,
            message = "Requesting system connection to $ssid...",
            candidateMasked = SafeWifiLogger.mask(password)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectViaSpecifier(ssid, password, securityType)
        } else {
            // Android 9 and below
            _connectionState.value = WifiConnectionState.Connecting(
                ssid = ssid,
                message = "Connecting via system settings..."
            )
            openWifiSettings()
            _connectionState.value = WifiConnectionState.Failed(
                ssid = ssid,
                reason = "Android 9 and below requires system Wi-Fi panel. Settings opened.",
                canOpenSettings = true
            )
        }
    }

    /**
     * Synchronous candidate connect with strict timeout, verification, and cleanup.
     * Designed for the WiFiConnectionRepository sequential state machine.
     */
    suspend fun connectCandidateSync(
        ssid: String,
        password: String,
        securityType: WifiSecurityType,
        candidateIndex: Int,
        totalCandidates: Int,
        timeoutMs: Long = 15_000L
    ): CandidateConnectionOutcome {
        val cm = connectivityManager
            ?: return CandidateConnectionOutcome.Failed("Connectivity Service unavailable on device.")

        disconnectCurrent()

        val masked = SafeWifiLogger.mask(password)
        SafeWifiLogger.d(
            "WifiConnector",
            "Starting candidate connection: SSID='$ssid', candidate $candidateIndex of $totalCandidates, masked='$masked'"
        )

        _connectionState.value = WifiConnectionState.Connecting(
            ssid = ssid,
            message = "Negotiating candidate $candidateIndex of $totalCandidates...",
            candidateIndex = candidateIndex,
            totalCandidates = totalCandidates,
            candidateMasked = masked
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            openWifiSettings()
            _connectionState.value = WifiConnectionState.Failed(
                ssid = ssid,
                reason = "Android 9 and below requires system Wi-Fi panel.",
                canOpenSettings = true
            )
            return CandidateConnectionOutcome.Failed("Requires system Wi-Fi panel on older Android.", canOpenSettings = true)
        }

        val completionDeferred = CompletableDeferred<CandidateConnectionOutcome>()

        try {
            val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid.trim())
            when (securityType) {
                WifiSecurityType.OPEN -> {
                    // Open network
                }
                WifiSecurityType.WPA3_SAE -> {
                    if (password.isNotEmpty()) {
                        specifierBuilder.setWpa3Passphrase(password)
                    }
                }
                else -> {
                    if (password.isNotEmpty()) {
                        specifierBuilder.setWpa2Passphrase(password)
                    }
                }
            }

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifierBuilder.build())
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    SafeWifiLogger.d("WifiConnector", "Network onAvailable for candidate $candidateIndex")
                    activeNetwork = network
                    cm.bindProcessToNetwork(network)

                    scope.launch(Dispatchers.IO) {
                        val verification = verifyNetwork(network, timeoutMs = 4000L)
                        if (verification.isVerified) {
                            SafeWifiLogger.i(
                                "WifiConnector",
                                "Candidate $candidateIndex successfully verified. IP=${verification.ipAddress}, Gateway=${verification.gateway}"
                            )
                            _connectionState.value = WifiConnectionState.Connected(
                                ssid = ssid,
                                message = "Connected to $ssid (${verification.ipAddress ?: "Assigned"})",
                                ipAddress = verification.ipAddress,
                                gateway = verification.gateway
                            )
                            completionDeferred.complete(
                                CandidateConnectionOutcome.Success(
                                    ipAddress = verification.ipAddress,
                                    gateway = verification.gateway,
                                    linkSpeedMbps = verification.linkSpeedMbps
                                )
                            )
                        } else {
                            SafeWifiLogger.w("WifiConnector", "Verification failed for candidate $candidateIndex: ${verification.details}")
                            completionDeferred.complete(
                                CandidateConnectionOutcome.Failed("Network connected but failed IP/Gateway verification: ${verification.details}")
                            )
                        }
                    }
                }

                override fun onUnavailable() {
                    SafeWifiLogger.d("WifiConnector", "Network onUnavailable for candidate $candidateIndex")
                    completionDeferred.complete(
                        CandidateConnectionOutcome.Failed("Network prompt dismissed or candidate rejected by access point.")
                    )
                }

                override fun onLost(network: Network) {
                    SafeWifiLogger.d("WifiConnector", "Network onLost for candidate $candidateIndex")
                    if (!completionDeferred.isCompleted) {
                        completionDeferred.complete(CandidateConnectionOutcome.Failed("Wi-Fi link was lost during negotiation."))
                    }
                }
            }

            activeCallback = callback
            cm.requestNetwork(request, callback)

            val outcome = withTimeoutOrNull(timeoutMs) {
                completionDeferred.await()
            }

            return outcome ?: run {
                SafeWifiLogger.d("WifiConnector", "Candidate $candidateIndex timed out after ${timeoutMs}ms")
                disconnectCurrent()
                CandidateConnectionOutcome.Failed("Connection timed out waiting for access point handshake.")
            }

        } catch (e: Exception) {
            SafeWifiLogger.e("WifiConnector", "Exception connecting candidate $candidateIndex", e)
            disconnectCurrent()
            return CandidateConnectionOutcome.Failed("Network configuration error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun connectViaSpecifier(
        ssid: String,
        password: String,
        securityType: WifiSecurityType
    ) {
        val cm = connectivityManager ?: return

        try {
            val specifierBuilder = WifiNetworkSpecifier.Builder()
                .setSsid(ssid.trim())

            when (securityType) {
                WifiSecurityType.OPEN -> {}
                WifiSecurityType.WPA3_SAE -> {
                    if (password.isNotEmpty()) specifierBuilder.setWpa3Passphrase(password)
                }
                else -> {
                    if (password.isNotEmpty()) specifierBuilder.setWpa2Passphrase(password)
                }
            }

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifierBuilder.build())
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    timeoutJob?.cancel()
                    activeNetwork = network
                    cm.bindProcessToNetwork(network)

                    scope.launch(Dispatchers.IO) {
                        val verification = verifyNetwork(network, timeoutMs = 4000L)
                        _connectionState.value = WifiConnectionState.Connected(
                            ssid = ssid,
                            message = "Connected to $ssid",
                            ipAddress = verification.ipAddress,
                            gateway = verification.gateway
                        )
                    }
                }

                override fun onUnavailable() {
                    timeoutJob?.cancel()
                    _connectionState.value = WifiConnectionState.Failed(
                        ssid = ssid,
                        reason = "Connection prompt was dismissed or network is unreachable.",
                        canOpenSettings = true
                    )
                }

                override fun onLost(network: Network) {
                    _connectionState.value = WifiConnectionState.Failed(
                        ssid = ssid,
                        reason = "Connection lost to $ssid.",
                        canOpenSettings = false
                    )
                }
            }

            activeCallback = callback
            cm.requestNetwork(request, callback)

            // Setup timeout
            timeoutJob = scope.launch {
                delay(45_000)
                if (_connectionState.value is WifiConnectionState.Connecting) {
                    _connectionState.value = WifiConnectionState.Failed(
                        ssid = ssid,
                        reason = "Connection timed out. Check password or signal range.",
                        canOpenSettings = true
                    )
                    disconnectCurrent()
                }
            }

        } catch (e: Exception) {
            _connectionState.value = WifiConnectionState.Failed(
                ssid = ssid,
                reason = "Error configuring network: ${e.localizedMessage ?: "Unknown error"}",
                canOpenSettings = true
            )
        }
    }

    /**
     * Verifies that the connected network is genuinely operational by confirming
     * Wi-Fi transport capability and polling for valid IPv4 and default Gateway assignment.
     */
    suspend fun verifyNetwork(network: Network, timeoutMs: Long = 4000L): NetworkVerificationResult {
        val cm = connectivityManager
        if (cm == null) {
            return NetworkVerificationResult(
                isVerified = false,
                ipAddress = null,
                gateway = null,
                details = "ConnectivityManager unavailable"
            )
        }

        val startTime = System.currentTimeMillis()
        var resolvedIp: String? = null
        var resolvedGateway: String? = null
        var linkSpeed: Int? = null

        // Poll LinkProperties to allow DHCP handshake to finish
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val caps = cm.getNetworkCapabilities(network)
            val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            val linkProperties = cm.getLinkProperties(network)
            if (linkProperties != null) {
                resolvedIp = extractIpv4(linkProperties)
                resolvedGateway = extractGateway(linkProperties)
            }

            // Fallback to WifiInfo / dhcpInfo if needed
            if (resolvedIp == null || resolvedGateway == null) {
                val dhcp = wifiManager?.dhcpInfo
                if (dhcp != null) {
                    if (resolvedIp == null && dhcp.ipAddress != 0) {
                        resolvedIp = formatIntIp(dhcp.ipAddress)
                    }
                    if (resolvedGateway == null && dhcp.gateway != 0) {
                        resolvedGateway = formatIntIp(dhcp.gateway)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && caps != null) {
                val transportInfo = caps.transportInfo
                if (transportInfo is WifiInfo) {
                    linkSpeed = transportInfo.linkSpeed
                }
            }

            if (hasWifi && (!resolvedIp.isNullOrBlank() || !resolvedGateway.isNullOrBlank())) {
                return NetworkVerificationResult(
                    isVerified = true,
                    ipAddress = resolvedIp,
                    gateway = resolvedGateway,
                    linkSpeedMbps = linkSpeed,
                    details = "Verified Wi-Fi transport, IP=$resolvedIp, Gateway=$resolvedGateway"
                )
            }

            delay(200)
        }

        // Even if gateway wasn't found, if we have an IP or Wi-Fi transport confirmed
        val finalCaps = cm.getNetworkCapabilities(network)
        val isWifi = finalCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        return NetworkVerificationResult(
            isVerified = isWifi,
            ipAddress = resolvedIp,
            gateway = resolvedGateway,
            linkSpeedMbps = linkSpeed,
            details = if (isWifi) "Connected to Wi-Fi transport (DHCP pending)" else "Network lacks Wi-Fi transport"
        )
    }

    private fun extractIpv4(linkProperties: LinkProperties): String? {
        return linkProperties.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }

    private fun extractGateway(linkProperties: LinkProperties): String? {
        return linkProperties.routes
            .firstOrNull { it.isDefaultRoute && it.gateway != null }
            ?.gateway
            ?.hostAddress
    }

    private fun formatIntIp(ip: Int): String? {
        if (ip == 0) return null
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }

    /**
     * Mandatory cleanup process that unbinds the process, unregisters network callbacks,
     * and cancels any pending timeouts.
     */
    fun disconnectCurrent() {
        timeoutJob?.cancel()
        timeoutJob = null
        val cm = connectivityManager
        val cb = activeCallback
        if (cm != null && cb != null) {
            try {
                cm.bindProcessToNetwork(null)
                cm.unregisterNetworkCallback(cb)
            } catch (_: Exception) {}
        }
        activeCallback = null
        activeNetwork = null
        SafeWifiLogger.d("WifiConnector", "Cleanup completed: unbound process and unregistered callback.")
    }

    fun resetState() {
        disconnectCurrent()
        _connectionState.value = WifiConnectionState.Idle
    }

    fun cancelConnection(ssid: String = "") {
        disconnectCurrent()
        _connectionState.value = WifiConnectionState.Cancelled(
            ssid = ssid,
            message = "Connection cancelled by user"
        )
    }

    fun openWifiSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
