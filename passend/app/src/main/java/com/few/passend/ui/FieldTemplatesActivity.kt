package com.few.passend.ui

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.few.passend.domain.FieldTemplate
import com.few.passend.domain.FieldTemplates
import com.few.passend.domain.backup.TemplateIoManager
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.model.CustomField
import com.few.passend.domain.util.enableSecureFlag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FieldTemplatesActivity : AppCompatActivity() {

    private lateinit var llList: LinearLayout
    private lateinit var tvStatus: TextView
    private val templates = mutableListOf<FieldTemplate>()

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data?.data != null) doExport(r.data!!.data!!)
    }
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data?.data != null) doImport(r.data!!.data!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureFlag()
        if (!CryptoManager.isUnlocked()) { finish(); return }

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(16); setPadding(p, p, p, dp(32))
        }
        root.addView(TextView(this).apply {
            text = "Template Custom Field"; textSize = 22f
            setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Template bisa punya value default — otomatis terisi saat dipakai. Tap untuk edit."
            textSize = 12f; setTextColor(0xFF888888.toInt())
            setPadding(0, 0, 0, dp(16)); setLineSpacing(0f, 1.3f)
        })

        tvStatus = TextView(this).apply {
            textSize = 11f; gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8)); visibility = View.GONE
        }
        root.addView(tvStatus)

        llList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(llList)

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(16), 0, dp(8)) }
        }
        row1.addView(Button(this).apply {
            text = "+ Tambah"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = dp(4) }
            setOnClickListener { editTemplate(null) }
        })
        row1.addView(Button(this).apply {
            text = "↻ Reset"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(4) }
            setOnClickListener {
                AlertDialog.Builder(this@FieldTemplatesActivity)
                    .setTitle("Reset ke Default?")
                    .setMessage("Semua template kustom akan dihapus dan diganti ke 7 default.")
                    .setPositiveButton("Reset") { _, _ ->
                        FieldTemplates.reset(this@FieldTemplatesActivity)
                        loadTemplates()
                    }.setNegativeButton("Batal", null).show()
            }
        })
        root.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row2.addView(Button(this).apply {
            text = "📤 Export"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = dp(4) }
            setOnClickListener { launchExport() }
        })
        row2.addView(Button(this).apply {
            text = "📥 Import"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(4) }
            setOnClickListener { launchImport() }
        })
        root.addView(row2)

        scroll.addView(root); setContentView(scroll)
        loadTemplates()
    }

    private fun launchExport() {
        val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_TITLE, "passend-templates-${System.currentTimeMillis()}.json")
        }
        exportLauncher.launch(intent)
    }

    private fun launchImport() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(android.content.Intent.EXTRA_MIME_TYPES,
                arrayOf("application/json", "text/plain", "*/*"))
        }
        importLauncher.launch(intent)
    }

    private fun doExport(uri: android.net.Uri) {
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = "Mengekspor…"
        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { os ->
                        TemplateIoManager.export(this@FieldTemplatesActivity, os)
                    } ?: throw IllegalStateException("Tidak bisa membuka file")
                }
                tvStatus.text = "✓ Export berhasil: $count template"
            } catch (e: Throwable) {
                tvStatus.text = "✗ Gagal: ${e.message}"
            }
        }
    }

    private fun doImport(uri: android.net.Uri) {
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = "Mengimpor…"
        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { ins ->
                        TemplateIoManager.import(this@FieldTemplatesActivity, ins)
                    } ?: throw IllegalStateException("Tidak bisa membuka file")
                }
                tvStatus.text = "✓ Import berhasil: $count template baru"
                loadTemplates()
            } catch (e: Throwable) {
                tvStatus.text = "✗ Gagal: ${e.message}"
            }
        }
    }

    private fun loadTemplates() {
        templates.clear(); templates.addAll(FieldTemplates.all(this)); render()
    }

    private fun render() {
        llList.removeAllViews()
        if (templates.isEmpty()) {
            llList.addView(TextView(this).apply {
                text = "(Tidak ada template)"; textSize = 12f
                setTextColor(0xFF888888.toInt()); gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, dp(20))
            })
            return
        }
        for (t in templates) llList.addView(buildRow(t))
    }

    private fun buildRow(t: FieldTemplate): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(0xFFF5F5F5.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(6)) }
            isClickable = true; isFocusable = true
            setOnClickListener { editTemplate(t) }
        }
        row.addView(TextView(this).apply {
            text = t.name; textSize = 15f; setTypeface(null, Typeface.BOLD)
        })
        val summary = t.fields.joinToString(", ") {
            val dn = if (it.value.isNotEmpty()) " (default)" else ""
            "${it.label.ifEmpty { "?" }}$dn"
        }
        row.addView(TextView(this).apply {
            text = "${t.fields.size} field: $summary"
            textSize = 11f; setTextColor(0xFF666666.toInt())
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(Button(this).apply {
            text = "Hapus"; textSize = 11f
            minWidth = 0; minHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            setTextColor(0xFFC62828.toInt())
            setBackgroundColor(0x00000000)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.END }
            setOnClickListener {
                AlertDialog.Builder(this@FieldTemplatesActivity)
                    .setTitle("Hapus template '${t.name}'?")
                    .setPositiveButton("Hapus") { _, _ ->
                        val list = FieldTemplates.all(this@FieldTemplatesActivity).toMutableList()
                        list.removeAll { it.id == t.id }
                        FieldTemplates.save(this@FieldTemplatesActivity, list)
                        loadTemplates()
                    }.setNegativeButton("Batal", null).show()
            }
        })
        return row
    }

    private fun editTemplate(existing: FieldTemplate?) {
        val isNew = existing == null
        val working = if (isNew) mutableListOf(CustomField("", "", false))
                      else existing!!.fields.toMutableList()
        val nameInput = EditText(this).apply {
            hint = "Nama template"
            setText(existing?.name ?: "")
            textSize = 14f
        }
        val fieldsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Reusable views
        val labelViews = mutableListOf<EditText>()
        val valueViews = mutableListOf<EditText>()
        val sensViews = mutableListOf<CheckBox>()

        // FIX: syncAll() HARUS didefinisikan SEBELUM renderFields() agar bisa dipanggil
        // dari dalam closure yang ada di renderFields(). Local function Kotlin
        // TIDAK mendukung forward reference.
        fun syncAll() {
            for (i in 0 until minOf(labelViews.size, working.size)) {
                working[i] = CustomField(
                    labelViews[i].text.toString().trim(),
                    valueViews[i].text.toString(),
                    sensViews[i].isChecked
                )
            }
        }

        fun renderFields() {
            fieldsContainer.removeAllViews()
            labelViews.clear(); valueViews.clear(); sensViews.clear()
            for ((i, f) in working.withIndex()) {
                val row1 = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(2))
                }
                val etLabel = EditText(this).apply {
                    hint = "Label"; setText(f.label); textSize = 13f
                    inputType = InputType.TYPE_CLASS_TEXT
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val cbSens = CheckBox(this).apply {
                    text = "S"; textSize = 10f; isChecked = f.isSensitive
                    setPadding(0, 0, 0, 0)
                }
                val btnDel = Button(this).apply {
                    text = "✕"; textSize = 12f
                    minWidth = 0; minHeight = 0
                    setPadding(dp(8), 0, dp(8), 0)
                    setTextColor(0xFFC62828.toInt())
                    setBackgroundColor(0x00000000)
                    setOnClickListener {
                        syncAll()  // sudah dideklarasikan di atas — valid
                        working.removeAt(i)
                        renderFields()
                    }
                }
                row1.addView(etLabel); row1.addView(cbSens); row1.addView(btnDel)
                fieldsContainer.addView(row1)

                val etValue = EditText(this).apply {
                    hint = "Default value (opsional)"; setText(f.value); textSize = 12f
                    inputType = InputType.TYPE_CLASS_TEXT
                    setPadding(dp(4), dp(4), dp(4), dp(8))
                }
                fieldsContainer.addView(etValue)

                labelViews.add(etLabel); valueViews.add(etValue); sensViews.add(cbSens)
            }
            fieldsContainer.addView(Button(this).apply {
                text = "+ Field"; textSize = 12f
                setOnClickListener {
                    syncAll()  // sudah dideklarasikan di atas — valid
                    working.add(CustomField("", "", false))
                    renderFields()
                }
            })
        }

        renderFields()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        content.addView(nameInput)
        content.addView(TextView(this).apply {
            text = "Fields:"; textSize = 12f; setPadding(0, dp(8), 0, dp(4))
        })
        content.addView(fieldsContainer)

        AlertDialog.Builder(this)
            .setTitle(if (isNew) "Template Baru" else "Edit Template")
            .setView(content)
            .setPositiveButton("Simpan") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) { toast("Nama wajib"); return@setPositiveButton }
                syncAll()
                val finalFields = working.filter { it.label.isNotBlank() }
                if (finalFields.isEmpty()) { toast("Minimal 1 field"); return@setPositiveButton }
                val id = existing?.id ?: "t_${System.currentTimeMillis()}"
                val newT = FieldTemplate(id, name, finalFields)
                val list = FieldTemplates.all(this).toMutableList()
                val idx = list.indexOfFirst { it.id == id }
                if (idx >= 0) list[idx] = newT else list.add(newT)
                FieldTemplates.save(this, list)
                loadTemplates()
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}