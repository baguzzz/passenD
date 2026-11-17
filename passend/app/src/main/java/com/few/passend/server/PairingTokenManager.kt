package com.few.passend.server

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

object PairingTokenManager {
    private const val P = "passend_server"
    private const val K_TOKEN = "pairing_token"
    private const val K_PORT = "server_port"
    private const val K_ENABLED = "server_enabled"
    private const val DEFAULT_PORT = 8765

    private val rng = SecureRandom()

    fun getOrCreateToken(c: Context): ByteArray {
        val p = prefs(c)
        val existing = p.getString(K_TOKEN, null)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }
        val fresh = ByteArray(32).also { rng.nextBytes(it) }
        p.edit().putString(K_TOKEN, b64(fresh)).apply()
        return fresh
    }

    fun regenerateToken(c: Context): ByteArray {
        val fresh = ByteArray(32).also { rng.nextBytes(it) }
        prefs(c).edit().putString(K_TOKEN, b64(fresh)).apply()
        return fresh
    }

    fun tokenBase64(c: Context): String = b64(getOrCreateToken(c))

    fun port(c: Context): Int = prefs(c).getInt(K_PORT, DEFAULT_PORT)
    fun setPort(c: Context, port: Int) = prefs(c).edit().putInt(K_PORT, port).apply()

    fun isEnabled(c: Context): Boolean = prefs(c).getBoolean(K_ENABLED, false)
    fun setEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean(K_ENABLED, on).apply()

    private fun prefs(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
}