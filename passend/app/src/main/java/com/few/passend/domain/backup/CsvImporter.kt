package com.few.passend.domain.backup

import com.few.passend.domain.crypto.Totp
import com.few.passend.domain.model.Credential
import java.net.URI

object CsvImporter {

    data class Result(val credentials: List<Credential>, val format: String, val skipped: Int)

    fun parse(csvText: String): Result {
        val rows = parseCsv(csvText)
        if (rows.isEmpty()) return Result(emptyList(), "kosong", 0)

        val header = rows[0].map { it.trim().lowercase() }

        val isBitwarden = header.containsAll(listOf("login_uri", "login_username", "login_password"))
        val isLastPass = header.containsAll(listOf("url", "username", "password")) && header.contains("grouping")
        val isChrome = header.containsAll(listOf("name", "url", "username", "password"))

        val idxUrl = header.indexOfFirst { it in listOf("url", "login_uri", "uri", "website", "web site") }
        val idxUser = header.indexOfFirst { it in listOf("username", "login_username", "user", "login") }
        val idxPass = header.indexOfFirst { it in listOf("password", "login_password", "pass") }
        val idxName = header.indexOfFirst { it in listOf("name", "title") }
        val idxNotes = header.indexOfFirst { it in listOf("note", "notes") }
        val idxTotp = header.indexOfFirst { it in listOf("login_totp", "totp") }

        if (idxUrl < 0 || idxUser < 0 || idxPass < 0) {
            return Result(emptyList(), "tidak dikenal", rows.size - 1)
        }

        val format = when {
            isBitwarden -> "Bitwarden"
            isLastPass -> "LastPass"
            isChrome -> "Chrome"
            else -> "Generik"
        }

        val credentials = mutableListOf<Credential>()
        var skipped = 0
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.size <= maxOf(idxUrl, idxUser, idxPass)) { skipped++; continue }
            val urlRaw = row.getOrNull(idxUrl)?.trim().orEmpty()
            val user = row.getOrNull(idxUser)?.trim().orEmpty()
            val pwd = row.getOrNull(idxPass).orEmpty()
            if (urlRaw.isEmpty() || pwd.isEmpty()) { skipped++; continue }

            val host = extractHost(urlRaw)
            val site = host.ifEmpty { urlRaw }
            val displayName = row.getOrNull(idxName)?.trim().orEmpty()
            val notesRaw = row.getOrNull(idxNotes)?.trim().orEmpty()

            val notes = buildString {
                if (displayName.isNotEmpty() && displayName != site) appendLine(displayName)
                if (notesRaw.isNotEmpty()) appendLine(notesRaw)
                if (host.isNotEmpty() && host != urlRaw) appendLine(urlRaw)
            }.trim()

            val totpRaw = row.getOrNull(idxTotp)?.trim().orEmpty()
            val otp = normalizeOtp(totpRaw)

            credentials.add(Credential(
                site = site,
                username = user,
                password = pwd,
                notes = notes,
                otpSecret = otp
            ))
        }
        return Result(credentials, format, skipped)
    }

    private fun extractHost(url: String): String {
        return try {
            val u = URI(url)
            (u.host ?: "").removePrefix("www.")
        } catch (e: Throwable) { "" }
    }

    private fun normalizeOtp(raw: String): String {
        if (raw.isEmpty()) return ""
        val candidate = if (raw.startsWith("otpauth://")) {
            try {
                val q = URI(raw).query ?: return ""
                q.split("&").firstOrNull { it.startsWith("secret=") }
                    ?.substringAfter("=")?.uppercase() ?: return ""
            } catch (e: Throwable) { return "" }
        } else raw.replace(" ", "").uppercase()
        return if (Totp.isValidSecret(candidate)) candidate else ""
    }

    /** Parser CSV minimal yang mendukung quoted field + escaped quotes (""). */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val current = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        val s = text.replace("\r\n", "\n").replace("\r", "\n")
        // Skip BOM — 'i' diinisialisasi SEKALI dengan nilai final (tidak redundant)
        var i = if (s.startsWith("\uFEFF")) 1 else 0

        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < s.length && s[i + 1] == '"') { sb.append('"'); i++ }
                        else inQuotes = false
                    } else sb.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { current.add(sb.toString()); sb.clear() }
                c == '\n' -> {
                    current.add(sb.toString()); sb.clear()
                    if (current.any { it.isNotEmpty() }) rows.add(current.toList())
                    current.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || current.isNotEmpty()) {
            current.add(sb.toString())
            if (current.any { it.isNotEmpty() }) rows.add(current.toList())
        }
        return rows
    }
}