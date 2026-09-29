package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.BatchPasswordEntry
import com.example.model.ImportProgressStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

class PasswordBatchRepository(private val context: Context) {

    companion object {
        const val BATCH_SIZE = 500
        private const val INDEX_FILE_NAME = "password_batch_index.bin"
        private const val BUFFER_SIZE = 65536 // 64 KB read buffer
    }

    private val indexFile: File
        get() = File(context.cacheDir, INDEX_FILE_NAME)

    sealed class IndexResult {
        data class Success(
            val totalEntries: Long,
            val totalBatches: Int,
            val batchOffsets: List<Long>,
            val fileName: String
        ) : IndexResult()

        data class Error(val message: String) : IndexResult()
    }

    /**
     * Streams through the source file on Dispatchers.IO to build a lightweight
     * batch offset index (one 8-byte offset for every 500 entries).
     * Never loads the whole file into RAM.
     */
    suspend fun buildBatchIndex(
        uri: Uri,
        onProgress: (ImportProgressStats) -> Unit
    ): IndexResult = withContext(Dispatchers.IO) {
        val fileName = getFileName(uri)
        val totalBytes = getFileSize(uri)

        val batchOffsets = ArrayList<Long>()
        var entryCount = 0L
        var globalByteOffset = 0L
        var lineStartByteOffset = 0L
        var hasCharsInLine = false

        val startTime = System.currentTimeMillis()
        var lastProgressUpdateTime = startTime

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext IndexResult.Error("Unable to open file stream: $fileName")

            BufferedInputStream(inputStream, BUFFER_SIZE).use { bis ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int

                while (bis.read(buffer).also { bytesRead = it } != -1) {
                    currentCoroutineContext().ensureActive()

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
                            // Carriage return, lineStart will advance after next byte if \n follows
                            lineStartByteOffset = currentBytePos + 1
                        } else {
                            // Non-whitespace character check
                            if (b > ' '.code.toByte()) {
                                hasCharsInLine = true
                            }
                        }
                    }

                    globalByteOffset += bytesRead

                    // Throttle UI progress update (every 100ms) to avoid Compose recomposition storms
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdateTime >= 100L) {
                        val elapsedSec = (now - startTime) / 1000f
                        val speed = if (elapsedSec > 0.3f) (entryCount / elapsedSec).toLong() else 0L

                        val percentage = if (totalBytes > 0) {
                            (globalByteOffset.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) * 100f
                        } else 0f

                        val estimatedTotal = if (percentage > 5f && totalBytes > 0) {
                            ((entryCount.toDouble() / globalByteOffset.toDouble()) * totalBytes).toLong()
                        } else {
                            entryCount
                        }
                        val remaining = maxOf(0L, estimatedTotal - entryCount)

                        val etaSec = if (speed > 0 && remaining > 0) {
                            (remaining / speed)
                        } else 0L

                        val currentBatchNum = ((entryCount / BATCH_SIZE) + 1).toInt()
                        val estTotalBatches = maxOf(1, ((estimatedTotal + BATCH_SIZE - 1) / BATCH_SIZE).toInt())

                        onProgress(
                            ImportProgressStats(
                                processedEntries = entryCount,
                                totalEntries = if (estimatedTotal > entryCount) estimatedTotal else entryCount,
                                remainingEntries = remaining,
                                percentage = percentage,
                                currentBatch = currentBatchNum,
                                totalBatches = estTotalBatches,
                                speedEntriesPerSec = speed,
                                etaSeconds = etaSec
                            )
                        )
                        lastProgressUpdateTime = now
                    }
                }

                // Handle last line if file didn't end with a newline
                if (hasCharsInLine) {
                    if (entryCount % BATCH_SIZE == 0L) {
                        batchOffsets.add(lineStartByteOffset)
                    }
                    entryCount++
                }
            }

            if (entryCount == 0L) {
                return@withContext IndexResult.Error("No valid password entries found in file: $fileName")
            }

            val totalBatches = ((entryCount + BATCH_SIZE - 1) / BATCH_SIZE).toInt()

            // Save binary index to cache for fast persistence
            saveIndexToDisk(entryCount, totalBatches, batchOffsets)

            // Final 100% progress emission
            onProgress(
                ImportProgressStats(
                    processedEntries = entryCount,
                    totalEntries = entryCount,
                    remainingEntries = 0L,
                    percentage = 100f,
                    currentBatch = 1,
                    totalBatches = totalBatches,
                    speedEntriesPerSec = if ((System.currentTimeMillis() - startTime) > 0) {
                        (entryCount * 1000 / (System.currentTimeMillis() - startTime))
                    } else 0L,
                    etaSeconds = 0L
                )
            )

            IndexResult.Success(
                totalEntries = entryCount,
                totalBatches = totalBatches,
                batchOffsets = batchOffsets,
                fileName = fileName
            )
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            IndexResult.Error(e.localizedMessage ?: "Failed to index file")
        }
    }

    /**
     * Reads exactly one 500-entry batch from disk by seeking directly to its byte offset.
     * Keeps memory bounded to at most 500 string entries.
     */
    suspend fun loadBatch(
        uri: Uri,
        batchNumber: Int,
        batchOffsets: List<Long>,
        totalEntries: Long
    ): List<BatchPasswordEntry> = withContext(Dispatchers.IO) {
        if (batchNumber < 1 || batchNumber > batchOffsets.size) {
            return@withContext emptyList()
        }

        val startOffset = batchOffsets[batchNumber - 1]
        val entries = ArrayList<BatchPasswordEntry>(BATCH_SIZE)

        try {
            // Seek directly via FileChannel if available
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
                val stream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext emptyList()
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
                        if (globalStart + localIndex - 1 >= totalEntries) {
                            break
                        }
                    }
                }
            }
            pfd?.close()
        } catch (_: Exception) {
            // Return collected entries or empty
        }

        entries
    }

    /**
     * Disk-based streaming search without loading all lines into memory.
     * Returns matching entries with global line numbers.
     */
    suspend fun searchStreaming(
        uri: Uri,
        query: String,
        maxResults: Int = 100,
        onProgress: (Float) -> Unit
    ): List<BatchPasswordEntry> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

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
                        onProgress(progress)
                    }
                }
            }
        } catch (_: Exception) {
            // Handle error or cancellation gracefully
        }

        results
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

    fun clearCachedIndex() {
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
}
