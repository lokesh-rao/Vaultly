package com.vaultly.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vaultly.core.database.dao.NoteDao
import com.vaultly.core.database.dao.PasswordDao
import com.vaultly.core.database.entity.NoteEntity
import com.vaultly.core.database.entity.PasswordEntity

@Database(
    entities = [PasswordEntity::class, NoteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VaultlyDatabase : RoomDatabase() {
    abstract fun passwordDao(): PasswordDao
    abstract fun noteDao(): NoteDao
}
