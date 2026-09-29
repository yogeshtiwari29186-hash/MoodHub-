package com.example.data.preference

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

        const val THEME_SYSTEM = "system"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
    }
}
