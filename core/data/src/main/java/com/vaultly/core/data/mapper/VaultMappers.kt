package com.vaultly.core.data.mapper

import com.vaultly.core.database.entity.NoteEntity
import com.vaultly.core.database.entity.PasswordEntity
import com.vaultly.core.domain.model.NoteCategory
import com.vaultly.core.domain.model.PasswordCategory
import com.vaultly.core.domain.model.PasswordEntry
import com.vaultly.core.domain.model.SecureNote
import com.vaultly.core.security.crypto.CryptoEngine
import javax.crypto.SecretKey

fun PasswordEntity.toDomain(key: SecretKey): PasswordEntry = PasswordEntry(
    id = id,
    title = CryptoEngine.decrypt(titleCipher, key),
    username = CryptoEngine.decrypt(usernameCipher, key),
    password = CryptoEngine.decrypt(passwordCipher, key),
    url = CryptoEngine.decrypt(urlCipher, key),
    notes = CryptoEngine.decrypt(notesCipher, key),
    category = runCatching { PasswordCategory.valueOf(category) }.getOrDefault(PasswordCategory.OTHER),
    tags = if (tagsCsv.isBlank()) emptyList() else tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    favorite = favorite,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)

fun PasswordEntry.toEntity(key: SecretKey): PasswordEntity = PasswordEntity(
    id = id,
    titleCipher = CryptoEngine.encrypt(title, key),
    usernameCipher = CryptoEngine.encrypt(username, key),
    passwordCipher = CryptoEngine.encrypt(password, key),
    urlCipher = CryptoEngine.encrypt(url, key),
    notesCipher = CryptoEngine.encrypt(notes, key),
    category = category.name,
    tagsCsv = tags.joinToString(","),
    favorite = favorite,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)

fun NoteEntity.toDomain(key: SecretKey): SecureNote = SecureNote(
    id = id,
    title = CryptoEngine.decrypt(titleCipher, key),
    content = CryptoEngine.decrypt(contentCipher, key),
    category = runCatching { NoteCategory.valueOf(category) }.getOrDefault(NoteCategory.OTHER),
    tags = if (tagsCsv.isBlank()) emptyList() else tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    favorite = favorite,
    pinned = pinned,
    archived = archived,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)

fun SecureNote.toEntity(key: SecretKey): NoteEntity = NoteEntity(
    id = id,
    titleCipher = CryptoEngine.encrypt(title, key),
    contentCipher = CryptoEngine.encrypt(content, key),
    category = category.name,
    tagsCsv = tags.joinToString(","),
    favorite = favorite,
    pinned = pinned,
    archived = archived,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)
