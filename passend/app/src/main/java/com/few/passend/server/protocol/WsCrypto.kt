package com.few.passend.server.protocol

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object WsCrypto {
    private const val TAG_BYTES = 128
    private const val IV_BYTES = 12
    private const val NONCE_BYTES = 16
    private const val SESSION_INFO = "passend-ws-session-v1"

    private val rng = SecureRandom()

    fun newNonce(): ByteArray = ByteArray(NONCE_BYTES).also { rng.nextBytes(it) }

    /**
     * HKDF-Expand sederhana (RFC 5869). Cukup untuk menurunkan 32-byte key.
     */
    fun deriveSessionKey(token: ByteArray, clientNonce: ByteArray, serverNonce: ByteArray): ByteArray {
        val salt = clientNonce + serverNonce
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(token)

        val info = SESSION_INFO.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        var t = ByteArray(0)
        var counter = 1
        while (out.size() < 32) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            out.write(t)
            counter++
        }
        return out.toByteArray().copyOf(32)
    }

    fun encrypt(key: ByteArray, plaintext: String): Pair<String, String> {
        val iv = ByteArray(IV_BYTES).also { rng.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES, iv))
        val ciphertext = c.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return b64(iv) to b64(ciphertext)
    }

    fun decrypt(key: ByteArray, ivB64: String, ctB64: String): String {
        val iv = unB64(ivB64)
        val ct = unB64(ctB64)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES, iv))
        return String(c.doFinal(ct), Charsets.UTF_8)
    }

    fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    fun unB64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}