package com.few.passend.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "password_history",
    indices = [Index("credentialId")]
)
data class PasswordHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val credentialId: Long,
    val encryptedPassword: String,
    val iv: String,
    val replacedAt: Long
)