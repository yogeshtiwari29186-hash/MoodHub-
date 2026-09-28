package com.example.data.repository

import com.example.data.crypto.CredentialSecurity
import com.example.data.local.CredentialDao
import com.example.model.AuthorizedCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CredentialRepository(private val credentialDao: CredentialDao) {

    val allCredentials: Flow<List<AuthorizedCredential>> = credentialDao.getAllCredentials()

    suspend fun saveCredential(
        ssid: String,
        plainPassword: String,
        securityType: String = "WPA2_PSK",
        notes: String = "",
        importedFrom: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val encrypted = CredentialSecurity.encrypt(plainPassword)
        val credential = AuthorizedCredential(
            ssid = ssid.trim(),
            encryptedPassword = encrypted,
            securityType = securityType,
            notes = notes,
            importedFrom = importedFrom,
            createdAt = System.currentTimeMillis()
        )
        credentialDao.insertCredential(credential)
    }

    suspend fun saveBatch(credentials: List<Pair<String, String>>, sourceName: String? = null): Int =
        withContext(Dispatchers.IO) {
            val entities = credentials.map { (ssid, pass) ->
                AuthorizedCredential(
                    ssid = ssid.trim(),
                    encryptedPassword = CredentialSecurity.encrypt(pass),
                    securityType = "WPA2_PSK",
                    importedFrom = sourceName,
                    createdAt = System.currentTimeMillis()
                )
            }
            credentialDao.insertAll(entities).size
        }

    fun decryptPassword(credential: AuthorizedCredential): String {
        return CredentialSecurity.decrypt(credential.encryptedPassword)
    }

    suspend fun getCredentialForSsid(ssid: String): AuthorizedCredential? =
        withContext(Dispatchers.IO) {
            credentialDao.getCredentialBySsid(ssid.trim())
        }

    suspend fun updateCredential(id: Long, newSsid: String, newPassword: String, notes: String = "") = withContext(Dispatchers.IO) {
        val existing = credentialDao.getCredentialById(id) ?: return@withContext
        val updated = existing.copy(
            ssid = newSsid.trim(),
            encryptedPassword = CredentialSecurity.encrypt(newPassword),
            notes = notes.trim()
        )
        credentialDao.updateCredential(updated)
    }

    suspend fun deleteCredential(id: Long) = withContext(Dispatchers.IO) {
        credentialDao.deleteById(id)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        credentialDao.clearAll()
    }
}
