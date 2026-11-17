package com.few.passend.domain.model

data class CustomField(
    val label: String = "",
    val value: String = "",
    val isSensitive: Boolean = false
)

data class Credential(
    val id: Long = 0,
    val site: String,
    val username: String,
    val password: String,
    val notes: String = "",
    val otpSecret: String = "",
    val otpType: String = "TOTP",
    val otpCounter: Long = 0L,
    val customFields: List<CustomField> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class PasswordHistoryEntry(
    val id: Long,
    val password: String,
    val replacedAt: Long
)