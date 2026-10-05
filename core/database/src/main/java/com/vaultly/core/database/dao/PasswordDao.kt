package com.vaultly.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vaultly.core.database.entity.PasswordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {
    @Query("SELECT * FROM passwords ORDER BY favorite DESC, titleCipher ASC")
    fun observeAll(): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PasswordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PasswordEntity)

    @Query("DELETE FROM passwords WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM passwords")
    suspend fun deleteAll()
}
