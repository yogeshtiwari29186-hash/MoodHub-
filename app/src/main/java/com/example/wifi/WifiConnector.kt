package com.example.wifi

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.provider.Settings
import com.example.model.WifiSecurityType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class WifiConnectionState {
    object Idle : WifiConnectionState()
    data class Connecting(val ssid: String, val message: String = "Negotiating connection...") : WifiConnectionState()
    data class Connected(val ssid: String, val message: String = "Successfully connected!") : WifiConnectionState()
    data class Failed(val ssid: String, val reason: String, val canOpenSettings: Boolean = true) : WifiConnectionState()
}

class WifiConnector(private val context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _connectionState = MutableStateFlow<WifiConnectionState>(WifiConnectionState.Idle)
    val connectionState: StateFlow<WifiConnectionState> = _connectionState.asStateFlow()

    private var activeCallback: ConnectivityManager.NetworkCallback? = null
    private var timeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

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
            message = "Requesting system connection to $ssid..."
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
                WifiSecurityType.OPEN -> {
                    // Open network
                }
                WifiSecurityType.WPA3_SAE -> {
                    if (password.isNotEmpty()) {
                        specifierBuilder.setWpa3Passphrase(password)
                    }
                }
                else -> {
                    // WPA / WPA2
                    if (password.isNotEmpty()) {
                        specifierBuilder.setWpa2Passphrase(password)
                    }
                }
            }

            val specifier = specifierBuilder.build()
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    timeoutJob?.cancel()
                    cm.bindProcessToNetwork(network)
                    _connectionState.value = WifiConnectionState.Connected(
                        ssid = ssid,
                        message = "Connected to $ssid."
                    )
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

            // Setup a 45-second timeout for the user approval dialog and handshake
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
    }

    fun resetState() {
        disconnectCurrent()
        _connectionState.value = WifiConnectionState.Idle
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
