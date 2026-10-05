package com.vaultly.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
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
