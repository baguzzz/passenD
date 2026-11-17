package com.few.passend.domain.backup

import com.few.passend.domain.model.Credential

object CsvExporter {

    /**
     * Format generik yang kompatibel dengan Chrome, Bitwarden, dan 1Password.
     * Kolom: name, url, username, password, notes, totp
     */
    fun export(all: List<Credential>, includeOtp: Boolean = true): String {
        val sb = StringBuilder()
        sb.append("name,url,username,password,notes")
        if (includeOtp) sb.append(",totp")
        sb.append('\n')

        for (c in all) {
            sb.append(escape(c.site)).append(',')
            sb.append(escape(urlFor(c.site))).append(',')
            sb.append(escape(c.username)).append(',')
            sb.append(escape(c.password)).append(',')
            sb.append(escape(c.notes))
            if (includeOtp) { sb.append(','); sb.append(escape(c.otpSecret)) }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun urlFor(site: String): String {
        val trimmed = site.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "https://$trimmed"
    }

    private fun escape(s: String): String {
        if (s.isEmpty()) return ""
        val needQuote = s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')
        if (!needQuote) return s
        return "\"" + s.replace("\"", "\"\"") + "\""
    }
}