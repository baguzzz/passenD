package com.few.passend.domain.crypto

import android.content.Context
import android.util.Base64
import javax.crypto.Cipher

object VaultPrefs {
    private const val P = "vault_meta"
    private const val K_SALT = "salt"
    private const val K_ENC = "enc_vault"
    private const val K_IV = "iv_vault"
    private const val K_INIT = "initialized"
    private const val K_BIO_ENC = "bio_enc_vault"
    private const val K_BIO_IV = "bio_iv_vault"
    private const val K_BIO_ENABLED = "bio_enabled"

    fun isInitialized(c: Context) = p(c).getBoolean(K_INIT, false)

    fun saveSetup(c: Context, salt: ByteArray, encVault: ByteArray, iv: ByteArray) {
        p(c).edit()
            .putString(K_SALT, b64(salt))
            .putString(K_ENC, b64(encVault))
            .putString(K_IV, b64(iv))
            .putBoolean(K_INIT, true)
            .apply()
    }

    fun salt(c: Context): ByteArray? = p(c).getString(K_SALT, null)?.let { unB64(it) }
    fun encVault(c: Context): ByteArray? = p(c).getString(K_ENC, null)?.let { unB64(it) }
    fun ivVault(c: Context): ByteArray? = p(c).getString(K_IV, null)?.let { unB64(it) }

    fun tryUnlock(c: Context, masterPassword: String): Boolean {
        val salt = salt(c) ?: return false
        val enc = encVault(c) ?: return false
        val iv = ivVault(c) ?: return false
        return try {
            val kek = CryptoManager.deriveKek(masterPassword.toCharArray(), salt)
            val vk = CryptoManager.unwrapVaultKey(enc, iv, kek)
            kek.fill(0)
            CryptoManager.setVaultKey(vk)
            vk.fill(0)
            true
        } catch (e: Throwable) { false }
    }

    fun isBiometricEnabled(c: Context) = p(c).getBoolean(K_BIO_ENABLED, false)
    fun biometricEnc(c: Context): ByteArray? = p(c).getString(K_BIO_ENC, null)?.let { unB64(it) }
    fun biometricIv(c: Context): ByteArray? = p(c).getString(K_BIO_IV, null)?.let { unB64(it) }

    fun saveBiometricWrap(c: Context, enc: ByteArray, iv: ByteArray) {
        p(c).edit()
            .putString(K_BIO_ENC, b64(enc))
            .putString(K_BIO_IV, b64(iv))
            .putBoolean(K_BIO_ENABLED, true)
            .apply()
    }

    fun clearBiometricWrap(c: Context) {
        p(c).edit().remove(K_BIO_ENC).remove(K_BIO_IV).putBoolean(K_BIO_ENABLED, false).apply()
    }

    fun tryUnlockBiometric(c: Context, cipher: Cipher): Boolean {
        val enc = biometricEnc(c) ?: return false
        return CryptoManager.unlockWithCipherBiometric(cipher, enc)
    }

    // ---------- Fase 7 ----------

    /** Verifikasi master password tanpa mengubah VaultKey yang sedang aktif. */
    fun verifyMasterPassword(c: Context, pwd: String): Boolean {
        if (!CryptoManager.isUnlocked()) return false
        val salt = salt(c) ?: return false
        val enc = encVault(c) ?: return false
        val iv = ivVault(c) ?: return false
        return try {
            val kek = CryptoManager.deriveKek(pwd.toCharArray(), salt)
            val vk = CryptoManager.unwrapVaultKey(enc, iv, kek)
            kek.fill(0)
            val current = CryptoManager.getVaultKeyCopy()
            val match = current != null && vk.size == current.size && vk.contentEquals(current)
            vk.fill(0); current?.fill(0)
            match
        } catch (e: Throwable) { false }
    }

    /**
     * Ganti master password. VaultKey TIDAK berubah (data tidak perlu re-encrypt),
     * hanya wrapper KEK baru. Biometrik tetap valid karena wraps VaultKey langsung.
     */
    fun changeMasterPassword(c: Context, currentPwd: String, newPwd: String): Boolean {
        if (newPwd.length < 8) return false
        if (!verifyMasterPassword(c, currentPwd)) return false
        val newSalt = CryptoManager.generateSalt()
        val newKek = CryptoManager.deriveKek(newPwd.toCharArray(), newSalt)
        val vkCopy = CryptoManager.getVaultKeyCopy() ?: return false
        return try {
            val (newEnc, newIv) = CryptoManager.wrapVaultKey(vkCopy, newKek)
            saveSetup(c, newSalt, newEnc, newIv)
            true
        } catch (e: Throwable) { false }
        finally { vkCopy.fill(0); newKek.fill(0) }
    }

    private fun p(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unB64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}