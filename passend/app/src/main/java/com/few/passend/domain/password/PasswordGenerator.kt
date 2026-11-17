package com.few.passend.domain.password

import java.security.SecureRandom

object PasswordGenerator {
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"

    private val rng = SecureRandom()

    fun generate(
        length: Int = 20,
        lower: Boolean = true,
        upper: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true
    ): String {
        val sets = mutableListOf<String>()
        if (lower) sets.add(LOWER)
        if (upper) sets.add(UPPER)
        if (digits) sets.add(DIGITS)
        if (symbols) sets.add(SYMBOLS)
        if (sets.isEmpty()) sets.add(LOWER)

        val pool = sets.joinToString("")
        val len = length.coerceIn(4, 64)

        val chars = CharArray(len)
        var idx = 0
        for (s in sets) {
            chars[idx++] = s[rng.nextInt(s.length)]
        }
        while (idx < len) {
            chars[idx++] = pool[rng.nextInt(pool.length)]
        }
        for (i in chars.indices.reversed()) {
            val j = rng.nextInt(i + 1)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        return String(chars)
    }
}