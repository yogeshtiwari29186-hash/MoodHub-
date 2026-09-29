package com.example.data.repository

import com.example.model.ConnectionCandidate
import com.example.model.ConnectionLifecycleStatus
import com.example.model.WifiConnectionSessionState
import com.example.model.WifiSecurityType
import com.example.util.SafeWifiLogger
import com.example.wifi.CandidateConnectionOutcome
import com.example.wifi.WifiConnector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Repository orchestrating the Wi-Fi connection lifecycle across single or ordered
 * candidate passwords using a strict sequential state machine:
 * IDLE -> CONNECTING -> (SUCCESS | FAILED) with mandatory per-candidate cleanup.
 * Transitions to CANCELLED upon user request.
 */
class WiFiConnectionRepository(
    private val wifiConnector: WifiConnector
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + Job())
    private var connectionJob: Job? = null
    private var timerJob: Job? = null

    private val _sessionState = MutableStateFlow(WifiConnectionSessionState())
    val sessionState: StateFlow<WifiConnectionSessionState> = _sessionState.asStateFlow()

    /**
     * Connects to a network using a single credential.
     */
    fun connectSingle(
        ssid: String,
        password: String,
        securityType: WifiSecurityType,
        timeoutMs: Long = 25_000L
    ) {
        connectWithCandidates(
            ssid = ssid,
            securityType = securityType,
            candidatePasswords = listOf(password),
            timeoutPerCandidateMs = timeoutMs
        )
    }

    /**
     * Executes the sequential state machine over an ordered list of connection candidates.
     */
    fun connectWithCandidates(
        ssid: String,
        securityType: WifiSecurityType,
        candidatePasswords: List<String>,
        timeoutPerCandidateMs: Long = 15_000L
    ) {
        if (ssid.isBlank()) {
            _sessionState.value = WifiConnectionSessionState(
                status = ConnectionLifecycleStatus.FAILED,
                targetSsid = "",
                errorMessage = "Target SSID cannot be blank."
            )
            return
        }

        // Prepare ordered candidates
        val candidates: List<ConnectionCandidate> = if (securityType == WifiSecurityType.OPEN) {
            listOf(ConnectionCandidate(rawPassword = "", source = "OpenNetwork"))
        } else {
            val list = candidatePasswords
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { ConnectionCandidate(rawPassword = it, source = "List") }
            if (list.isEmpty()) {
                listOf(ConnectionCandidate(rawPassword = "", source = "Empty"))
            } else {
                list
            }
        }

        // Cancel previous work cleanly
        cancelCurrentJobs(shouldSetCancelledState = false)
        wifiConnector.disconnectCurrent()

        val startTime = System.currentTimeMillis()
        SafeWifiLogger.i(
            "WiFiConnectionRepository",
            "Initiating connection state machine: SSID='$ssid', candidates=${candidates.size}"
        )

        _sessionState.value = WifiConnectionSessionState(
            status = ConnectionLifecycleStatus.CONNECTING,
            targetSsid = ssid,
            securityType = securityType,
            currentCandidateIndex = 1,
            totalCandidates = candidates.size,
            currentCandidateMasked = candidates.firstOrNull()?.masked ?: "",
            statusMessage = "Starting connection to $ssid...",
            elapsedTimeSeconds = 0L
        )

        // Elapsed time ticker
        timerJob = repositoryScope.launch {
            while (isActive) {
                delay(1000)
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                _sessionState.value = _sessionState.value.copy(elapsedTimeSeconds = elapsed)
            }
        }

        connectionJob = repositoryScope.launch {
            try {
                for ((index, candidate) in candidates.withIndex()) {
                    if (!isActive) break

                    val candidateIndex = index + 1
                    val masked = candidate.masked

                    SafeWifiLogger.d(
                        "WiFiConnectionRepository",
                        "Candidate [$candidateIndex/${candidates.size}] transition -> CONNECTING (masked='$masked')"
                    )

                    _sessionState.value = _sessionState.value.copy(
                        status = ConnectionLifecycleStatus.CONNECTING,
                        currentCandidateIndex = candidateIndex,
                        totalCandidates = candidates.size,
                        currentCandidateMasked = masked,
                        statusMessage = "Negotiating candidate $candidateIndex of ${candidates.size}..."
                    )

                    // Execute synchronous attempt for this candidate
                    val outcome = wifiConnector.connectCandidateSync(
                        ssid = ssid,
                        password = candidate.rawPassword,
                        securityType = securityType,
                        candidateIndex = candidateIndex,
                        totalCandidates = candidates.size,
                        timeoutMs = timeoutPerCandidateMs
                    )

                    when (outcome) {
                        is CandidateConnectionOutcome.Success -> {
                            SafeWifiLogger.i(
                                "WiFiConnectionRepository",
                                "Candidate [$candidateIndex/${candidates.size}] -> SUCCESS! IP=${outcome.ipAddress}, Gateway=${outcome.gateway}"
                            )
                            timerJob?.cancel()
                            _sessionState.value = _sessionState.value.copy(
                                status = ConnectionLifecycleStatus.SUCCESS,
                                currentCandidateIndex = candidateIndex,
                                currentCandidateMasked = masked,
                                verifiedIpAddress = outcome.ipAddress,
                                verifiedGateway = outcome.gateway,
                                linkSpeedMbps = outcome.linkSpeedMbps,
                                statusMessage = "Successfully connected & verified on $ssid!",
                                confirmedPassword = candidate.rawPassword,
                                errorMessage = null
                            )
                            return@launch
                        }

                        is CandidateConnectionOutcome.Failed -> {
                            SafeWifiLogger.w(
                                "WiFiConnectionRepository",
                                "Candidate [$candidateIndex/${candidates.size}] failed: ${outcome.reason}"
                            )
                            // Mandatory per-candidate cleanup before advancing
                            wifiConnector.disconnectCurrent()

                            // Brief cooldown to allow system network stack to settle
                            if (candidateIndex < candidates.size) {
                                delay(600)
                            }
                        }

                        is CandidateConnectionOutcome.Cancelled -> {
                            handleCancellation(ssid)
                            return@launch
                        }
                    }
                }

                // All candidates exhausted without success -> transition to FAILED
                SafeWifiLogger.w(
                    "WiFiConnectionRepository",
                    "All ${candidates.size} candidates exhausted. Transition -> FAILED"
                )
                timerJob?.cancel()
                wifiConnector.disconnectCurrent()
                _sessionState.value = _sessionState.value.copy(
                    status = ConnectionLifecycleStatus.FAILED,
                    statusMessage = "Connection failed. None of the ${candidates.size} candidate(s) could connect to $ssid.",
                    errorMessage = "Unable to connect with provided credential(s). Check signal or security settings.",
                    canOpenSettings = true
                )

            } catch (c: CancellationException) {
                handleCancellation(ssid)
            } catch (e: Exception) {
                SafeWifiLogger.e("WiFiConnectionRepository", "Unexpected connection error", e)
                timerJob?.cancel()
                wifiConnector.disconnectCurrent()
                _sessionState.value = _sessionState.value.copy(
                    status = ConnectionLifecycleStatus.FAILED,
                    statusMessage = "Connection error: ${e.localizedMessage ?: "Unknown error"}",
                    errorMessage = e.localizedMessage ?: "Connection error",
                    canOpenSettings = true
                )
            }
        }
    }

    private fun handleCancellation(ssid: String) {
        SafeWifiLogger.i("WiFiConnectionRepository", "Connection cancelled by user. Transition -> CANCELLED")
        timerJob?.cancel()
        wifiConnector.disconnectCurrent()
        wifiConnector.cancelConnection(ssid)
        _sessionState.value = _sessionState.value.copy(
            status = ConnectionLifecycleStatus.CANCELLED,
            statusMessage = "Connection cancelled by user.",
            errorMessage = "Operation cancelled."
        )
    }

    /**
     * User-requested cancellation of ongoing connection or candidate testing.
     */
    fun cancel() {
        val currentSsid = _sessionState.value.targetSsid
        cancelCurrentJobs(shouldSetCancelledState = true)
        handleCancellation(currentSsid)
    }

    /**
     * Resets the repository back to IDLE state.
     */
    fun reset() {
        cancelCurrentJobs(shouldSetCancelledState = false)
        wifiConnector.resetState()
        _sessionState.value = WifiConnectionSessionState()
        SafeWifiLogger.d("WiFiConnectionRepository", "Reset state machine to IDLE")
    }

    private fun cancelCurrentJobs(shouldSetCancelledState: Boolean) {
        connectionJob?.cancel()
        connectionJob = null
        timerJob?.cancel()
        timerJob = null
    }

    fun openWifiSettings() {
        wifiConnector.openWifiSettings()
    }
}
