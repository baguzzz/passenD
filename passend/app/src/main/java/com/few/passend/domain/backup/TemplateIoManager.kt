package com.few.passend.domain.backup

import android.content.Context
import com.few.passend.domain.FieldTemplate
import com.few.passend.domain.FieldTemplates
import com.few.passend.domain.model.CustomField
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

object TemplateIoManager {

    fun export(ctx: Context, os: OutputStream): Int {
        val templates = FieldTemplates.all(ctx)
        val root = JSONObject().apply {
            put("version", 1)
            put("templates", JSONArray().apply {
                for (t in templates) put(JSONObject().apply {
                    put("id", t.id); put("name", t.name)
                    put("fields", JSONArray().apply {
                        for (f in t.fields) put(JSONObject().apply {
                            put("label", f.label); put("value", f.value); put("sensitive", f.isSensitive)
                        })
                    })
                })
            })
        }
        os.write(root.toString(2).toByteArray(Charsets.UTF_8))
        os.flush()
        return templates.size
    }

    /** Merge dengan yang sudah ada (skip duplikat by id). */
    fun import(ctx: Context, ins: InputStream): Int {
        val text = ins.bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val arr = root.optJSONArray("templates") ?: throw IllegalArgumentException("Format tidak valid")
        val existing = FieldTemplates.all(ctx).toMutableList()
        val existingIds = existing.map { it.id }.toMutableSet()
        var added = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            var id = o.optString("id", "")
            val name = o.optString("name", "").trim()
            if (name.isEmpty()) continue
            // Kalau id kosong atau bentrok, generate id baru
            if (id.isEmpty() || id in existingIds) {
                id = "imported_${System.currentTimeMillis()}_$i"
            }
            val fieldsArr = o.optJSONArray("fields") ?: JSONArray()
            val fields = mutableListOf<CustomField>()
            for (j in 0 until fieldsArr.length()) {
                val f = fieldsArr.optJSONObject(j) ?: continue
                fields.add(CustomField(
                    label = f.optString("label", ""),
                    value = f.optString("value", ""),
                    isSensitive = f.optBoolean("sensitive", false)
                ))
            }
            if (fields.isEmpty()) continue
            existing.add(FieldTemplate(id, name, fields))
            existingIds.add(id)
            added++
        }
        FieldTemplates.save(ctx, existing)
        return added
    }
}