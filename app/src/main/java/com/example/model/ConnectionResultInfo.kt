package com.example.model

import com.example.util.SafeWifiLogger
import java.text.NumberFormat

/**
 * Immutable representation of a candidate selected for a Wi-Fi connection attempt.
 * Bundles the credential with its origin metadata (file line number, batch, position).
 */
data class Candidate(
    val credential: String,
    val source: String = "Imported TXT",
    val globalLineNumber: Long? = null,
    val batchNumber: Long? = null,
    val positionInBatch: Int? = null,
    val totalLines: Long? = null,
    val totalBatches: Long? = null
) {
    val rawPassword: String get() = credential
    val masked: String get() = SafeWifiLogger.mask(credential)

    override fun toString(): String {
        return "Candidate(source='$source', line=$globalLineNumber, batch=$batchNumber, pos=$positionInBatch, masked='$masked')"
    }
}

/**
 * Immutable result model constructed directly from the successful Candidate upon connection.
 * Guarantees that the UI displays the exact candidate credential and line number rather
 * than whatever was entered in a manual input field.
 */
data class SuccessfulConnectionResult(
    val credential: String,
    val source: String = "Imported TXT",
    val globalLineNumber: Long? = null,
    val batchNumber: Long? = null,
    val positionInBatch: Int? = null,
    val totalLines: Long? = null,
    val totalBatches: Long? = null,
    val ssid: String = "",
    val ipAddress: String? = null,
    val gateway: String? = null,
    val status: String = "Connected",
    val isConnected: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
) {
    // Aliases to maintain full backwards compatibility with existing code and tests
    val password: String get() = credential
    val lineNumber: Long? get() = globalLineNumber
    val passwordNumber: Long? get() = globalLineNumber

    val isImported: Boolean
        get() = source != "Manually Entered" && globalLineNumber != null

    constructor(
        ssid: String,
        password: String,
        source: String = "Imported TXT",
        passwordNumber: Long? = null,
        lineNumber: Long? = null,
        totalLines: Long? = null,
        batchNumber: Long? = null,
        totalBatches: Long? = null,
        positionInBatch: Int? = null,
        ipAddress: String? = null,
        gateway: String? = null,
        status: String = "Connected",
        isConnected: Boolean = true,
        timestamp: Long = System.currentTimeMillis()
    ) : this(
        credential = password,
        source = source,
        globalLineNumber = lineNumber ?: passwordNumber,
        batchNumber = batchNumber,
        positionInBatch = positionInBatch,
        totalLines = totalLines,
        totalBatches = totalBatches,
        ssid = ssid,
        ipAddress = ipAddress,
        gateway = gateway,
        status = status,
        isConnected = isConnected,
        timestamp = timestamp
    )

    fun formatPasswordNumber(): String? {
        val num = globalLineNumber ?: return null
        return "#$num"
    }

    fun formatLine(): String? {
        val line = globalLineNumber ?: return null
        val total = totalLines
        return if (total != null && total > 0) {
            "${formatNumber(line)} / ${formatNumber(total)}"
        } else {
            formatNumber(line)
        }
    }

    fun formatBatch(): String? {
        val batch = batchNumber ?: globalLineNumber?.let { ((it - 1) / 500) + 1 } ?: return null
        val totalB = totalBatches ?: totalLines?.let { if (it > 0) ((it - 1) / 500) + 1 else 1L }
        return if (totalB != null && totalB > 0) {
            "${formatNumber(batch)} / ${formatNumber(totalB)}"
        } else {
            formatNumber(batch)
        }
    }

    fun formatPositionInBatch(): String? {
        val pos = positionInBatch ?: globalLineNumber?.let { (((it - 1) % 500) + 1).toInt() } ?: return null
        return "$pos / 500"
    }

    companion object {
        fun formatNumber(number: Long): String {
            return NumberFormat.getNumberInstance().format(number)
        }
    }
}

typealias ConnectionResultInfo = SuccessfulConnectionResult

/**
 * Origin metadata helper for selected credentials.
 */
data class SelectedCredentialMetadata(
    val password: String,
    val source: String = "Imported TXT",
    val passwordNumber: Long? = null,
    val lineNumber: Long? = null,
    val totalLines: Long? = null,
    val batchNumber: Long? = null,
    val totalBatches: Long? = null,
    val positionInBatch: Int? = null
) {
    fun formatPasswordNumber(): String? {
        val num = passwordNumber ?: lineNumber ?: return null
        return "#$num"
    }

    fun formatLine(): String? {
        val line = lineNumber ?: return null
        val total = totalLines
        return if (total != null && total > 0) {
            "${formatNumber(line)} / ${formatNumber(total)}"
        } else {
            formatNumber(line)
        }
    }

    fun formatBatch(): String? {
        val batch = batchNumber ?: lineNumber?.let { ((it - 1) / 500) + 1 } ?: return null
        val totalB = totalBatches ?: totalLines?.let { if (it > 0) ((it - 1) / 500) + 1 else 1L }
        return if (totalB != null && totalB > 0) {
            "${formatNumber(batch)} / ${formatNumber(totalB)}"
        } else {
            formatNumber(batch)
        }
    }

    fun formatPositionInBatch(): String? {
        val pos = positionInBatch ?: lineNumber?.let { (((it - 1) % 500) + 1).toInt() } ?: return null
        return "$pos / 500"
    }

    companion object {
        fun formatNumber(number: Long): String {
            return NumberFormat.getNumberInstance().format(number)
        }
    }
}
