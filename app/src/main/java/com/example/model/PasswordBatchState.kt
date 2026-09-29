package com.example.model

enum class PasswordBatchStatus {
    IDLE,
    INDEXING,
    PAUSED,
    STOPPED,
    READY,
    ERROR
}

data class ImportProgressStats(
    val processedEntries: Long = 0L,
    val totalEntries: Long = 0L,
    val remainingEntries: Long = 0L,
    val percentage: Float = 0f,
    val currentBatch: Int = 0,
    val totalBatches: Int = 0,
    val speedEntriesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val currentByteOffset: Long = 0L,
    val totalBytes: Long = 0L
)

data class PasswordBatchUiState(
    val status: PasswordBatchStatus = PasswordBatchStatus.IDLE,
    val fileName: String = "",
    val fileUri: String = "",
    val totalEntries: Long = 0L,
    val totalBatches: Int = 0,
    val currentBatch: Int = 1,
    val batchSize: Int = 500,
    val currentEntries: List<BatchPasswordEntry> = emptyList(),
    val selectedEntry: BatchPasswordEntry? = null,
    val isLoadingBatch: Boolean = false,
    val isAutoRunning: Boolean = false,
    val importProgress: ImportProgressStats? = null,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<BatchPasswordEntry> = emptyList(),
    val searchProgress: Float = 0f,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val hasResumableCheckpoint: Boolean = false
) {
    val currentRangeStart: Long
        get() = if (totalEntries == 0L) 0L else ((currentBatch - 1) * batchSize + 1L)

    val currentRangeEnd: Long
        get() = if (totalEntries == 0L) 0L else minOf(currentBatch.toLong() * batchSize, totalEntries)

    val progressPercentage: Int
        get() = if (totalBatches > 0) ((currentBatch.toFloat() / totalBatches.toFloat()) * 100).toInt() else 0
}
