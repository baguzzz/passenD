package com.few.passend.domain

import android.content.Context
import com.few.passend.domain.model.CustomField
import org.json.JSONArray
import org.json.JSONObject

data class FieldTemplate(
    val id: String,
    val name: String,
    val fields: List<CustomField>
)

object FieldTemplates {
    private const val P = "field_templates"
    private const val K_LIST = "templates"

    val DEFAULTS: List<FieldTemplate> = listOf(
        FieldTemplate("pin", "PIN", listOf(
            CustomField("PIN", "", true)
        )),
        FieldTemplate("sec_qa", "Security Q&A", listOf(
            CustomField("Security Question", "", false),
            CustomField("Security Answer", "", true)
        )),
        FieldTemplate("recovery", "Recovery Code", listOf(
            CustomField("Recovery Code", "", true)
        )),
        FieldTemplate("ssh", "SSH Key", listOf(
            CustomField("SSH Public Key", "", false),
            CustomField("SSH Passphrase", "", true)
        )),
        FieldTemplate("api", "API Key", listOf(
            CustomField("API Key", "", true)
        )),
        FieldTemplate("wifi", "WiFi", listOf(
            CustomField("WiFi SSID", "", false),
            CustomField("WiFi Password", "", true)
        )),
        FieldTemplate("note", "Catatan", listOf(
            CustomField("Catatan", "", false)
        ))
    )

    fun all(c: Context): List<FieldTemplate> {
        val prefs = c.getSharedPreferences(P, Context.MODE_PRIVATE)
        if (!prefs.contains(K_LIST)) return DEFAULTS
        return try {
            val arr = JSONArray(prefs.getString(K_LIST, "[]") ?: "[]")
            // FIX: pakai for-loop (bukan nested mapNotNull) — hindari warning label ambigu
            val out = mutableListOf<FieldTemplate>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                val name = obj.optString("name", "")
                if (id.isEmpty() || name.isEmpty()) continue
                val fieldsArr = obj.optJSONArray("fields") ?: JSONArray()
                val parsedFields = mutableListOf<CustomField>()
                for (j in 0 until fieldsArr.length()) {
                    val fieldObj = fieldsArr.optJSONObject(j) ?: continue
                    parsedFields.add(CustomField(
                        label = fieldObj.optString("label", ""),
                        value = fieldObj.optString("value", ""),
                        isSensitive = fieldObj.optBoolean("sensitive", false)
                    ))
                }
                out.add(FieldTemplate(id, name, parsedFields))
            }
            out
        } catch (e: Throwable) { DEFAULTS }
    }

    fun save(c: Context, templates: List<FieldTemplate>) {
        val arr = JSONArray()
        for (t in templates) {
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("fields", JSONArray().apply {
                    for (f in t.fields) put(JSONObject().apply {
                        put("label", f.label)
                        put("value", f.value)
                        put("sensitive", f.isSensitive)
                    })
                })
            })
        }
        c.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
            .putString(K_LIST, arr.toString()).apply()
    }

    fun reset(c: Context) {
        c.getSharedPreferences(P, Context.MODE_PRIVATE).edit().remove(K_LIST).apply()
    }
}