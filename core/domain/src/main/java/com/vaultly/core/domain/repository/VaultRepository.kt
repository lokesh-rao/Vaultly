package com.vaultly.core.domain.repository

import com.vaultly.core.domain.model.PasswordEntry
import com.vaultly.core.domain.model.SecureNote
import com.vaultly.core.domain.model.VaultHealth
import com.vaultly.core.domain.model.VaultSearchResult
import com.vaultly.core.domain.util.DataError
import com.vaultly.core.domain.util.EmptyResult
import com.vaultly.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    fun observePasswords(): Flow<List<PasswordEntry>>
    fun observeNotes(): Flow<List<SecureNote>>
    fun observeFavorites(): Flow<List<VaultSearchResult>>
    fun observeVaultHealth(): Flow<VaultHealth>

    suspend fun getPassword(id: String): Result<PasswordEntry, DataError.Local>
    suspend fun upsertPassword(entry: PasswordEntry): EmptyResult<DataError.Local>
    suspend fun deletePassword(id: String): EmptyResult<DataError.Local>

    suspend fun getNote(id: String): Result<SecureNote, DataError.Local>
    suspend fun upsertNote(note: SecureNote): EmptyResult<DataError.Local>
    suspend fun deleteNote(id: String): EmptyResult<DataError.Local>

    suspend fun search(query: String): Result<List<VaultSearchResult>, DataError.Local>
    suspend fun exportEncryptedBackup(): Result<ByteArray, DataError.Local>
    suspend fun importEncryptedBackup(bytes: ByteArray, masterPassword: String): EmptyResult<DataError.Local>
}
