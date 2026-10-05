package com.vaultly.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "passwords")
data class PasswordEntity(
    @PrimaryKey val id: String,
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
