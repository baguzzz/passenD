package com.few.passend.domain.backup

import com.few.passend.data.repository.CredentialRepository
import com.few.passend.domain.crypto.BackupCrypto
import com.few.passend.domain.model.Credential
import com.few.passend.domain.model.CustomField
import org.json.JSONArray
import org.json.JSONObject

object VaultBackupManager {

    suspend fun exportJson(repo: CredentialRepository, passphrase: CharArray): String {
        val all = repo.observeAllOnce()
        val arr = JSONArray()
        for (c in all) {
            arr.put(JSONObject().apply {
                put("site", c.site); put("username", c.username)
                put("password", c.password); put("notes", c.notes)
                if (c.otpSecret.isNotEmpty()) {
                    put("otpSecret", c.otpSecret)
                    put("otpType", c.otpType)
                    put("otpCounter", c.otpCounter)
                }
                if (c.customFields.isNotEmpty()) {
                    put("customFields", JSONArray().apply {
                        for (f in c.customFields) put(JSONObject().apply {
                            put("label", f.label); put("value", f.value); put("sensitive", f.isSensitive)
                        })
                    })
                }
                put("createdAt", c.createdAt)
            })
        }
        return BackupCrypto.encryptBackup(arr.toString(), passphrase)
    }

    suspend fun importJson(repo: CredentialRepository, text: String, passphrase: CharArray): Int {
        val plain = BackupCrypto.decryptBackup(text, passphrase)
        val arr = JSONArray(plain)
        var count = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val site = o.optString("site", "")
            val password = o.optString("password", "")
            if (site.isEmpty() || password.isEmpty()) continue

            val cfs = mutableListOf<CustomField>()
            val cfArr = o.optJSONArray("customFields")
            if (cfArr != null) {
                for (j in 0 until cfArr.length()) {
                    val f = cfArr.getJSONObject(j)
                    cfs.add(CustomField(
                        label = f.optString("label", ""),
                        value = f.optString("value", ""),
                        isSensitive = f.optBoolean("sensitive", false)
                    ))
                }
            }

            repo.save(Credential(
                site = site, username = o.optString("username", ""),
                password = password, notes = o.optString("notes", ""),
                otpSecret = o.optString("otpSecret", ""),
                otpType = o.optString("otpType", "TOTP"),
                otpCounter = o.optLong("otpCounter", 0L),
                customFields = cfs,
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            ))
            count++
        }
        return count
    }
}