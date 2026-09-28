package com.example.model

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val capabilities: String,
    val securityType: WifiSecurityType,
    val level: Int, // RSSI in dBm
    val signalLevel: Int, // 0 to 4
    val frequency: Int, // in MHz
    val bandLabel: String, // "2.4 GHz", "5 GHz", "6 GHz"
    val channel: Int,
    val isConnected: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    val displaySsid: String
        get() = if (ssid.isBlank() || ssid == "<unknown ssid>") "Hidden Network" else ssid.replace("\"", "")

    val signalPercentage: Int
        get() = ((level + 100).coerceIn(0, 50) * 2) // Roughly 0 to 100% (-100dBm to -50dBm)

    companion object {
        fun calculateChannel(frequency: Int): Int {
            return when {
                frequency >= 2412 && frequency <= 2484 -> (frequency - 2407) / 5
                frequency in 5170..5825 -> (frequency - 5000) / 5
                frequency in 5945..7105 -> (frequency - 5940) / 5
                else -> 0
            }
        }

        fun calculateBand(frequency: Int): String {
            return when {
                frequency in 2400..2500 -> "2.4 GHz"
                frequency in 4900..5900 -> "5 GHz"
                frequency in 5925..7125 -> "6 GHz"
                else -> "Wi-Fi"
            }
        }
    }
}

data class CurrentWifiInfo(
    val isConnected: Boolean = false,
    val ssid: String = "",
    val bssid: String = "",
    val ipAddress: String = "",
    val linkSpeedMbps: Int = 0,
    val rssi: Int = 0,
    val frequency: Int = 0,
    val band: String = "",
    val gateway: String = "",
    val dns: String = "",
    val netmask: String = ""
)
