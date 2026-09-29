package com.example.wifi

import android.content.Context
import com.example.data.repository.WiFiConnectionRepository
import com.example.model.WifiSecurityType
import com.example.util.SafeWifiLogger
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

class AuthorizedRouterTestManager(
    private val context: Context,
    private val wifiConnector: WifiConnector,
    private val wifiConnectionRepository: WiFiConnectionRepository? = null
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var testJob: Job? = null
    private var timerJob: Job? = null

    private val _testState = MutableStateFlow(RouterTestState())
    val testState: StateFlow<RouterTestState> = _testState.asStateFlow()

    data class RouterTestState(
        val isRunning: Boolean = false,
        val targetSsid: String = "",
        val securityType: WifiSecurityType = WifiSecurityType.WPA2_PSK,
        val currentIndex: Int = 0,
        val totalCount: Int = 0,
        val currentCandidateMasked: String = "",
        val elapsedTimeSeconds: Long = 0,
        val statusMessage: String = "Ready to test authorized network.",
        val verifiedIpAddress: String? = null,
        val verifiedGateway: String? = null,
        val result: TestResult? = null
    )

    sealed class TestResult {
        data class Success(
            val confirmedPassword: String,
            val attemptsCount: Int,
            val timeSeconds: Long,
            val ipAddress: String? = null,
            val gateway: String? = null
        ) : TestResult()

        data class CompletedNoMatch(val totalTested: Int, val timeSeconds: Long) : TestResult()
        data class Cancelled(val testedCount: Int, val timeSeconds: Long) : TestResult()
        data class Error(val message: String) : TestResult()
    }

    fun startTest(
        targetSsid: String,
        securityType: WifiSecurityType,
        candidatePasswords: List<String>
    ) {
        if (targetSsid.isBlank()) {
            _testState.value = _testState.value.copy(
                statusMessage = "Error: Please select a valid Wi-Fi network.",
                result = TestResult.Error("No target SSID specified")
            )
            return
        }

        val filteredCandidates = candidatePasswords.map { it.trim() }.filter { it.length >= 8 }
        if (filteredCandidates.isEmpty()) {
            _testState.value = _testState.value.copy(
                statusMessage = "No valid candidate passwords (min 8 chars) found in list.",
                result = TestResult.Error("Candidate list has no valid passwords")
            )
            return
        }

        stopTest()

        _testState.value = RouterTestState(
            isRunning = true,
            targetSsid = targetSsid,
            securityType = securityType,
            currentIndex = 0,
            totalCount = filteredCandidates.size,
            elapsedTimeSeconds = 0,
            statusMessage = "Starting authorized router verification...",
            result = null
        )

        // Elapsed time counter
        val startTime = System.currentTimeMillis()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                _testState.value = _testState.value.copy(elapsedTimeSeconds = elapsed)
            }
        }

        testJob = scope.launch {
            try {
                for ((index, candidate) in filteredCandidates.withIndex()) {
                    if (!isActive) break

                    val candidateNumber = index + 1
                    val masked = SafeWifiLogger.mask(candidate)

                    _testState.value = _testState.value.copy(
                        currentIndex = candidateNumber,
                        currentCandidateMasked = masked,
                        statusMessage = "Verifying candidate $candidateNumber of ${filteredCandidates.size}..."
                    )

                    SafeWifiLogger.d(
                        "AuthorizedRouterTestManager",
                        "Candidate $candidateNumber/${filteredCandidates.size} testing with masked='$masked'"
                    )

                    // Execute candidate connection with verification of IP and gateway
                    val outcome = wifiConnector.connectCandidateSync(
                        ssid = targetSsid,
                        password = candidate,
                        securityType = securityType,
                        candidateIndex = candidateNumber,
                        totalCandidates = filteredCandidates.size,
                        timeoutMs = 12_000L
                    )

                    when (outcome) {
                        is CandidateConnectionOutcome.Success -> {
                            val elapsed = _testState.value.elapsedTimeSeconds
                            timerJob?.cancel()
                            SafeWifiLogger.i(
                                "AuthorizedRouterTestManager",
                                "Candidate $candidateNumber verified! IP=${outcome.ipAddress}, Gateway=${outcome.gateway}"
                            )
                            _testState.value = _testState.value.copy(
                                isRunning = false,
                                verifiedIpAddress = outcome.ipAddress,
                                verifiedGateway = outcome.gateway,
                                statusMessage = "Success! Authorized credential confirmed for $targetSsid (IP: ${outcome.ipAddress ?: "Assigned"}).",
                                result = TestResult.Success(
                                    confirmedPassword = candidate,
                                    attemptsCount = candidateNumber,
                                    timeSeconds = elapsed,
                                    ipAddress = outcome.ipAddress,
                                    gateway = outcome.gateway
                                )
                            )
                            return@launch
                        }

                        is CandidateConnectionOutcome.Failed -> {
                            SafeWifiLogger.d(
                                "AuthorizedRouterTestManager",
                                "Candidate $candidateNumber failed: ${outcome.reason}. Executing cleanup."
                            )
                            // Mandatory per-candidate cleanup
                            wifiConnector.disconnectCurrent()
                            delay(500) // Cooldown between candidates
                        }

                        is CandidateConnectionOutcome.Cancelled -> {
                            val elapsed = _testState.value.elapsedTimeSeconds
                            timerJob?.cancel()
                            wifiConnector.disconnectCurrent()
                            _testState.value = _testState.value.copy(
                                isRunning = false,
                                statusMessage = "Authorized test cancelled.",
                                result = TestResult.Cancelled(candidateNumber, elapsed)
                            )
                            return@launch
                        }
                    }
                }

                // If loop finished without finding a match
                val elapsed = _testState.value.elapsedTimeSeconds
                timerJob?.cancel()
                wifiConnector.disconnectCurrent()
                _testState.value = _testState.value.copy(
                    isRunning = false,
                    statusMessage = "Completed: No matching credential found among ${filteredCandidates.size} entries.",
                    result = TestResult.CompletedNoMatch(filteredCandidates.size, elapsed)
                )

            } catch (e: CancellationException) {
                val elapsed = _testState.value.elapsedTimeSeconds
                timerJob?.cancel()
                wifiConnector.disconnectCurrent()
                _testState.value = _testState.value.copy(
                    isRunning = false,
                    statusMessage = "Authorized test stopped by user.",
                    result = TestResult.Cancelled(_testState.value.currentIndex, elapsed)
                )
            } catch (e: Exception) {
                timerJob?.cancel()
                wifiConnector.disconnectCurrent()
                _testState.value = _testState.value.copy(
                    isRunning = false,
                    statusMessage = "Error during test: ${e.localizedMessage ?: "Unknown error"}",
                    result = TestResult.Error(e.localizedMessage ?: "Unknown error")
                )
            }
        }
    }

    fun stopTest() {
        testJob?.cancel()
        timerJob?.cancel()
        wifiConnector.disconnectCurrent()
        val currState = _testState.value
        if (currState.isRunning) {
            _testState.value = currState.copy(
                isRunning = false,
                statusMessage = "Test stopped by user.",
                result = TestResult.Cancelled(currState.currentIndex, currState.elapsedTimeSeconds)
            )
        }
    }

    fun resetState() {
        stopTest()
        _testState.value = RouterTestState()
    }
}
