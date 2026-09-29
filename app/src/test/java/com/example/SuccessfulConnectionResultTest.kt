package com.example

import com.example.model.ConnectionResultInfo
import com.example.model.SelectedCredentialMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuccessfulConnectionResultTest {

    @Test
    fun testImportedTxtCredentialWithBatchInfo() {
        // Line 11 out of 10,000,000 in Batch 1 of 20,000
        val result = ConnectionResultInfo(
            ssid = "sai raj",
            password = "12345678",
            source = "Imported TXT",
            passwordNumber = 11L,
            lineNumber = 11L,
            totalLines = 10_000_000L,
            batchNumber = 1L,
            totalBatches = 20_000L,
            positionInBatch = 11,
            ipAddress = "192.168.1.5",
            status = "Connected"
        )

        assertTrue(result.isImported)
        assertEquals("#11", result.formatPasswordNumber())
        assertEquals("11 / 10,000,000", result.formatLine())
        assertEquals("1 / 20,000", result.formatBatch())
        assertEquals("11 / 500", result.formatPositionInBatch())
        assertEquals("192.168.1.5", result.ipAddress)
        assertEquals("sai raj", result.ssid)
        assertEquals("Connected", result.status)
        assertEquals("Imported TXT", result.source)
    }

    @Test
    fun testPreservedLineNumberAcrossBatches() {
        // Line 511 out of 10,000,000 in Batch 2 of 20,000
        // Line 511 corresponds to batch 2, position 11 / 500
        val result = ConnectionResultInfo(
            ssid = "Office-5G",
            password = "SecurePassword2026",
            source = "Imported TXT",
            passwordNumber = 511L,
            lineNumber = 511L,
            totalLines = 10_000_000L,
            batchNumber = 2L,
            totalBatches = 20_000L,
            positionInBatch = 11,
            ipAddress = "192.168.1.100",
            status = "Connected"
        )

        assertTrue(result.isImported)
        assertEquals("#511", result.formatPasswordNumber())
        assertEquals("511 / 10,000,000", result.formatLine())
        assertEquals("2 / 20,000", result.formatBatch())
        assertEquals("11 / 500", result.formatPositionInBatch())
    }

    @Test
    fun testManuallyEnteredCredentialDoesNotInventLineNumber() {
        val manualResult = ConnectionResultInfo(
            ssid = "sai raj",
            password = "MyCustomPassword99!",
            source = "Manually Entered",
            passwordNumber = null,
            lineNumber = null,
            totalLines = null,
            batchNumber = null,
            totalBatches = null,
            positionInBatch = null,
            ipAddress = "192.168.1.5",
            status = "Connected"
        )

        assertFalse(manualResult.isImported)
        assertEquals("Manually Entered", manualResult.source)
        assertNull(manualResult.formatPasswordNumber())
        assertNull(manualResult.formatLine())
        assertNull(manualResult.formatBatch())
        assertNull(manualResult.formatPositionInBatch())
        assertEquals("192.168.1.5", manualResult.ipAddress)
        assertEquals("Connected", manualResult.status)
    }

    @Test
    fun testSelectedCredentialMetadataFormatting() {
        val metadata = SelectedCredentialMetadata(
            password = "testPassword",
            source = "Imported TXT",
            passwordNumber = 42L,
            lineNumber = 42L,
            totalLines = 5000L,
            batchNumber = 1L,
            totalBatches = 10L,
            positionInBatch = 42
        )

        assertEquals("#42", metadata.formatPasswordNumber())
        assertEquals("42 / 5,000", metadata.formatLine())
        assertEquals("1 / 10", metadata.formatBatch())
        assertEquals("42 / 500", metadata.formatPositionInBatch())
    }
}
