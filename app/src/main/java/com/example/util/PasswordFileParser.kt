package com.example.util

import android.content.Context
import android.net.Uri
import com.example.model.ImportedEntry
import com.example.model.WifiSecurityType
import java.io.BufferedReader
import java.io.InputStreamReader

object PasswordFileParser {

    /**
     * Parses an input stream from a selected TXT or CSV Uri.
     * Supports formats:
     * 1. Single password per line TXT file (e.g. wordlist / password list):
     *    MySecretPass1
     *    MySecretPass2
     * 2. Key-value / CSV lines:
     *    SSID,Password
     *    SSID,Password,SecurityType
     *    SSID=Password
     *    SSID:Password
     * Preserves original line ordering.
     */
    fun parseUri(context: Context, uri: Uri): ParseResult {
        val entries = mutableListOf<ImportedEntry>()
        var totalLines = 0
        var skippedLines = 0

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var isFirstLine = true
                    var entryIndex = 0

                    reader.forEachLine { rawLine ->
                        totalLines++
                        val line = rawLine.trim()
                        if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                            return@forEachLine
                        }

                        // Check if first line is a CSV/TXT column header (e.g. "SSID,Password")
                        if (isFirstLine && isHeaderLine(line)) {
                            isFirstLine = false
                            return@forEachLine
                        }
                        isFirstLine = false

                        val parsed = parseLine(line, ++entryIndex)
                        if (parsed != null && parsed.password.isNotBlank()) {
                            entries.add(parsed)
                        } else {
                            skippedLines++
                        }
                    }
                }
            }

            val finalTotal = totalLines.toLong()
            val totalBatches = if (finalTotal > 0) ((finalTotal - 1) / 500) + 1 else 1L
            val finalizedEntries = entries.map { entry ->
                val lineNum = entry.lineNumber ?: 1L
                val batchNum = ((lineNum - 1) / 500) + 1
                val posInBatch = (((lineNum - 1) % 500) + 1).toInt()
                entry.copy(
                    totalLines = finalTotal,
                    batchNumber = batchNum,
                    totalBatches = totalBatches,
                    positionInBatch = posInBatch
                )
            }

            return ParseResult.Success(finalizedEntries, totalLines, skippedLines)
        } catch (e: Exception) {
            return ParseResult.Error(e.localizedMessage ?: "Failed to read file")
        }
    }

    private fun isHeaderLine(line: String): Boolean {
        val upper = line.uppercase()
        return (upper.contains("SSID") || upper.contains("WIFI") || upper.contains("NETWORK")) &&
                (upper.contains("PASS") || upper.contains("KEY") || upper.contains("SECRET"))
    }

    private fun parseLine(line: String, index: Int): ImportedEntry? {
        // Check if line contains a recognizable key-value or CSV delimiter
        val hasDelimiter = line.contains(",") || line.contains(";") || line.contains("\t") ||
                line.contains("=") || (line.contains(":") && !line.startsWith("http"))

        if (hasDelimiter) {
            val delimiter = when {
                line.contains(",") -> ','
                line.contains(";") -> ';'
                line.contains("\t") -> '\t'
                line.contains("=") -> '='
                line.contains(":") -> ':'
                else -> ','
            }

            val tokens = splitCsvLine(line, delimiter)
            if (tokens.size >= 2) {
                val first = cleanToken(tokens[0])
                val second = cleanToken(tokens[1])
                val securityStr = if (tokens.size >= 3) cleanToken(tokens[2]) else "WPA2"
                val securityType = WifiSecurityType.fromCapabilities(securityStr)

                if (first.isNotBlank() && second.isNotBlank()) {
                    return ImportedEntry(
                        ssid = first,
                        password = second,
                        securityType = securityType,
                        isValid = true,
                        lineNumber = index.toLong(),
                        source = "Imported List"
                    )
                }
            }
        }

        // Single password per line format
        val plainPassword = cleanToken(line)
        if (plainPassword.isNotBlank()) {
            return ImportedEntry(
                ssid = "Password #$index",
                password = plainPassword,
                securityType = WifiSecurityType.WPA2_PSK,
                isValid = true,
                lineNumber = index.toLong(),
                source = "Imported TXT"
            )
        }

        return null
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            if (ch == '\"') {
                inQuotes = !inQuotes
            } else if (ch == delimiter && !inQuotes) {
                tokens.add(sb.toString())
                sb.clear()
            } else {
                sb.append(ch)
            }
        }
        tokens.add(sb.toString())
        return tokens
    }

    private fun cleanToken(token: String): String {
        return token.trim().trim('\"', '\'')
    }

    sealed class ParseResult {
        data class Success(
            val entries: List<ImportedEntry>,
            val totalLinesRead: Int,
            val skippedLines: Int
        ) : ParseResult()

        data class Error(val message: String) : ParseResult()
    }
}
