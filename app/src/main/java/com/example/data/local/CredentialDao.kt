package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.AuthorizedCredential
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {
    @Query("SELECT * FROM authorized_credentials ORDER BY createdAt DESC")
    fun getAllCredentials(): Flow<List<AuthorizedCredential>>

    @Query("SELECT * FROM authorized_credentials WHERE ssid = :ssid COLLATE NOCASE LIMIT 1")
    suspend fun getCredentialBySsid(ssid: String): AuthorizedCredential?

    @Query("SELECT * FROM authorized_credentials WHERE id = :id")
    suspend fun getCredentialById(id: Long): AuthorizedCredential?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: AuthorizedCredential): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(credentials: List<AuthorizedCredential>): List<Long>

    @Update
    suspend fun updateCredential(credential: AuthorizedCredential)

    @Query("DELETE FROM authorized_credentials WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM authorized_credentials WHERE ssid = :ssid")
    suspend fun deleteBySsid(ssid: String)

    @Query("DELETE FROM authorized_credentials")
    suspend fun clearAll()
}
