package com.example.data.preference

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ImportCheckpoint(
    val uriString: String,
    val fileName: String,
    val byteOffset: Long,
    val processedEntries: Long,
    val totalEstimatedEntries: Long,
    val totalBytes: Long,
    val status: String
)

class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "wifi_manager_prefs",
        Context.MODE_PRIVATE
    )

    private val _backgroundMonitoring = MutableStateFlow(
        prefs.getBoolean(KEY_BACKGROUND_MONITORING, false)
    )
    val backgroundMonitoring: StateFlow<Boolean> = _backgroundMonitoring.asStateFlow()

    private val _resumeOnBoot = MutableStateFlow(
        prefs.getBoolean(KEY_RESUME_ON_BOOT, false)
    )
    val resumeOnBoot: StateFlow<Boolean> = _resumeOnBoot.asStateFlow()

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _lastPasswordFileUri = MutableStateFlow(
        prefs.getString(KEY_LAST_PASSWORD_FILE_URI, null)
    )
    val lastPasswordFileUri: StateFlow<String?> = _lastPasswordFileUri.asStateFlow()

    private val _lastPasswordFileName = MutableStateFlow(
        prefs.getString(KEY_LAST_PASSWORD_FILE_NAME, null)
    )
    val lastPasswordFileName: StateFlow<String?> = _lastPasswordFileName.asStateFlow()

    fun saveLastPasswordFileInfo(uriString: String?, fileName: String?) {
        prefs.edit()
            .putString(KEY_LAST_PASSWORD_FILE_URI, uriString)
            .putString(KEY_LAST_PASSWORD_FILE_NAME, fileName)
            .apply()
        _lastPasswordFileUri.value = uriString
        _lastPasswordFileName.value = fileName
    }

    fun clearLastPasswordFileInfo() {
        prefs.edit()
            .remove(KEY_LAST_PASSWORD_FILE_URI)
            .remove(KEY_LAST_PASSWORD_FILE_NAME)
            .apply()
        _lastPasswordFileUri.value = null
        _lastPasswordFileName.value = null
    }

    fun saveImportCheckpoint(
        uriString: String,
        fileName: String,
        byteOffset: Long,
        processedEntries: Long,
        totalEstimatedEntries: Long,
        totalBytes: Long,
        status: String
    ) {
        prefs.edit()
            .putString(KEY_CHECKPOINT_URI, uriString)
            .putString(KEY_CHECKPOINT_FILE_NAME, fileName)
            .putLong(KEY_CHECKPOINT_BYTE_OFFSET, byteOffset)
            .putLong(KEY_CHECKPOINT_PROCESSED_ENTRIES, processedEntries)
            .putLong(KEY_CHECKPOINT_TOTAL_ESTIMATED, totalEstimatedEntries)
            .putLong(KEY_CHECKPOINT_TOTAL_BYTES, totalBytes)
            .putString(KEY_CHECKPOINT_STATUS, status)
            .apply()
    }

    fun getImportCheckpoint(): ImportCheckpoint? {
        val uri = prefs.getString(KEY_CHECKPOINT_URI, null) ?: return null
        val fileName = prefs.getString(KEY_CHECKPOINT_FILE_NAME, "") ?: ""
        val byteOffset = prefs.getLong(KEY_CHECKPOINT_BYTE_OFFSET, 0L)
        val processed = prefs.getLong(KEY_CHECKPOINT_PROCESSED_ENTRIES, 0L)
        val totalEstimated = prefs.getLong(KEY_CHECKPOINT_TOTAL_ESTIMATED, 0L)
        val totalBytes = prefs.getLong(KEY_CHECKPOINT_TOTAL_BYTES, 0L)
        val status = prefs.getString(KEY_CHECKPOINT_STATUS, "PAUSED") ?: "PAUSED"

        return ImportCheckpoint(
            uriString = uri,
            fileName = fileName,
            byteOffset = byteOffset,
            processedEntries = processed,
            totalEstimatedEntries = totalEstimated,
            totalBytes = totalBytes,
            status = status
        )
    }

    fun updateCheckpointStatus(status: String) {
        prefs.edit().putString(KEY_CHECKPOINT_STATUS, status).apply()
    }

    fun clearImportCheckpoint() {
        prefs.edit()
            .remove(KEY_CHECKPOINT_URI)
            .remove(KEY_CHECKPOINT_FILE_NAME)
            .remove(KEY_CHECKPOINT_BYTE_OFFSET)
            .remove(KEY_CHECKPOINT_PROCESSED_ENTRIES)
            .remove(KEY_CHECKPOINT_TOTAL_ESTIMATED)
            .remove(KEY_CHECKPOINT_TOTAL_BYTES)
            .remove(KEY_CHECKPOINT_STATUS)
            .apply()
    }

    fun setBackgroundMonitoring(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_MONITORING, enabled).apply()
        _backgroundMonitoring.value = enabled
    }

    fun setResumeOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RESUME_ON_BOOT, enabled).apply()
        _resumeOnBoot.value = enabled
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    fun isBackgroundMonitoringEnabled(): Boolean {
        return prefs.getBoolean(KEY_BACKGROUND_MONITORING, false)
    }

    companion object {
        const val KEY_BACKGROUND_MONITORING = "bg_monitoring"
        const val KEY_RESUME_ON_BOOT = "resume_on_boot"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LAST_PASSWORD_FILE_URI = "last_pwd_file_uri"
        const val KEY_LAST_PASSWORD_FILE_NAME = "last_pwd_file_name"

        const val KEY_CHECKPOINT_URI = "checkpoint_uri"
        const val KEY_CHECKPOINT_FILE_NAME = "checkpoint_file_name"
        const val KEY_CHECKPOINT_BYTE_OFFSET = "checkpoint_byte_offset"
        const val KEY_CHECKPOINT_PROCESSED_ENTRIES = "checkpoint_processed_entries"
        const val KEY_CHECKPOINT_TOTAL_ESTIMATED = "checkpoint_total_estimated"
        const val KEY_CHECKPOINT_TOTAL_BYTES = "checkpoint_total_bytes"
        const val KEY_CHECKPOINT_STATUS = "checkpoint_status"

        const val THEME_SYSTEM = "system"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
    }
}
