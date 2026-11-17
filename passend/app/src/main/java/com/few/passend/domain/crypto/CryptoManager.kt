package com.few.passend.domain.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val PBKDF2_ITER = 120_000
    private const val KEY_BITS = 256
    private const val GCM_TAG = 128
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val VAULT_KEY_BYTES = 32

    private val rng = SecureRandom()
    @Volatile private var vaultKey: ByteArray? = null

    fun isUnlocked(): Boolean = vaultKey != null

    fun generateSalt(): ByteArray = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
    fun generateVaultKey(): ByteArray = ByteArray(VAULT_KEY_BYTES).also { rng.nextBytes(it) }

    fun deriveKek(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITER, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val out = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return out
    }

    fun wrapVaultKey(vaultKey: ByteArray, kek: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(IV_BYTES).also { rng.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG, iv))
        return c.doFinal(vaultKey) to iv
    }

    fun unwrapVaultKey(enc: ByteArray, iv: ByteArray, kek: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG, iv))
        return c.doFinal(enc)
    }

    fun setVaultKey(k: ByteArray) { vaultKey = k.copyOf() }

    fun getVaultKeyCopy(): ByteArray? = vaultKey?.copyOf()

    fun clearVaultKey() {
        val wasUnlocked = vaultKey != null
        vaultKey?.fill(0); vaultKey = null
        if (wasUnlocked) VaultEvents.notifyLocked()
    }

    fun encryptPassword(plain: String): Pair<ByteArray, ByteArray> {
        val k = vaultKey ?: error("Vault locked")
        val iv = ByteArray(IV_BYTES).also { rng.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(GCM_TAG, iv))
        return c.doFinal(plain.toByteArray(Charsets.UTF_8)) to iv
    }

    fun decryptPassword(enc: ByteArray, iv: ByteArray): String {
        val k = vaultKey ?: error("Vault locked")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(GCM_TAG, iv))
        return String(c.doFinal(enc), Charsets.UTF_8)
    }

    fun wrapWithCipherForBiometric(cipher: Cipher): Pair<ByteArray, ByteArray>? {
        val k = vaultKey ?: return null
        return try { cipher.doFinal(k) to cipher.iv } catch (e: Throwable) { null }
    }

    fun unlockWithCipherBiometric(cipher: Cipher, enc: ByteArray): Boolean {
        return try {
            val vk = cipher.doFinal(enc)
            if (vk.size != VAULT_KEY_BYTES) { vk.fill(0); return false }
            vaultKey = vk.copyOf()
            vk.fill(0)
            true
        } catch (e: Throwable) { false }
    }

    fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    fun unB64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}