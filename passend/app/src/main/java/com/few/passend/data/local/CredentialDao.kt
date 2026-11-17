package com.few.passend.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials WHERE id = :id")
    suspend fun findById(id: Long): CredentialEntity?

    @Insert
    suspend fun insert(entity: CredentialEntity): Long

    @Update
    suspend fun update(entity: CredentialEntity)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE credentials SET otpCounter = :counter, updatedAt = :ts WHERE id = :id")
    suspend fun updateOtpCounter(id: Long, counter: Long, ts: Long = System.currentTimeMillis())
}