package com.example.wifi

import android.content.Context
import com.example.model.WifiSecurityType
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
    private val wifiConnector: WifiConnector
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
        val result: TestResult? = null
    )

    sealed class TestResult {
        data class Success(val confirmedPassword: String, val attemptsCount: Int, val timeSeconds: Long) : TestResult()
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
                    val masked = if (candidate.length > 2) {
                        "${candidate.first()}${"•".repeat(candidate.length - 2)}${candidate.last()}"
                    } else {
                        "••••••••"
                    }

                    _testState.value = _testState.value.copy(
                        currentIndex = candidateNumber,
                        currentCandidateMasked = masked,
                        statusMessage = "Verifying candidate $candidateNumber of ${filteredCandidates.size}..."
                    )

                    // Attempt connection using Android official Wi-Fi specifier
                    wifiConnector.connect(targetSsid, candidate, securityType)

                    // Await connection state for up to 3.5 seconds
                    val pollLimit = 14
                    var confirmed = false
                    for (i in 0 until pollLimit) {
                        delay(250)
                        val connState = wifiConnector.connectionState.value
                        if (connState is WifiConnectionState.Connected) {
                            confirmed = true
                            break
                        }
                        if (connState is WifiConnectionState.Failed) {
                            break
                        }
                    }

                    if (confirmed) {
                        val elapsed = _testState.value.elapsedTimeSeconds
                        timerJob?.cancel()
                        _testState.value = _testState.value.copy(
                            isRunning = false,
                            statusMessage = "Success! Authorized credential confirmed for $targetSsid.",
                            result = TestResult.Success(
                                confirmedPassword = candidate,
                                attemptsCount = candidateNumber,
                                timeSeconds = elapsed
                            )
                        )
                        return@launch
                    }

                    // Reset and pause safely between candidates to avoid system throttling
                    wifiConnector.resetState()
                    delay(1200)
                }

                // If loop finished without finding a match
                val elapsed = _testState.value.elapsedTimeSeconds
                timerJob?.cancel()
                _testState.value = _testState.value.copy(
                    isRunning = false,
                    statusMessage = "Completed: No matching credential found among ${filteredCandidates.size} entries.",
                    result = TestResult.CompletedNoMatch(filteredCandidates.size, elapsed)
                )

            } catch (e: CancellationException) {
                val elapsed = _testState.value.elapsedTimeSeconds
                timerJob?.cancel()
                _testState.value = _testState.value.copy(
                    isRunning = false,
                    statusMessage = "Authorized test stopped by user.",
                    result = TestResult.Cancelled(_testState.value.currentIndex, elapsed)
                )
            } catch (e: Exception) {
                timerJob?.cancel()
                _testState.value = _testState.value.copy(
                    isRunning = false,
                    statusMessage = "Error during test: ${e.localizedMessage}",
                    result = TestResult.Error(e.localizedMessage ?: "Unknown error")
                )
            }
        }
    }

    fun stopTest() {
        testJob?.cancel()
        timerJob?.cancel()
        wifiConnector.resetState()
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
