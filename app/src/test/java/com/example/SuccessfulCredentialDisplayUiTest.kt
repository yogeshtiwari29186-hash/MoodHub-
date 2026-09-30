package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.model.Candidate
import com.example.model.SuccessfulConnectionResult
import com.example.ui.components.SuccessfulConnectionCard
import com.example.ui.theme.WifiManagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit and UI test verifying that the successful result card renders ONLY from
 * the immutable SuccessfulConnectionResult, displaying the authorized candidate ("Aarti7756")
 * and never the manual password ("123456").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SuccessfulCredentialDisplayUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCardDisplaysSuccessfulCandidateInsteadOfManualPassword() {
        val manualPassword = "123456"
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
            ssid = "AartiRouter",
            ipAddress = "192.168.1.100",
            gateway = "192.168.1.1"
        )

        // Assertion 1: result.credential == "Aarti7756"
        assertEquals("Aarti7756", result.credential)
        assertNotEquals(manualPassword, result.credential)

        // Render Compose result card
        composeTestRule.setContent {
            WifiManagerTheme {
                SuccessfulConnectionCard(result = result)
            }
        }

        // Assertion 2: The UI must display "Aarti7756", not "123456"
        composeTestRule.onNodeWithTag("successful_result_credential").assertExists()
        composeTestRule.onNodeWithText(successfulCandidate, substring = true).assertExists()
        composeTestRule.onNodeWithText("Password: $successfulCandidate", substring = true).assertExists()
        composeTestRule.onNodeWithText("Imported TXT", substring = true).assertExists()
        composeTestRule.onNodeWithText("1 / 4", substring = true).assertExists()
        composeTestRule.onNodeWithText("4 / 500", substring = true).assertExists()

        // Assertion 3: manualPassword "123456" must NOT appear in the UI
        composeTestRule.onNodeWithText(manualPassword, substring = true).assertDoesNotExist()
    }
}
