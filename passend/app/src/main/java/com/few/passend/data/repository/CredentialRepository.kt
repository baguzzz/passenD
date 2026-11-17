package com.few.passend.data.repository

import com.few.passend.data.local.CredentialDao
import com.few.passend.data.local.CredentialEntity
import com.few.passend.data.local.PasswordHistoryDao
import com.few.passend.data.local.PasswordHistoryEntity
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.model.Credential
import com.few.passend.domain.model.CustomField
import com.few.passend.domain.model.PasswordHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class CredentialRepository(
    private val dao: CredentialDao,
    private val historyDao: PasswordHistoryDao
) {
    companion object { const val MAX_HISTORY = 5 }

    fun observeAll(): Flow<List<Credential>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun observeAllOnce(): List<Credential> = observeAll().first()
    suspend fun findById(id: Long): Credential? = dao.findById(id)?.toDomain()
    suspend fun findBySite(site: String): Credential? =
        observeAll().first().firstOrNull { it.site.equals(site, ignoreCase = true) }

    suspend fun save(cred: Credential): Long {
        if (cred.id > 0L) {
            val current = dao.findById(cred.id)
            if (current != null) {
                val oldPwd = try {
                    CryptoManager.decryptPassword(
                        CryptoManager.unB64(current.encryptedPassword),
                        CryptoManager.unB64(current.iv)
                    )
                } catch (e: Throwable) { null }
                if (oldPwd != null && oldPwd != cred.password) {
                    val (e2, i2) = CryptoManager.encryptPassword(oldPwd)
                    historyDao.insert(PasswordHistoryEntity(
                        credentialId = cred.id,
                        encryptedPassword = CryptoManager.b64(e2),
                        iv = CryptoManager.b64(i2),
                        replacedAt = System.currentTimeMillis()
                    ))
                    historyDao.trimToLatest(cred.id, MAX_HISTORY)
                }
            }
        }

        val (encPwd, ivPwd) = CryptoManager.encryptPassword(cred.password)

        var encOtp = ""; var ivOtp = ""
        if (cred.otpSecret.isNotEmpty()) {
            val (e, i) = CryptoManager.encryptPassword(cred.otpSecret)
            encOtp = CryptoManager.b64(e); ivOtp = CryptoManager.b64(i)
        }

        var encCf = ""; var ivCf = ""
        val cleanCf = cred.customFields.filter { it.label.isNotBlank() || it.value.isNotBlank() }
        if (cleanCf.isNotEmpty()) {
            val arr = JSONArray()
            for (f in cleanCf) arr.put(JSONObject().apply {
                put("label", f.label); put("value", f.value); put("sensitive", f.isSensitive)
            })
            val (e, i) = CryptoManager.encryptPassword(arr.toString())
            encCf = CryptoManager.b64(e); ivCf = CryptoManager.b64(i)
        }

        val otpType = if (cred.otpType == "HOTP") "HOTP" else "TOTP"

        val entity = CredentialEntity(
            id = cred.id,
            site = cred.site.trim(),
            username = cred.username.trim(),
            encryptedPassword = CryptoManager.b64(encPwd),
            iv = CryptoManager.b64(ivPwd),
            notes = cred.notes,
            encryptedOtpSecret = encOtp,
            otpIv = ivOtp,
            otpType = otpType,
            otpCounter = cred.otpCounter.coerceAtLeast(0L),
            encryptedCustomFields = encCf,
            customFieldsIv = ivCf,
            createdAt = cred.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        return if (cred.id == 0L) dao.insert(entity) else { dao.update(entity); cred.id }
    }

    suspend fun delete(id: Long) {
        historyDao.deleteAllForCredential(id)
        dao.deleteById(id)
    }

    suspend fun updateOtpCounter(id: Long, counter: Long) {
        dao.updateOtpCounter(id, counter.coerceAtLeast(0L))
    }

    suspend fun getPasswordHistory(credId: Long): List<PasswordHistoryEntry> {
        val rows = historyDao.findLatest(credId, MAX_HISTORY)
        return rows.mapNotNull { row ->
            try {
                val pwd = CryptoManager.decryptPassword(
                    CryptoManager.unB64(row.encryptedPassword),
                    CryptoManager.unB64(row.iv)
                )
                PasswordHistoryEntry(row.id, pwd, row.replacedAt)
            } catch (e: Throwable) { null }
        }
    }

    private fun CredentialEntity.toDomain(): Credential {
        val pwd = try {
            CryptoManager.decryptPassword(
                CryptoManager.unB64(encryptedPassword), CryptoManager.unB64(iv)
            )
        } catch (e: Throwable) { "<error>" }

        val otp = if (encryptedOtpSecret.isEmpty()) "" else try {
            CryptoManager.decryptPassword(
                CryptoManager.unB64(encryptedOtpSecret), CryptoManager.unB64(otpIv)
            )
        } catch (e: Throwable) { "" }

        val cfs: List<CustomField> = if (encryptedCustomFields.isEmpty()) emptyList() else try {
            val plain = CryptoManager.decryptPassword(
                CryptoManager.unB64(encryptedCustomFields), CryptoManager.unB64(customFieldsIv)
            )
            val arr = JSONArray(plain)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CustomField(
                    label = o.optString("label", ""),
                    value = o.optString("value", ""),
                    isSensitive = o.optBoolean("sensitive", false)
                )
            }
        } catch (e: Throwable) { emptyList() }

        return Credential(
            id, site, username, pwd, notes, otp,
            otpType = if (otpType == "HOTP") "HOTP" else "TOTP",
            otpCounter = otpCounter.coerceAtLeast(0L),
            customFields = cfs, createdAt = createdAt, updatedAt = updatedAt
        )
    }
}