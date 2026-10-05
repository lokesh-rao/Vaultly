package com.vaultly.core.domain.model

data class SecureNote(
    val id: String,
    val title: String,
    val content: String,
    val category: NoteCategory,
    val tags: List<String>,
    val favorite: Boolean,
    val pinned: Boolean,
    val archived: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
