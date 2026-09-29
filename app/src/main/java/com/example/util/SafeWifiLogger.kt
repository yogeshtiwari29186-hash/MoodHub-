package com.example.util

import android.util.Log
import com.example.BuildConfig

/**
 * Safe development logger for Wi-Fi operations that guarantees sensitive credentials
 * (passwords, pre-shared keys, PINs) are NEVER logged in plaintext.
 */
object SafeWifiLogger {

    private const val DEFAULT_TAG = "SafeWifiLogger"

    /**
     * Safely masks a password string, revealing only first and last characters if length > 2.
     * Never returns the raw secret.
     */
    fun mask(secret: String?): String {
        if (secret.isNullOrEmpty()) return "<empty>"
        if (secret.length <= 2) return "••••"
        val maskedMiddle = "•".repeat((secret.length - 2).coerceIn(4, 16))
        return "${secret.first()}$maskedMiddle${secret.last()}"
    }

    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String = DEFAULT_TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, message)
        }
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, message, throwable)
        }
    }
}
