package com.few.passend.domain.crypto

import java.io.ByteArrayOutputStream

object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(s: String): ByteArray {
        val clean = s.replace("=", "").replace(" ", "").replace("-", "").uppercase()
        if (clean.isEmpty()) throw IllegalArgumentException("Empty base32")
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bitsLeft = 0
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) throw IllegalArgumentException("Invalid base32 char: $c")
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.write((buffer shr bitsLeft) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}