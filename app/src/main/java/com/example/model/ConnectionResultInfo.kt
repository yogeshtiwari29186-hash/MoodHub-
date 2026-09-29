package com.example.model

import java.text.NumberFormat

/**
 * Tracks origin and location metadata for a Wi-Fi password candidate
 * (e.g. from an imported single-password-per-line TXT file or batch).
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
        val batch = batchNumber ?: return null
        val totalB = totalBatches
        return if (totalB != null && totalB > 0) {
            "${formatNumber(batch)} / ${formatNumber(totalB)}"
        } else {
            formatNumber(batch)
        }
    }

    fun formatPositionInBatch(): String? {
        val pos = positionInBatch ?: return null
        return "$pos / 500"
    }

    companion object {
        fun formatNumber(number: Long): String {
            return NumberFormat.getNumberInstance().format(number)
        }
    }
}

/**
 * Full information displayed on the Successful Authorized Connection Result Card
 * directly on the Wi-Fi connection screen.
 */
data class ConnectionResultInfo(
    val ssid: String,
    val password: String,
    val source: String, // "Imported TXT" or "Manually Entered"
    val passwordNumber: Long? = null,
    val lineNumber: Long? = null,
    val totalLines: Long? = null,
    val batchNumber: Long? = null,
    val totalBatches: Long? = null,
    val positionInBatch: Int? = null,
    val ipAddress: String? = null,
    val gateway: String? = null,
    val status: String = "Connected",
    val isConnected: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isImported: Boolean
        get() = source != "Manually Entered" && (passwordNumber != null || lineNumber != null)

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
        val batch = batchNumber ?: return null
        val totalB = totalBatches
        return if (totalB != null && totalB > 0) {
            "${formatNumber(batch)} / ${formatNumber(totalB)}"
        } else {
            formatNumber(batch)
        }
    }

    fun formatPositionInBatch(): String? {
        val pos = positionInBatch ?: return null
        return "$pos / 500"
    }

    companion object {
        fun formatNumber(number: Long): String {
            return NumberFormat.getNumberInstance().format(number)
        }
    }
}
