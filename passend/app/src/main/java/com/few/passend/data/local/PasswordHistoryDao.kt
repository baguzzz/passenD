package com.few.passend.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PasswordHistoryDao {

    @Query("SELECT * FROM password_history WHERE credentialId = :credId ORDER BY replacedAt DESC LIMIT :limit")
    suspend fun findLatest(credId: Long, limit: Int): List<PasswordHistoryEntity>

    @Insert
    suspend fun insert(entity: PasswordHistoryEntity): Long

    @Query(
        "DELETE FROM password_history WHERE credentialId = :credId " +
        "AND id NOT IN (" +
        "  SELECT id FROM password_history WHERE credentialId = :credId " +
        "  ORDER BY replacedAt DESC LIMIT :keep" +
        ")"
    )
    suspend fun trimToLatest(credId: Long, keep: Int)

    @Query("DELETE FROM password_history WHERE credentialId = :credId")
    suspend fun deleteAllForCredential(credId: Long)
}