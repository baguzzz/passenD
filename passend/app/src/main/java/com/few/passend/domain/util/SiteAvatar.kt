package com.few.passend.domain.util

object SiteAvatar {
    private val PALETTE = intArrayOf(
        0xFFE57373.toInt(), 0xFFF06292.toInt(), 0xFFBA68C8.toInt(),
        0xFF9575CD.toInt(), 0xFF7986CB.toInt(), 0xFF64B5F6.toInt(),
        0xFF4FC3F7.toInt(), 0xFF4DD0E1.toInt(), 0xFF4DB6AC.toInt(),
        0xFF81C784.toInt(), 0xFFAED581.toInt(), 0xFFFFB74D.toInt(),
        0xFFFF8A65.toInt(), 0xFFA1887F.toInt(), 0xFF90A4AE.toInt()
    )

    /** Warna deterministik dari hash situs — selalu sama untuk situs yang sama. */
    fun colorFor(site: String): Int {
        if (site.isEmpty()) return PALETTE[0]
        val hash = site.lowercase().hashCode()
        return PALETTE[Math.abs(hash) % PALETTE.size]
    }

    /** Inisial 1-2 huruf dari nama domain (contoh: "google.com" → "Go"). */
    fun initialsFor(site: String): String {
        val clean = site.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
        if (clean.isEmpty()) return "?"
        val dot = clean.indexOf('.')
        val name = if (dot > 0) clean.substring(0, dot) else clean
        if (name.isEmpty()) return "?"
        if (name.length == 1) return name.uppercase()
        return name.substring(0, 2).replaceFirstChar { it.uppercase() }
    }
}