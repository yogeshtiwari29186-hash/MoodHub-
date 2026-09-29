package com.example.model

data class ConnectedDevice(
    val ip: String,
    val hostName: String,
    val isOnline: Boolean,
    val isCurrentDevice: Boolean = false,
    val isGateway: Boolean = false,
    val interfaceInfo: String = "",
    val responseTimeMs: Long = 0,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    val displayHostName: String
        get() = if (hostName.isNotBlank() && hostName != ip && !hostName.equals("unknown", ignoreCase = true)) {
            hostName
        } else {
            "Unknown device"
        }
}
