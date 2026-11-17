package com.few.passend.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val site: String,
    val username: String,
    val encryptedPassword: String,
    val iv: String,
    val notes: String = "",
    val encryptedOtpSecret: String = "",
    val otpIv: String = "",
    val otpType: String = "TOTP",
    val otpCounter: Long = 0L,
    val encryptedCustomFields: String = "",
    val customFieldsIv: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)