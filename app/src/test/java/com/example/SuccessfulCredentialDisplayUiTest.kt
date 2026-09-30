package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.model.Candidate
import com.example.model.ImportedEntry
import com.example.model.SuccessfulConnectionResult
import com.example.model.WifiNetwork
import com.example.model.WifiSecurityType
import com.example.ui.components.ConnectModal
import com.example.ui.components.SuccessfulConnectionCard
import com.example.ui.theme.WifiManagerTheme
import com.example.wifi.WifiConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit and UI test verifying that:
 * 1. The successful result card renders ONLY from the immutable SuccessfulConnectionResult,
 *    displaying the authorized candidate ("Aarti7756") and never any manual or previous password.
 * 2. The ConnectModal UI has completely removed manual password TextFields.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SuccessfulCredentialDisplayUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCardDisplaysSuccessfulCandidateInsteadOfManualPassword() {
        val previousOrManualPassword = "123456"
        val defaultPassword = "defaultPassword123"
        val successfulCandidate = "Aarti7756"

        val candidate = Candidate(
            credential = successfulCandidate,
            source = "Imported TXT",
            globalLineNumber = 4L,
            batchNumber = 1L,
            positionInBatch = 4,
            totalLines = 2000L,
            totalBatches = 4L
        )

        // Capture immutable SuccessfulConnectionResult ONLY from candidate
        val result = SuccessfulConnectionResult(
            credential = candidate.credential,
            source = candidate.source,
            globalLineNumber = candidate.globalLineNumber,
            batchNumber = candidate.batchNumber,
            positionInBatch = candidate.positionInBatch,
            totalLines = candidate.totalLines,
            totalBatches = candidate.totalBatches,
            ssid = "MyWiFi",
            ipAddress = "192.168.1.10",
            gateway = "192.168.1.1"
        )

        // Assertion 1: result.credential == "Aarti7756" regardless of any previous/default state
        assertEquals("Aarti7756", result.credential)
        assertNotEquals(previousOrManualPassword, result.credential)
        assertNotEquals(defaultPassword, result.credential)

        // Render Compose result card
        composeTestRule.setContent {
            WifiManagerTheme {
                SuccessfulConnectionCard(result = result)
            }
        }

        // Assertion 2: The UI must display "Aarti7756", "MyWiFi", "192.168.1.10"
        composeTestRule.onNodeWithTag("successful_result_credential").assertExists()
        composeTestRule.onNodeWithText(successfulCandidate, substring = true).assertExists()
        composeTestRule.onNodeWithText("Password: $successfulCandidate", substring = true).assertExists()
        composeTestRule.onNodeWithText("MyWiFi", substring = true).assertExists()
        composeTestRule.onNodeWithText("192.168.1.10", substring = true).assertExists()
        composeTestRule.onNodeWithText("Imported TXT", substring = true).assertExists()
        composeTestRule.onNodeWithText("1 / 4", substring = true).assertExists()
        composeTestRule.onNodeWithText("4 / 500", substring = true).assertExists()

        // Assertion 3: Previous / manual password must NOT appear anywhere in the UI
        composeTestRule.onNodeWithText(previousOrManualPassword, substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText(defaultPassword, substring = true).assertDoesNotExist()
    }

    @Test
    fun testConnectModalHasNoManualPasswordTextField() {
        val network = WifiNetwork(
            ssid = "MyWiFi",
            bssid = "00:11:22:33:44:55",
            level = 3,
            rssi = -60,
            frequency = 5180,
            capabilities = "[WPA2-PSK-CCMP]",
            securityType = WifiSecurityType.WPA2_PSK
        )

        val importedList = listOf(
            ImportedEntry(id = "1", ssid = "MyWiFi", password = "Aarti7756", lineNumber = 4L, totalLines = 2000L)
        )

        composeTestRule.setContent {
            WifiManagerTheme {
                ConnectModal(
                    network = network,
                    importedEntries = importedList,
                    connectionState = WifiConnectionState.Idle,
                    onImportFileClick = {},
                    onEditImportedEntry = { _, _, _ -> },
                    onDeleteImportedEntry = {},
                    onCancelConnection = {},
                    onOpenRouterTest = {},
                    onOpenSettings = {},
                    onDismiss = {}
                )
            }
        }

        // Verify that the manual password input field is completely removed
        composeTestRule.onNodeWithTag("wifi_password_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("tab_enter_password").assertDoesNotExist()

        // Verify that candidate "Aarti7756" is rendered in the candidate list
        composeTestRule.onNodeWithText("Aarti7756", substring = true).assertExists()
    }
}
