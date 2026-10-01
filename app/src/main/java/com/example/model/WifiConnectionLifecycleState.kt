package com.example.model

import com.example.util.SafeWifiLogger

/**
 * Strict lifecycle states for the Wi-Fi connection state machine.
 */
enum class ConnectionLifecycleStatus {
    IDLE,
    CONNECTING,
    SUCCESS,
    FAILED,
    CANCELLED
}

/**
 * Encapsulates an ordered connection candidate with its raw credential
 * and safe masked representation for logging and UI.
 */
data class ConnectionCandidate(
    val rawPassword: String,
    val source: String = "User"
) {
    val masked: String = SafeWifiLogger.mask(rawPassword)

    override fun toString(): String {
        return "ConnectionCandidate(source='$source', masked='$masked')"
    }
}

/**
 * Comprehensive session state exposed by WiFiConnectionRepository and ViewModel.
 */
data class WifiConnectionSessionState(
    val status: ConnectionLifecycleStatus = ConnectionLifecycleStatus.IDLE,
    val targetSsid: String = "",
    val securityType: WifiSecurityType = WifiSecurityType.WPA2_PSK,
    val currentCandidateIndex: Int = 0, // 1-based index (e.g., 1 of 5)
    val totalCandidates: Int = 0,
    val currentCandidateMasked: String = "",
    val verifiedIpAddress: String? = null,
    val verifiedGateway: String? = null,
    val linkSpeedMbps: Int? = null,
    val statusMessage: String = "Idle",
    val errorMessage: String? = null,
    val elapsedTimeSeconds: Long = 0L,
    val canOpenSettings: Boolean = false,
    val successfulResult: SuccessfulConnectionResult? = null
) {
    val isRunning: Boolean
        get() = status == ConnectionLifecycleStatus.CONNECTING

    val isTerminal: Boolean
        get() = status in listOf(
            ConnectionLifecycleStatus.SUCCESS,
            ConnectionLifecycleStatus.FAILED,
            ConnectionLifecycleStatus.CANCELLED
        )

    override fun toString(): String {
        return "WifiConnectionSessionState(status=$status, ssid='$targetSsid', " +
                "candidate=$currentCandidateIndex/$totalCandidates, masked='$currentCandidateMasked', " +
                "ip=$verifiedIpAddress, gateway=$verifiedGateway, elapsed=${elapsedTimeSeconds}s)"
    }
}
