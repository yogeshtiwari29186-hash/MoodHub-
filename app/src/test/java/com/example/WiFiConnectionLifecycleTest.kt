package com.example

import com.example.model.ConnectionCandidate
import com.example.model.ConnectionLifecycleStatus
import com.example.model.WifiConnectionSessionState
import com.example.model.WifiSecurityType
import com.example.util.SafeWifiLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WiFiConnectionLifecycleTest {

    @Test
    fun testPasswordMaskingProtectsSecrets() {
        val shortPass = "ab"
        val normalPass = "SuperSecret123"
        val longPass = "VeryLongAndComplexPassphrase999!"

        val maskedShort = SafeWifiLogger.mask(shortPass)
        val maskedNormal = SafeWifiLogger.mask(normalPass)
        val maskedLong = SafeWifiLogger.mask(longPass)

        // Ensure raw string is not equal to masked string
        assertNotEquals(normalPass, maskedNormal)
        assertNotEquals(longPass, maskedLong)

        // Ensure bullet characters are used
        assertTrue(maskedNormal.contains("•"))
        assertTrue(maskedLong.contains("•"))

        // Ensure first and last character preserved for recognition if long enough
        assertTrue(maskedNormal.startsWith("S"))
        assertTrue(maskedNormal.endsWith("3"))
        assertFalse(maskedNormal.contains("Secret"))

        // Empty password safety
        assertEquals("<empty>", SafeWifiLogger.mask(""))
        assertEquals("<empty>", SafeWifiLogger.mask(null))
    }

    @Test
    fun testConnectionCandidateDoesNotLeakInToString() {
        val raw = "ConfidentialPassword2026"
        val candidate = ConnectionCandidate(rawPassword = raw, source = "Wordlist")

        val stringRepresentation = candidate.toString()
        assertFalse(
            "Candidate toString must NEVER contain raw password!",
            stringRepresentation.contains(raw)
        )
        assertTrue(stringRepresentation.contains("masked="))
        assertTrue(stringRepresentation.contains("•"))
    }

    @Test
    fun testSessionStateInitialStateIsIdle() {
        val state = WifiConnectionSessionState()
        assertEquals(ConnectionLifecycleStatus.IDLE, state.status)
        assertEquals(0, state.currentCandidateIndex)
        assertEquals(0, state.totalCandidates)
        assertFalse(state.isRunning)
        assertFalse(state.isTerminal)
        assertNull(state.verifiedIpAddress)
        assertNull(state.verifiedGateway)
    }

    @Test
    fun testSessionStateConnectingIsRunning() {
        val state = WifiConnectionSessionState(
            status = ConnectionLifecycleStatus.CONNECTING,
            targetSsid = "MyOfficeWifi",
            currentCandidateIndex = 2,
            totalCandidates = 5,
            currentCandidateMasked = "P••••••••9"
        )
        assertTrue(state.isRunning)
        assertFalse(state.isTerminal)
        assertEquals("MyOfficeWifi", state.targetSsid)
        assertEquals(2, state.currentCandidateIndex)
        assertEquals(5, state.totalCandidates)
    }

    @Test
    fun testSessionStateSuccessWithVerification() {
        val state = WifiConnectionSessionState(
            status = ConnectionLifecycleStatus.SUCCESS,
            targetSsid = "HomeNetwork",
            currentCandidateIndex = 1,
            totalCandidates = 1,
            verifiedIpAddress = "192.168.1.150",
            verifiedGateway = "192.168.1.1",
            linkSpeedMbps = 433
        )
        assertFalse(state.isRunning)
        assertTrue(state.isTerminal)
        assertEquals("192.168.1.150", state.verifiedIpAddress)
        assertEquals("192.168.1.1", state.verifiedGateway)
        assertEquals(433, state.linkSpeedMbps)
    }

    @Test
    fun testSessionStateFailureAndCancellationAreTerminal() {
        val failedState = WifiConnectionSessionState(
            status = ConnectionLifecycleStatus.FAILED,
            targetSsid = "GuestWifi",
            errorMessage = "None of the 4 candidates could connect."
        )
        assertTrue(failedState.isTerminal)
        assertFalse(failedState.isRunning)

        val cancelledState = WifiConnectionSessionState(
            status = ConnectionLifecycleStatus.CANCELLED,
            targetSsid = "GuestWifi",
            statusMessage = "Connection cancelled by user."
        )
        assertTrue(cancelledState.isTerminal)
        assertFalse(cancelledState.isRunning)
    }

    @Test
    fun testSequentialCandidateProgressSimulation() {
        // Simulating the sequential state machine transitions:
        // IDLE -> CONNECTING(Candidate 1) -> FAIL -> CLEANUP -> CONNECTING(Candidate 2) -> SUCCESS(Verified IP/Gateway)
        var currentState = WifiConnectionSessionState(
            status = ConnectionLifecycleStatus.IDLE,
            targetSsid = "Router5G"
        )
        assertEquals(ConnectionLifecycleStatus.IDLE, currentState.status)

        val candidates = listOf(
            ConnectionCandidate("WrongPass1"),
            ConnectionCandidate("CorrectPass2026!"),
            ConnectionCandidate("UnusedPass3")
        )

        // Step 1: Candidate 1 begins
        currentState = currentState.copy(
            status = ConnectionLifecycleStatus.CONNECTING,
            currentCandidateIndex = 1,
            totalCandidates = candidates.size,
            currentCandidateMasked = candidates[0].masked
        )
        assertEquals(ConnectionLifecycleStatus.CONNECTING, currentState.status)
        assertEquals(1, currentState.currentCandidateIndex)

        // Step 2: Candidate 1 fails, cleanup is executed
        var cleanupCount = 0
        fun doCleanup() { cleanupCount++ }
        doCleanup()
        assertEquals(1, cleanupCount)

        // Step 3: Candidate 2 begins
        currentState = currentState.copy(
            status = ConnectionLifecycleStatus.CONNECTING,
            currentCandidateIndex = 2,
            currentCandidateMasked = candidates[1].masked
        )
        assertEquals(2, currentState.currentCandidateIndex)

        // Step 4: Candidate 2 succeeds and verifies IP & Gateway
        currentState = currentState.copy(
            status = ConnectionLifecycleStatus.SUCCESS,
            verifiedIpAddress = "192.168.0.42",
            verifiedGateway = "192.168.0.1",
            confirmedPassword = candidates[1].rawPassword
        )
        assertEquals(ConnectionLifecycleStatus.SUCCESS, currentState.status)
        assertEquals("192.168.0.42", currentState.verifiedIpAddress)
        assertEquals("192.168.0.1", currentState.verifiedGateway)
        assertEquals("CorrectPass2026!", currentState.confirmedPassword)
    }

    @Test
    fun testUserCancellationTransitionsToCancelled() {
        var currentState = WifiConnectionSessionState(
            status = ConnectionLifecycleStatus.CONNECTING,
            targetSsid = "CafeWifi",
            currentCandidateIndex = 3,
            totalCandidates = 10
        )
        assertTrue(currentState.isRunning)

        // User hits Stop / Cancel
        var cleanupRan = false
        fun cancelAndCleanup() {
            cleanupRan = true
            currentState = currentState.copy(
                status = ConnectionLifecycleStatus.CANCELLED,
                errorMessage = "Operation cancelled."
            )
        }
        cancelAndCleanup()

        assertTrue(cleanupRan)
        assertEquals(ConnectionLifecycleStatus.CANCELLED, currentState.status)
        assertFalse(currentState.isRunning)
        assertTrue(currentState.isTerminal)
    }
}
