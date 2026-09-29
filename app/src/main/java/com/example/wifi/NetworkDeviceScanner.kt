package com.example.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import com.example.model.ConnectedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class NetworkDeviceScanner(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    data class ScanProgress(
        val scannedCount: Int,
        val totalCount: Int,
        val discoveredDevices: List<ConnectedDevice>,
        val isFinished: Boolean,
        val errorMessage: String? = null
    )

    fun scanLocalSubnetFlow(): Flow<ScanProgress> = flow {
        val (localIp, gatewayIp) = getNetworkAddresses()
        if (localIp.isBlank()) {
            emit(
                ScanProgress(
                    scannedCount = 0,
                    totalCount = 0,
                    discoveredDevices = emptyList(),
                    isFinished = true,
                    errorMessage = "Not connected to Wi-Fi. Connect to a network to discover local devices."
                )
            )
            return@flow
        }

        val ipParts = localIp.split(".")
        if (ipParts.size != 4) {
            emit(
                ScanProgress(
                    scannedCount = 0,
                    totalCount = 0,
                    discoveredDevices = emptyList(),
                    isFinished = true,
                    errorMessage = "Invalid local IP address ($localIp)"
                )
            )
            return@flow
        }

        val subnetPrefix = "${ipParts[0]}.${ipParts[1]}.${ipParts[2]}"
        val totalHosts = 254
        val discovered = mutableListOf<ConnectedDevice>()

        // 1. Immediately register Current Device (This Phone)
        val currentDevice = ConnectedDevice(
            ip = localIp,
            hostName = "This Device (Phone)",
            isOnline = true,
            isCurrentDevice = true,
            interfaceInfo = "Wi-Fi Client",
            responseTimeMs = 0
        )
        discovered.add(currentDevice)

        // 2. Immediately register Gateway if known
        if (gatewayIp.isNotBlank() && gatewayIp != localIp) {
            val gatewayDevice = ConnectedDevice(
                ip = gatewayIp,
                hostName = "Default Gateway / Router",
                isOnline = true,
                isGateway = true,
                interfaceInfo = "Local Router AP",
                responseTimeMs = 2
            )
            discovered.add(gatewayDevice)
        }

        emit(ScanProgress(0, totalHosts, discovered.toList(), false))

        // 3. Concurrently probe the subnet in batches of 25 to balance speed & stability
        val batchSize = 25
        var scannedSoFar = 0

        for (startHost in 1..totalHosts step batchSize) {
            val endHost = minOf(startHost + batchSize - 1, totalHosts)
            val batchIps = (startHost..endHost).map { hostNum -> "$subnetPrefix.$hostNum" }
                .filter { it != localIp && it != gatewayIp }

            val batchResults = coroutineScope {
                batchIps.map { ip ->
                    async(Dispatchers.IO) {
                        probeIpAddress(ip)
                    }
                }.awaitAll().filterNotNull()
            }

            discovered.addAll(batchResults)
            scannedSoFar = endHost

            emit(ScanProgress(scannedSoFar, totalHosts, discovered.sortedBy { ipToLong(it.ip) }, false))
        }

        emit(ScanProgress(totalHosts, totalHosts, discovered.sortedBy { ipToLong(it.ip) }, true))
    }.flowOn(Dispatchers.IO)

    private fun probeIpAddress(ip: String): ConnectedDevice? {
        val startTime = System.currentTimeMillis()
        var isOnline = false
        var responseTime: Long = 0

        try {
            val inet = InetAddress.getByName(ip)

            // Try ICMP / standard reachability with 250ms timeout
            if (inet.isReachable(250)) {
                isOnline = true
                responseTime = System.currentTimeMillis() - startTime
            } else {
                // Many devices block ICMP ping. Try TCP socket on common ports (80, 443, 53, 8080, 22)
                val testPorts = intArrayOf(80, 443, 53, 8080, 5353)
                for (port in testPorts) {
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(ip, port), 120)
                            isOnline = true
                            responseTime = System.currentTimeMillis() - startTime
                        }
                        if (isOnline) break
                    } catch (_: java.net.ConnectException) {
                        // Connection refused means host is definitely UP and replied with TCP RST!
                        isOnline = true
                        responseTime = System.currentTimeMillis() - startTime
                        break
                    } catch (_: Exception) {
                        // Timeout or port unreachable
                    }
                }
            }

            if (isOnline) {
                // Hostname resolution: strictly do NOT invent fake names
                val resolvedName = try {
                    val canonical = inet.canonicalHostName
                    if (canonical.isNotBlank() && canonical != ip && !canonical.equals("unknown", ignoreCase = true)) {
                        canonical
                    } else {
                        val host = inet.hostName
                        if (host.isNotBlank() && host != ip && !host.equals("unknown", ignoreCase = true)) {
                            host
                        } else {
                            "Unknown device"
                        }
                    }
                } catch (_: Exception) {
                    "Unknown device"
                }

                return ConnectedDevice(
                    ip = ip,
                    hostName = resolvedName,
                    isOnline = true,
                    isCurrentDevice = false,
                    isGateway = false,
                    interfaceInfo = "LAN Host",
                    responseTimeMs = responseTime
                )
            }
        } catch (_: Exception) {
            // Ignore error
        }
        return null
    }

    private fun getNetworkAddresses(): Pair<String, String> {
        return try {
            val dhcp = wifiManager?.dhcpInfo
            val ipInt = dhcp?.ipAddress ?: 0
            val gatewayInt = dhcp?.gateway ?: 0

            val localIp = intToIp(ipInt)
            val gatewayIp = intToIp(gatewayInt)
            localIp to gatewayIp
        } catch (e: Exception) {
            "" to ""
        }
    }

    private fun intToIp(ipInt: Int): String {
        if (ipInt == 0) return ""
        return "${ipInt and 0xFF}.${ipInt shr 8 and 0xFF}.${ipInt shr 16 and 0xFF}.${ipInt shr 24 and 0xFF}"
    }

    private fun ipToLong(ip: String): Long {
        val parts = ip.split(".")
        if (parts.size != 4) return 0L
        return (parts[0].toLong() shl 24) +
                (parts[1].toLong() shl 16) +
                (parts[2].toLong() shl 8) +
                parts[3].toLong()
    }
}
