package com.example.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.example.model.CurrentWifiInfo
import com.example.model.WifiNetwork
import com.example.model.WifiSecurityType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WifiScannerManager(private val context: Context) {

    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val locationManager: LocationManager? =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun isWifiEnabled(): Boolean {
        return wifiManager?.isWifiEnabled == true
    }

    fun isLocationEnabled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager?.isLocationEnabled == true
            } else {
                locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                        locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            }
        } catch (_: Exception) {
            true
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun triggerScan(): Boolean {
        return try {
            wifiManager?.startScan() ?: false
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun scanResultsFlow(): Flow<List<WifiNetwork>> = callbackFlow {
        // Emit current scan results initially if available
        trySend(getProcessedScanResults())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    trySend(getProcessedScanResults())
                }
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (_: Exception) {
            try {
                context.registerReceiver(receiver, filter)
            } catch (_: Exception) {
                // Ignore register receiver failure
            }
        }

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
                // Receiver might already be unregistered
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun getProcessedScanResults(): List<WifiNetwork> {
        val wm = wifiManager ?: return emptyList()
        val rawResults: List<ScanResult> = try {
            wm.scanResults ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val currentInfo = getCurrentWifiInfo()
        val currentSsid = currentInfo.ssid.replace("\"", "")

        // Group by SSID to avoid duplicates, keeping the one with best RSSI
        val grouped = rawResults
            .filter { it.SSID != null && it.SSID.isNotBlank() }
            .groupBy { it.SSID.trim().replace("\"", "") }

        val networkList = grouped.map { (ssid, scanList) ->
            val best = scanList.maxByOrNull { it.level } ?: scanList.first()
            val security = WifiSecurityType.fromCapabilities(best.capabilities)
            val signalBars = WifiManager.calculateSignalLevel(best.level, 5).coerceIn(0, 4)
            val band = WifiNetwork.calculateBand(best.frequency)
            val channel = WifiNetwork.calculateChannel(best.frequency)
            val isConnected = currentInfo.isConnected && currentSsid.equals(ssid, ignoreCase = true)

            WifiNetwork(
                ssid = ssid,
                bssid = best.BSSID ?: "",
                capabilities = best.capabilities ?: "",
                securityType = security,
                level = best.level,
                signalLevel = signalBars,
                frequency = best.frequency,
                bandLabel = band,
                channel = channel,
                isConnected = isConnected,
                timestamp = best.timestamp
            )
        }.sortedWith(
            compareByDescending<WifiNetwork> { it.isConnected }
                .thenByDescending { it.level }
        )

        return networkList
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun getCurrentWifiInfo(): CurrentWifiInfo {
        val cm = connectivityManager ?: return CurrentWifiInfo()
        val wm = wifiManager ?: return CurrentWifiInfo()

        val activeNetwork: Network? = cm.activeNetwork
        val caps: NetworkCapabilities? = activeNetwork?.let { cm.getNetworkCapabilities(it) }

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!isWifi) {
            return CurrentWifiInfo(isConnected = false)
        }

        var ssid = ""
        var bssid = ""
        var rssi = 0
        var linkSpeed = 0
        var freq = 0

        // Extract WifiInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiInfo = caps?.transportInfo as? WifiInfo
            if (wifiInfo != null) {
                ssid = wifiInfo.ssid ?: ""
                bssid = wifiInfo.bssid ?: ""
                rssi = wifiInfo.rssi
                linkSpeed = wifiInfo.linkSpeed
                freq = wifiInfo.frequency
            }
        }

        // Fallback to connectionInfo
        if (ssid.isBlank() || ssid == "<unknown ssid>") {
            try {
                val connInfo = wm.connectionInfo
                if (connInfo != null) {
                    if (ssid.isBlank() || ssid == "<unknown ssid>") ssid = connInfo.ssid ?: ""
                    if (bssid.isBlank()) bssid = connInfo.bssid ?: ""
                    if (rssi == 0) rssi = connInfo.rssi
                    if (linkSpeed == 0) linkSpeed = connInfo.linkSpeed
                    if (freq == 0) freq = connInfo.frequency
                }
            } catch (_: Exception) {}
        }

        ssid = ssid.replace("\"", "")
        if (ssid == "<unknown ssid>") ssid = "Connected Wi-Fi"

        val dhcp = try { wm.dhcpInfo } catch (_: Exception) { null }
        val ipStr = dhcp?.let { intToIp(it.ipAddress) } ?: ""
        val gatewayStr = dhcp?.let { intToIp(it.gateway) } ?: ""
        val dnsStr = dhcp?.let { intToIp(it.dns1) } ?: ""
        val netmaskStr = dhcp?.let { intToIp(it.netmask) } ?: ""
        val band = if (freq > 0) WifiNetwork.calculateBand(freq) else "Wi-Fi"

        return CurrentWifiInfo(
            isConnected = true,
            ssid = ssid,
            bssid = bssid,
            ipAddress = ipStr,
            linkSpeedMbps = linkSpeed,
            rssi = rssi,
            frequency = freq,
            band = band,
            gateway = gatewayStr,
            dns = dnsStr,
            netmask = netmaskStr
        )
    }

    private fun intToIp(ip: Int): String {
        return try {
            val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ip).array()
            InetAddress.getByAddress(bytes).hostAddress ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
