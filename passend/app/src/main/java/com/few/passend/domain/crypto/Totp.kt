package com.few.passend.domain.crypto

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Totp {
    data class Result(val code: String, val remaining: Int, val period: Int, val digits: Int)

    fun generate(secretBase32: String, timestampMs: Long = System.currentTimeMillis()): Result {
        val key = Base32.decode(secretBase32)
        val period = 30
        val digits = 6
        val counter = timestampMs / 1000 / period
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[19].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
        val code = binary % 1_000_000
        val remaining = period - ((timestampMs / 1000) % period).toInt()
        return Result(String.format("%06d", code), remaining, period, digits)
    }

    /** Cek apakah string terlihat seperti secret base32 (untuk validasi input). */
    fun isValidSecret(s: String): Boolean {
        return try { Base32.decode(s).size >= 10 } catch (_: Throwable) { false }
    }
}