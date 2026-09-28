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
     * Supports formats like:
     * - SSID,Password
     * - SSID,Password,SecurityType
     * - SSID=Password
     * - SSID:Password
     * - "SSID","Password"
     */
    fun parseUri(context: Context, uri: Uri): ParseResult {
        val entries = mutableListOf<ImportedEntry>()
        var totalLines = 0
        var skippedLines = 0

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var isFirstLine = true
                    reader.forEachLine { rawLine ->
                        totalLines++
                        val line = rawLine.trim()
                        if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                            return@forEachLine
                        }

                        // Check if first line is a CSV/TXT header
                        if (isFirstLine && isHeaderLine(line)) {
                            isFirstLine = false
                            return@forEachLine
                        }
                        isFirstLine = false

                        val parsed = parseLine(line)
                        if (parsed != null && parsed.ssid.isNotBlank()) {
                            entries.add(parsed)
                        } else {
                            skippedLines++
                        }
                    }
                }
            }
            return ParseResult.Success(entries, totalLines, skippedLines)
        } catch (e: Exception) {
            return ParseResult.Error(e.localizedMessage ?: "Failed to read file")
        }
    }

    private fun isHeaderLine(line: String): Boolean {
        val upper = line.uppercase()
        return (upper.contains("SSID") || upper.contains("WIFI") || upper.contains("NETWORK")) &&
                (upper.contains("PASS") || upper.contains("KEY") || upper.contains("SECRET"))
    }

    private fun parseLine(line: String): ImportedEntry? {
        // Try comma, semicolon, tab, equals, colon delimiters
        val delimiter = when {
            line.contains(",") -> ','
            line.contains(";") -> ';'
            line.contains("\t") -> '\t'
            line.contains("=") -> '='
            line.contains(":") -> ':'
            else -> return null
        }

        val tokens = splitCsvLine(line, delimiter)
        if (tokens.size >= 2) {
            val ssid = cleanToken(tokens[0])
            val password = cleanToken(tokens[1])
            val securityStr = if (tokens.size >= 3) cleanToken(tokens[2]) else "WPA2"
            val securityType = WifiSecurityType.fromCapabilities(securityStr)

            if (ssid.isNotEmpty()) {
                return ImportedEntry(
                    ssid = ssid,
                    password = password,
                    securityType = securityType,
                    isValid = true
                )
            }
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
