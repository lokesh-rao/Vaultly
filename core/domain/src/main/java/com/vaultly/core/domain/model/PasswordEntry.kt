package com.vaultly.core.domain.model

data class PasswordEntry(
    val id: String,
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    val category: PasswordCategory,
    val tags: List<String>,
    val favorite: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
