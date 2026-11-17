package com.few.passend.domain.crypto

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    private const val VERSION = 1
    private const val ITER = 200_000
    private const val KEY_BITS = 256
    private const val GCM_TAG = 128
    private const val SALT = 16
    private const val IV = 12

    private val rng = SecureRandom()

    fun encryptBackup(plaintext: String, passphrase: CharArray): String {
        val salt = ByteArray(SALT).also { rng.nextBytes(it) }
        val iv = ByteArray(IV).also { rng.nextBytes(it) }
        val key = pbkdf2(passphrase, salt)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG, iv))
        val ct = c.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        key.fill(0)
        return JSONObject().apply {
            put("version", VERSION)
            put("salt", b64(salt))
            put("iv", b64(iv))
            put("ct", b64(ct))
        }.toString(2)
    }

    fun decryptBackup(text: String, passphrase: CharArray): String {
        val obj = JSONObject(text)
        require(obj.getInt("version") == VERSION) { "Versi backup tidak didukung" }
        val salt = unB64(obj.getString("salt"))
        val iv = unB64(obj.getString("iv"))
        val ct = unB64(obj.getString("ct"))
        val key = pbkdf2(passphrase, salt)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG, iv))
        val pt = c.doFinal(ct)
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

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unB64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}