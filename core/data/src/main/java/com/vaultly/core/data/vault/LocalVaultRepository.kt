package com.vaultly.core.data.vault

import com.vaultly.core.data.mapper.toDomain
import com.vaultly.core.data.mapper.toEntity
import com.vaultly.core.data.preferences.VaultPreferences
import com.vaultly.core.database.dao.NoteDao
import com.vaultly.core.database.dao.PasswordDao
import com.vaultly.core.domain.model.PasswordEntry
import com.vaultly.core.domain.model.SecureNote
import com.vaultly.core.domain.model.VaultHealth
import com.vaultly.core.domain.model.VaultSearchResult
import com.vaultly.core.domain.password.VaultHealthCalculator
import com.vaultly.core.domain.repository.VaultRepository
import com.vaultly.core.domain.util.DataError
import com.vaultly.core.domain.util.EmptyResult
import com.vaultly.core.domain.util.Result
import com.vaultly.core.security.crypto.CryptoEngine
import com.vaultly.core.security.crypto.SessionKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class LocalVaultRepository(
    private val passwordDao: PasswordDao,
    private val noteDao: NoteDao,
    private val sessionKeyStore: SessionKeyStore,
    private val preferences: VaultPreferences
) : VaultRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun observePasswords(): Flow<List<PasswordEntry>> =
        passwordDao.observeAll().map { list ->
            val key = sessionKeyStore.get() ?: return@map emptyList()
            list.mapNotNull { runCatching { it.toDomain(key) }.getOrNull() }
        }

    override fun observeNotes(): Flow<List<SecureNote>> =
        noteDao.observeAll().map { list ->
            val key = sessionKeyStore.get() ?: return@map emptyList()
            list.mapNotNull { runCatching { it.toDomain(key) }.getOrNull() }
        }

    override fun observeFavorites(): Flow<List<VaultSearchResult>> =
        combine(observePasswords(), observeNotes()) { passwords, notes ->
            buildList {
                passwords.filter { it.favorite }.forEach { add(VaultSearchResult.Password(it)) }
                notes.filter { it.favorite }.forEach { add(VaultSearchResult.Note(it)) }
            }
        }

    override fun observeVaultHealth(): Flow<VaultHealth> =
        observePasswords().map { VaultHealthCalculator.calculate(it) }

    override suspend fun getPassword(id: String): Result<PasswordEntry, DataError.Local> {
        val key = sessionKeyStore.get() ?: return Result.Error(DataError.Local.UNKNOWN)
        val entity = passwordDao.getById(id) ?: return Result.Error(DataError.Local.NOT_FOUND)
        return try {
            Result.Success(entity.toDomain(key))
        } catch (_: Exception) {
            Result.Error(DataError.Local.CORRUPT)
        }
    }

    override suspend fun upsertPassword(entry: PasswordEntry): EmptyResult<DataError.Local> {
        val key = sessionKeyStore.get() ?: return Result.Error(DataError.Local.UNKNOWN)
        return try {
            val normalized = if (entry.id.isBlank()) {
                entry.copy(id = UUID.randomUUID().toString())
            } else entry
            passwordDao.upsert(normalized.toEntity(key))
            Result.Success(Unit)
        } catch (_: Exception) {
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun deletePassword(id: String): EmptyResult<DataError.Local> {
        return try {
            passwordDao.delete(id)
            Result.Success(Unit)
        } catch (_: Exception) {
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun getNote(id: String): Result<SecureNote, DataError.Local> {
        val key = sessionKeyStore.get() ?: return Result.Error(DataError.Local.UNKNOWN)
        val entity = noteDao.getById(id) ?: return Result.Error(DataError.Local.NOT_FOUND)
        return try {
            Result.Success(entity.toDomain(key))
        } catch (_: Exception) {
            Result.Error(DataError.Local.CORRUPT)
        }
    }

    override suspend fun upsertNote(note: SecureNote): EmptyResult<DataError.Local> {
        val key = sessionKeyStore.get() ?: return Result.Error(DataError.Local.UNKNOWN)
        return try {
            val normalized = if (note.id.isBlank()) {
                note.copy(id = UUID.randomUUID().toString())
            } else note
            noteDao.upsert(normalized.toEntity(key))
            Result.Success(Unit)
        } catch (_: Exception) {
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun deleteNote(id: String): EmptyResult<DataError.Local> {
        return try {
            noteDao.delete(id)
            Result.Success(Unit)
        } catch (_: Exception) {
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun search(query: String): Result<List<VaultSearchResult>, DataError.Local> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return Result.Success(emptyList())
        val passwords = observePasswords().first()
        val notes = observeNotes().first()
        val results = buildList {
            passwords.filter {
                it.title.lowercase().contains(q) ||
                    it.username.lowercase().contains(q) ||
                    it.url.lowercase().contains(q) ||
                    it.category.displayName.lowercase().contains(q) ||
                    it.tags.any { tag -> tag.lowercase().contains(q) }
            }.forEach { add(VaultSearchResult.Password(it)) }
            notes.filter {
                it.title.lowercase().contains(q) ||
                    it.content.lowercase().contains(q) ||
                    it.category.displayName.lowercase().contains(q) ||
                    it.tags.any { tag -> tag.lowercase().contains(q) }
            }.forEach { add(VaultSearchResult.Note(it)) }
        }
        return Result.Success(results)
    }

    override suspend fun exportEncryptedBackup(): Result<ByteArray, DataError.Local> {
        val key = sessionKeyStore.get() ?: return Result.Error(DataError.Local.UNKNOWN)
        val snap = preferences.snapshot()
        val passwords = passwordDao.observeAll().first()
        val notes = noteDao.observeAll().first()
        val payload = BackupPayload(
            salt = snap.salt.orEmpty(),
            verification = snap.verification.orEmpty(),
            passwords = passwords.map {
                BackupPassword(
                    id = it.id,
                    titleCipher = it.titleCipher,
                    usernameCipher = it.usernameCipher,
                    passwordCipher = it.passwordCipher,
                    urlCipher = it.urlCipher,
                    notesCipher = it.notesCipher,
                    category = it.category,
                    tagsCsv = it.tagsCsv,
                    favorite = it.favorite,
                    createdAtEpochMs = it.createdAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs
                )
            },
            notes = notes.map {
                BackupNote(
                    id = it.id,
                    titleCipher = it.titleCipher,
                    contentCipher = it.contentCipher,
                    category = it.category,
                    tagsCsv = it.tagsCsv,
                    favorite = it.favorite,
                    pinned = it.pinned,
                    archived = it.archived,
                    createdAtEpochMs = it.createdAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs
                )
            }
        )
        // Wrap JSON with current DEK so file is ciphertext blob
        val jsonBytes = json.encodeToString(payload)
        val encrypted = CryptoEngine.encrypt(jsonBytes, key)
        return Result.Success(encrypted.toByteArray(Charsets.UTF_8))
    }

    override suspend fun importEncryptedBackup(bytes: ByteArray, masterPassword: String): EmptyResult<DataError.Local> {
        return try {
            val key = sessionKeyStore.get() ?: return Result.Error(DataError.Local.UNKNOWN)
            val decrypted = CryptoEngine.decrypt(String(bytes, Charsets.UTF_8), key)
            val payload = json.decodeFromString<BackupPayload>(decrypted)
            passwordDao.deleteAll()
            noteDao.deleteAll()
            payload.passwords.forEach {
                passwordDao.upsert(
                    com.vaultly.core.database.entity.PasswordEntity(
                        id = it.id,
                        titleCipher = it.titleCipher,
                        usernameCipher = it.usernameCipher,
                        passwordCipher = it.passwordCipher,
                        urlCipher = it.urlCipher,
                        notesCipher = it.notesCipher,
                        category = it.category,
                        tagsCsv = it.tagsCsv,
                        favorite = it.favorite,
                        createdAtEpochMs = it.createdAtEpochMs,
                        updatedAtEpochMs = it.updatedAtEpochMs
                    )
                )
            }
            payload.notes.forEach {
                noteDao.upsert(
                    com.vaultly.core.database.entity.NoteEntity(
                        id = it.id,
                        titleCipher = it.titleCipher,
                        contentCipher = it.contentCipher,
                        category = it.category,
                        tagsCsv = it.tagsCsv,
                        favorite = it.favorite,
                        pinned = it.pinned,
                        archived = it.archived,
                        createdAtEpochMs = it.createdAtEpochMs,
                        updatedAtEpochMs = it.updatedAtEpochMs
                    )
                )
            }
            Result.Success(Unit)
        } catch (_: Exception) {
            Result.Error(DataError.Local.CORRUPT)
        }
    }

    @Serializable
    private data class BackupPayload(
        val salt: String,
        val verification: String,
        val passwords: List<BackupPassword>,
        val notes: List<BackupNote>
    )

    @Serializable
    private data class BackupPassword(
        val id: String,
        val titleCipher: String,
        val usernameCipher: String,
        val passwordCipher: String,
        val urlCipher: String,
        val notesCipher: String,
        val category: String,
        val tagsCsv: String,
        val favorite: Boolean,
        val createdAtEpochMs: Long,
        val updatedAtEpochMs: Long
    )

    @Serializable
    private data class BackupNote(
        val id: String,
        val titleCipher: String,
        val contentCipher: String,
        val category: String,
        val tagsCsv: String,
        val favorite: Boolean,
        val pinned: Boolean,
        val archived: Boolean,
        val createdAtEpochMs: Long,
        val updatedAtEpochMs: Long
    )
}
