package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "authorized_credentials")
data class AuthorizedCredential(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ssid: String,
    val encryptedPassword: String,
    val securityType: String = "WPA2_PSK",
    val notes: String = "",
    val importedFrom: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)

data class ImportedEntry(
    val ssid: String,
    val password: String,
    val securityType: WifiSecurityType = WifiSecurityType.WPA2_PSK,
    val isValid: Boolean = true
)
