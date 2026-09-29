package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.preference.UserPreferencesRepository
import com.example.model.BatchPasswordEntry
import com.example.model.ImportProgressStats
import com.example.model.PasswordBatchStatus
import com.example.model.PasswordBatchUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class PasswordBatchRepository(private val context: Context) {

    companion object {
        const val BATCH_SIZE = 500
        private const val INDEX_FILE_NAME = "password_batch_index.bin"
        private const val BUFFER_SIZE = 65536 // 64 KB read buffer
        private const val PROGRESS_THROTTLE_MS = 250L // 250ms update interval for UI & notification
        private const val CHECKPOINT_INTERVAL_ENTRIES = 50000L // save checkpoint every 50,000 entries
    }

    private val prefs = UserPreferencesRepository(context)
    private val indexFile: File
        get() = File(context.cacheDir, INDEX_FILE_NAME)

    private val _batchUiState = MutableStateFlow(PasswordBatchUiState())
    val batchUiState: StateFlow<PasswordBatchUiState> = _batchUiState.asStateFlow()

    private val isPauseRequested = AtomicBoolean(false)
    private val isStopRequested = AtomicBoolean(false)

    // Cached in-memory batch offsets (one Long = 8 bytes per 500 entries)
    // 10,000,000 entries = 20,000 Longs = only 160 KB RAM!
    private val batchOffsets = ArrayList<Long>()

    init {
        restoreLastKnownState()
    }

    private fun restoreLastKnownState() {
        val diskIndex = loadIndexFromDisk()
        val checkpoint = prefs.getImportCheckpoint()

        if (diskIndex != null) {
            val (totalEntries, totalBatches, offsets) = diskIndex
            batchOffsets.clear()
            batchOffsets.addAll(offsets)

            val fileName = prefs.lastPasswordFileName.value ?: "passwords.txt"
            val fileUri = prefs.lastPasswordFileUri.value ?: ""

            _batchUiState.value = _batchUiState.value.copy(
                status = PasswordBatchStatus.READY,
                fileName = fileName,
                fileUri = fileUri,
                totalEntries = totalEntries,
                totalBatches = totalBatches,
                currentBatch = 1,
                hasResumableCheckpoint = false,
                infoMessage = "Loaded index for $fileName ($totalEntries entries ready)"
            )
        } else if (checkpoint != null && checkpoint.byteOffset > 0L) {
            _batchUiState.value = _batchUiState.value.copy(
                status = PasswordBatchStatus.PAUSED,
                fileName = checkpoint.fileName,
                fileUri = checkpoint.uriString,
                totalEntries = checkpoint.totalEstimatedEntries,
                hasResumableCheckpoint = true,
                importProgress = ImportProgressStats(
                    processedEntries = checkpoint.processedEntries,
                    totalEntries = checkpoint.totalEstimatedEntries,
                    currentByteOffset = checkpoint.byteOffset,
                    totalBytes = checkpoint.totalBytes,
                    percentage = if (checkpoint.totalBytes > 0) {
                        (checkpoint.byteOffset.toFloat() / checkpoint.totalBytes.toFloat()) * 100f
                    } else 0f
                ),
                infoMessage = "Paused import available for ${checkpoint.fileName} (${checkpoint.processedEntries} processed)"
            )
        }
    }

    fun requestPause() {
        isPauseRequested.set(true)
    }

    fun requestStop() {
        isStopRequested.set(true)
    }

    /**
     * Streams through the source file on Dispatchers.IO to index entries.
     * Supports initial start or resuming from saved checkpoint.
     * Strictly reads locally — NEVER performs any Wi-Fi guessing or connections.
     */
    suspend fun processFile(
        uri: Uri,
        resumeFromCheckpoint: Boolean = false,
        onProgressUpdate: ((ImportProgressStats) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        isPauseRequested.set(false)
        isStopRequested.set(false)

        val fileName = getFileName(uri)
        val totalBytes = getFileSize(uri)

        var entryCount = 0L
        var globalByteOffset = 0L
        var lineStartByteOffset = 0L
        var hasCharsInLine = false

        if (resumeFromCheckpoint) {
            val checkpoint = prefs.getImportCheckpoint()
            if (checkpoint != null && checkpoint.uriString == uri.toString()) {
                entryCount = checkpoint.processedEntries
                globalByteOffset = checkpoint.byteOffset
                lineStartByteOffset = checkpoint.byteOffset
                val loaded = loadIndexFromDisk()
                if (loaded != null) {
                    batchOffsets.clear()
                    batchOffsets.addAll(loaded.third)
                }
            } else {
                batchOffsets.clear()
            }
        } else {
            batchOffsets.clear()
            clearCachedIndex()
            prefs.clearImportCheckpoint()
        }

        prefs.saveLastPasswordFileInfo(uri.toString(), fileName)

        _batchUiState.value = _batchUiState.value.copy(
            status = PasswordBatchStatus.INDEXING,
            fileName = fileName,
            fileUri = uri.toString(),
            errorMessage = null,
            infoMessage = if (resumeFromCheckpoint) "Resuming file indexing..." else "Starting background file indexing...",
            hasResumableCheckpoint = false
        )

        val startTime = System.currentTimeMillis()
        var lastProgressUpdateTime = startTime
        var lastCheckpointEntryCount = entryCount

        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open file stream for $fileName")

            if (globalByteOffset > 0L) {
                // Seek or skip to resume position
                skipFully(inputStream, globalByteOffset)
            }

            BufferedInputStream(inputStream, BUFFER_SIZE).use { bis ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int

                while (bis.read(buffer).also { bytesRead = it } != -1) {
                    currentCoroutineContext().ensureActive()

                    // Check for user-initiated stop
                    if (isStopRequested.get()) {
                        saveCheckpoint(uri, fileName, globalByteOffset, entryCount, totalBytes, "STOPPED")
                        _batchUiState.value = _batchUiState.value.copy(
                            status = PasswordBatchStatus.STOPPED,
                            infoMessage = "File import stopped by user."
                        )
                        return@withContext false
                    }

                    // Check for user-initiated pause
                    if (isPauseRequested.get()) {
                        saveCheckpoint(uri, fileName, lineStartByteOffset, entryCount, totalBytes, "PAUSED")
                        val totalBatches = maxOf(1, ((entryCount + BATCH_SIZE - 1) / BATCH_SIZE).toInt())
                        saveIndexToDisk(entryCount, totalBatches, batchOffsets)
                        _batchUiState.value = _batchUiState.value.copy(
                            status = PasswordBatchStatus.PAUSED,
                            hasResumableCheckpoint = true,
                            infoMessage = "File import paused at ${formatCount(entryCount)} entries."
                        )
                        return@withContext false
                    }

                    for (i in 0 until bytesRead) {
                        val b = buffer[i]
                        val currentBytePos = globalByteOffset + i

                        if (b == '\n'.code.toByte()) {
                            if (hasCharsInLine) {
                                if (entryCount % BATCH_SIZE == 0L) {
                                    batchOffsets.add(lineStartByteOffset)
                                }
                                entryCount++
                                hasCharsInLine = false
                            }
                            lineStartByteOffset = currentBytePos + 1
                        } else if (b == '\r'.code.toByte()) {
                            if (hasCharsInLine) {
                                if (entryCount % BATCH_SIZE == 0L) {
                                    batchOffsets.add(lineStartByteOffset)
                                }
                                entryCount++
                                hasCharsInLine = false
                            }
                            lineStartByteOffset = currentBytePos + 1
                        } else {
                            if (b > ' '.code.toByte()) {
                                hasCharsInLine = true
                            }
                        }
                    }

                    globalByteOffset += bytesRead

                    // Periodic disk checkpointing for crash resilience
                    if (entryCount - lastCheckpointEntryCount >= CHECKPOINT_INTERVAL_ENTRIES) {
                        saveCheckpoint(uri, fileName, lineStartByteOffset, entryCount, totalBytes, "INDEXING")
                        val currentBatches = maxOf(1, ((entryCount + BATCH_SIZE - 1) / BATCH_SIZE).toInt())
                        saveIndexToDisk(entryCount, currentBatches, batchOffsets)
                        lastCheckpointEntryCount = entryCount
                    }

                    // Throttled UI & notification progress emission
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdateTime >= PROGRESS_THROTTLE_MS) {
                        val elapsedSec = (now - startTime) / 1000f
                        val speed = if (elapsedSec > 0.2f) {
                            ((entryCount - (if (resumeFromCheckpoint) 0L else 0L)) / elapsedSec).toLong()
                        } else 0L

                        val percentage = if (totalBytes > 0) {
                            (globalByteOffset.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) * 100f
                        } else 0f

                        val estimatedTotal = if (percentage > 3f && totalBytes > 0) {
                            ((entryCount.toDouble() / globalByteOffset.toDouble()) * totalBytes).toLong()
                        } else {
                            entryCount
                        }
                        val remaining = maxOf(0L, estimatedTotal - entryCount)
                        val etaSec = if (speed > 0 && remaining > 0) remaining / speed else 0L
                        val currentBatchNum = ((entryCount / BATCH_SIZE) + 1).toInt()
                        val estTotalBatches = maxOf(1, ((estimatedTotal + BATCH_SIZE - 1) / BATCH_SIZE).toInt())

                        val stats = ImportProgressStats(
                            processedEntries = entryCount,
                            totalEntries = if (estimatedTotal > entryCount) estimatedTotal else entryCount,
                            remainingEntries = remaining,
                            percentage = percentage,
                            currentBatch = currentBatchNum,
                            totalBatches = estTotalBatches,
                            speedEntriesPerSec = speed,
                            etaSeconds = etaSec,
                            currentByteOffset = globalByteOffset,
                            totalBytes = totalBytes
                        )

                        _batchUiState.value = _batchUiState.value.copy(
                            importProgress = stats,
                            totalEntries = stats.totalEntries,
                            totalBatches = estTotalBatches
                        )

                        onProgressUpdate?.invoke(stats)
                        lastProgressUpdateTime = now
                    }
                }

                // Handle trailing line
                if (hasCharsInLine) {
                    if (entryCount % BATCH_SIZE == 0L) {
                        batchOffsets.add(lineStartByteOffset)
                    }
                    entryCount++
                }
            }

            if (entryCount == 0L) {
                _batchUiState.value = _batchUiState.value.copy(
                    status = PasswordBatchStatus.ERROR,
                    errorMessage = "No valid password entries found in file: $fileName"
                )
                return@withContext false
            }

            val totalBatches = maxOf(1, ((entryCount + BATCH_SIZE - 1) / BATCH_SIZE).toInt())
            saveIndexToDisk(entryCount, totalBatches, batchOffsets)
            prefs.clearImportCheckpoint()

            val finalStats = ImportProgressStats(
                processedEntries = entryCount,
                totalEntries = entryCount,
                remainingEntries = 0L,
                percentage = 100f,
                currentBatch = 1,
                totalBatches = totalBatches,
                speedEntriesPerSec = if ((System.currentTimeMillis() - startTime) > 0) {
                    (entryCount * 1000 / (System.currentTimeMillis() - startTime))
                } else 0L,
                etaSeconds = 0L,
                currentByteOffset = totalBytes,
                totalBytes = totalBytes
            )

            // Load first batch for immediate view
            val firstBatch = loadBatchInternal(uri, 1, batchOffsets, entryCount)

            _batchUiState.value = _batchUiState.value.copy(
                status = PasswordBatchStatus.READY,
                fileName = fileName,
                fileUri = uri.toString(),
                totalEntries = entryCount,
                totalBatches = totalBatches,
                currentBatch = 1,
                currentEntries = firstBatch,
                selectedEntry = firstBatch.firstOrNull(),
                importProgress = finalStats,
                hasResumableCheckpoint = false,
                infoMessage = "Successfully indexed ${formatCount(entryCount)} passwords ($totalBatches batches)."
            )

            onProgressUpdate?.invoke(finalStats)
            return@withContext true

        } catch (e: CancellationException) {
            saveCheckpoint(uri, fileName, globalByteOffset, entryCount, totalBytes, "STOPPED")
            _batchUiState.value = _batchUiState.value.copy(
                status = PasswordBatchStatus.STOPPED,
                infoMessage = "Operation cancelled."
            )
            return@withContext false
        } catch (e: Exception) {
            saveCheckpoint(uri, fileName, globalByteOffset, entryCount, totalBytes, "ERROR")
            _batchUiState.value = _batchUiState.value.copy(
                status = PasswordBatchStatus.ERROR,
                errorMessage = "File processing error: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
            return@withContext false
        } finally {
            try {
                inputStream?.close()
            } catch (_: Exception) {}
        }
    }

    private fun saveCheckpoint(
        uri: Uri,
        fileName: String,
        byteOffset: Long,
        processedEntries: Long,
        totalBytes: Long,
        status: String
    ) {
        val estimatedTotal = if (totalBytes > 0 && byteOffset > 0) {
            ((processedEntries.toDouble() / byteOffset.toDouble()) * totalBytes).toLong()
        } else processedEntries

        prefs.saveImportCheckpoint(
            uriString = uri.toString(),
            fileName = fileName,
            byteOffset = byteOffset,
            processedEntries = processedEntries,
            totalEstimatedEntries = estimatedTotal,
            totalBytes = totalBytes,
            status = status
        )
    }

    /**
     * Reads exactly one 500-entry batch from disk by seeking directly to its byte offset.
     * Bounded memory footprint (max 500 strings).
     */
    suspend fun loadBatch(batchNumber: Int): List<BatchPasswordEntry> = withContext(Dispatchers.IO) {
        val uriStr = _batchUiState.value.fileUri
        if (uriStr.isBlank() || batchOffsets.isEmpty()) return@withContext emptyList()

        _batchUiState.value = _batchUiState.value.copy(isLoadingBatch = true)
        val uri = Uri.parse(uriStr)
        val entries = loadBatchInternal(uri, batchNumber, batchOffsets, _batchUiState.value.totalEntries)

        _batchUiState.value = _batchUiState.value.copy(
            isLoadingBatch = false,
            currentBatch = batchNumber,
            currentEntries = entries,
            selectedEntry = entries.firstOrNull()
        )
        entries
    }

    private fun loadBatchInternal(
        uri: Uri,
        batchNumber: Int,
        offsets: List<Long>,
        totalEntries: Long
    ): List<BatchPasswordEntry> {
        if (batchNumber < 1 || batchNumber > offsets.size) return emptyList()

        val startOffset = offsets[batchNumber - 1]
        val entries = ArrayList<BatchPasswordEntry>(BATCH_SIZE)

        try {
            val pfd = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (_: Exception) {
                null
            }

            val reader: BufferedReader = if (pfd != null) {
                val fis = FileInputStream(pfd.fileDescriptor)
                fis.channel.position(startOffset)
                BufferedReader(InputStreamReader(fis, StandardCharsets.UTF_8), 8192)
            } else {
                val stream = context.contentResolver.openInputStream(uri) ?: return emptyList()
                val bis = BufferedInputStream(stream, 8192)
                skipFully(bis, startOffset)
                BufferedReader(InputStreamReader(bis, StandardCharsets.UTF_8), 8192)
            }

            reader.use { br ->
                var localIndex = 1
                val globalStart = (batchNumber - 1) * BATCH_SIZE.toLong()

                while (localIndex <= BATCH_SIZE) {
                    val line = br.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        entries.add(
                            BatchPasswordEntry(
                                globalIndex = globalStart + localIndex,
                                batchIndex = localIndex,
                                password = trimmed
                            )
                        )
                        localIndex++
                        if (totalEntries > 0 && globalStart + localIndex - 1 >= totalEntries) {
                            break
                        }
                    }
                }
            }
            pfd?.close()
        } catch (_: Exception) {}

        return entries
    }

    /**
     * Disk-based streaming search without loading all lines into memory.
     */
    suspend fun searchStreaming(query: String, maxResults: Int = 100): List<BatchPasswordEntry> = withContext(Dispatchers.IO) {
        val uriStr = _batchUiState.value.fileUri
        if (uriStr.isBlank() || query.isBlank()) {
            _batchUiState.value = _batchUiState.value.copy(
                isSearching = false,
                searchQuery = query,
                searchResults = emptyList(),
                searchProgress = 0f
            )
            return@withContext emptyList()
        }

        _batchUiState.value = _batchUiState.value.copy(
            isSearching = true,
            searchQuery = query,
            searchResults = emptyList(),
            searchProgress = 0f
        )

        val uri = Uri.parse(uriStr)
        val results = ArrayList<BatchPasswordEntry>()
        val totalBytes = getFileSize(uri)
        var globalIndex = 0L
        var processedBytes = 0L

        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext emptyList()
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8), 16384).use { reader ->
                var line: String?
                var linesSinceProgress = 0

                while (reader.readLine().also { line = it } != null) {
                    currentCoroutineContext().ensureActive()
                    val raw = line!!
                    processedBytes += raw.toByteArray(StandardCharsets.UTF_8).size + 1
                    val trimmed = raw.trim()

                    if (trimmed.isNotEmpty()) {
                        globalIndex++
                        if (trimmed.contains(query, ignoreCase = true)) {
                            val batchIdx = ((globalIndex - 1) % BATCH_SIZE).toInt() + 1
                            results.add(
                                BatchPasswordEntry(
                                    globalIndex = globalIndex,
                                    batchIndex = batchIdx,
                                    password = trimmed
                                )
                            )
                            if (results.size >= maxResults) break
                        }
                    }

                    linesSinceProgress++
                    if (linesSinceProgress >= 2000) {
                        linesSinceProgress = 0
                        val progress = if (totalBytes > 0) {
                            (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        _batchUiState.value = _batchUiState.value.copy(
                            searchProgress = progress,
                            searchResults = ArrayList(results)
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        _batchUiState.value = _batchUiState.value.copy(
            isSearching = false,
            searchProgress = 1f,
            searchResults = results
        )
        results
    }

    fun selectEntry(entry: BatchPasswordEntry?) {
        _batchUiState.value = _batchUiState.value.copy(selectedEntry = entry)
    }

    fun clearSearch() {
        _batchUiState.value = _batchUiState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false,
            searchProgress = 0f
        )
    }

    fun clearAllData() {
        batchOffsets.clear()
        clearCachedIndex()
        prefs.clearLastPasswordFileInfo()
        prefs.clearImportCheckpoint()
        _batchUiState.value = PasswordBatchUiState()
    }

    fun dismissInfoMessage() {
        _batchUiState.value = _batchUiState.value.copy(infoMessage = null)
    }

    fun dismissErrorMessage() {
        _batchUiState.value = _batchUiState.value.copy(errorMessage = null)
    }

    private fun skipFully(inputStream: InputStream, targetBytes: Long) {
        var remaining = targetBytes
        val skipBuffer = ByteArray(8192)
        while (remaining > 0) {
            val skipped = inputStream.skip(remaining)
            if (skipped <= 0) {
                val bytesToRead = minOf(remaining, skipBuffer.size.toLong()).toInt()
                val read = inputStream.read(skipBuffer, 0, bytesToRead)
                if (read == -1) break
                remaining -= read
            } else {
                remaining -= skipped
            }
        }
    }

    private fun saveIndexToDisk(
        totalEntries: Long,
        totalBatches: Int,
        offsets: List<Long>
    ) {
        try {
            DataOutputStream(indexFile.outputStream().buffered()).use { dos ->
                dos.writeLong(totalEntries)
                dos.writeInt(totalBatches)
                for (offset in offsets) {
                    dos.writeLong(offset)
                }
            }
        } catch (_: Exception) {}
    }

    fun loadIndexFromDisk(): Triple<Long, Int, List<Long>>? {
        if (!indexFile.exists() || indexFile.length() < 12) return null
        return try {
            DataInputStream(indexFile.inputStream().buffered()).use { dis ->
                val totalEntries = dis.readLong()
                val totalBatches = dis.readInt()
                val offsets = ArrayList<Long>(totalBatches)
                for (i in 0 until totalBatches) {
                    offsets.add(dis.readLong())
                }
                Triple(totalEntries, totalBatches, offsets)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun clearCachedIndex() {
        try {
            if (indexFile.exists()) {
                indexFile.delete()
            }
        } catch (_: Exception) {}
    }

    private fun getFileName(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (colIndex != -1) {
                            name = cursor.getString(colIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return name ?: uri.lastPathSegment ?: "passwords.txt"
    }

    private fun getFileSize(uri: Uri): Long {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    return pfd.statSize
                }
            } catch (_: Exception) {}
        }
        return 0L
    }

    private fun formatCount(number: Long): String {
        return java.text.NumberFormat.getNumberInstance().format(number)
    }
}
