package com.few.passend.domain.backup

import android.util.Xml
import com.few.passend.domain.model.Credential
import com.few.passend.domain.model.CustomField
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.URI

object KeePassXmlImporter {

    data class Result(val credentials: List<Credential>, val format: String, val skipped: Int)

    fun parse(input: InputStream): Result {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        val credentials = mutableListOf<Credential>()
        var skipped = 0
        var hasTotpSeed = false  // track TOTP across ALL entries

        var inEntry = false
        val strings = mutableMapOf<String, String>()
        var currentKey: String? = null
        var currentValue: StringBuilder? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""

            when (event) {
                XmlPullParser.START_TAG -> when (tag) {
                    "Entry" -> {
                        inEntry = true
                        strings.clear()
                    }
                    "String" -> if (inEntry) {
                        currentKey = null
                        currentValue = StringBuilder()
                    }
                    "Key" -> if (inEntry && currentValue != null) {
                        currentKey = readText(parser)
                        if (currentKey == "TOTP Seed") hasTotpSeed = true
                    }
                    "Value" -> {
                        // FIX: capture ke local val — hindari elvis redundant setelah smart-cast
                        val key = currentKey
                        val builder = currentValue
                        if (inEntry && key != null && builder != null) {
                            builder.append(readText(parser))
                            val value = builder.toString()
                            if (key.isNotEmpty()) strings[key] = value
                            currentKey = null
                            currentValue = null
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (tag == "Entry" && inEntry) {
                    inEntry = false
                    val cred = buildCredential(strings)
                    if (cred != null) credentials.add(cred) else skipped++
                }
            }
            event = parser.next()
        }

        val format = if (hasTotpSeed) "KeePass 2.x" else "KeePass XML"
        return Result(credentials, format, skipped)
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text ?: ""
            parser.nextTag()
        }
        return result
    }

    private fun buildCredential(strings: Map<String, String>): Credential? {
        val title = strings["Title"]?.trim().orEmpty()
        val user = strings["UserName"]?.trim().orEmpty()
        val pwd = strings["Password"].orEmpty()
        val url = strings["URL"]?.trim().orEmpty()
        val notes = strings["Notes"]?.trim().orEmpty()
        val otpRaw = (strings["TOTP Seed"] ?: strings["otp"] ?: "").trim()

        if (title.isEmpty() && user.isEmpty() && pwd.isEmpty()) return null
        if (pwd.isEmpty()) return null

        val site = if (url.isNotEmpty()) extractHost(url) else title
        if (site.isEmpty()) return null

        val otp = normalizeOtp(otpRaw)

        val knownKeys = setOf("Title", "UserName", "Password", "URL", "Notes", "TOTP Seed", "otp")
        val customFields = mutableListOf<CustomField>()
        for ((k, v) in strings) {
            if (k in knownKeys || v.isBlank()) continue
            customFields.add(CustomField(
                label = k,
                value = v,
                isSensitive = k.lowercase().contains("password") ||
                        k.lowercase().contains("pin") ||
                        k.lowercase().contains("secret")
            ))
        }

        val finalNotes = buildString {
            if (title.isNotEmpty() && title != site) appendLine("Title: $title")
            if (notes.isNotEmpty()) appendLine(notes)
            if (url.isNotEmpty() && site != url) appendLine("URL: $url")
        }.trim()

        return Credential(
            site = site,
            username = user,
            password = pwd,
            notes = finalNotes,
            otpSecret = otp,
            customFields = customFields
        )
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
        return if (com.few.passend.domain.crypto.Totp.isValidSecret(candidate)) candidate else ""
    }
}