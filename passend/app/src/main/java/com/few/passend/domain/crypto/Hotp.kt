package com.few.passend.domain.crypto

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HOTP (RFC 4226) — counter-based one-time password.
 * Berbeda dari TOTP: kode tidak bergantung waktu, melainkan counter yang naik setiap kali dipakai.
 */
object Hotp {
    fun generate(secretBase32: String, counter: Long): String {
        val key = Base32.decode(secretBase32)
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[19].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
        return String.format("%06d", binary % 1_000_000)
    }
}