package com.few.passend.domain

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Crypto untuk QR share: PBKDF2-SHA256 + AES-256-GCM.
 * Format: base64( salt[16] || iv[12] || ciphertext+tag )
 */
object ShareCrypto {
    private const val ITER = 200_000
    private const val KEY_BITS = 256
    private const val GCM_TAG = 128
    private const val SALT = 16
    private const val IV = 12

    private val rng = SecureRandom()

    fun encrypt(plaintext: String, passphrase: CharArray): String {
        val salt = ByteArray(SALT).also { rng.nextBytes(it) }
        val iv = ByteArray(IV).also { rng.nextBytes(it) }
        val key = pbkdf2(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        key.fill(0)
        val blob = ByteArray(SALT + IV + ct.size)
        System.arraycopy(salt, 0, blob, 0, SALT)
        System.arraycopy(iv, 0, blob, SALT, IV)
        System.arraycopy(ct, 0, blob, SALT + IV, ct.size)
        return Base64.encodeToString(blob, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    fun decrypt(b64Data: String, passphrase: CharArray): String {
        val blob = Base64.decode(b64Data, Base64.NO_WRAP or Base64.URL_SAFE)
        require(blob.size > SALT + IV + 16) { "Data terlalu pendek" }
        val salt = blob.copyOfRange(0, SALT)
        val iv = blob.copyOfRange(SALT, SALT + IV)
        val ct = blob.copyOfRange(SALT + IV, blob.size)
        val key = pbkdf2(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG, iv))
        val pt = cipher.doFinal(ct)
        key.fill(0)
        return String(pt, Charsets.UTF_8)
    }

    private fun pbkdf2(p: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(p, salt, ITER, KEY_BITS)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val out = f.generateSecret(spec).encoded
        spec.clearPassword()
        return out
    }
}