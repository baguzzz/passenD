package com.few.passend.ui

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.few.passend.PassenDApplication
import com.few.passend.R
import com.few.passend.domain.backup.CsvImporter
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.util.enableSecureFlag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportCsvActivity : AppCompatActivity() {

    private lateinit var tvFile: TextView
    private lateinit var tvPreview: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnImport: Button

    private var pendingUri: Uri? = null
    private var pendingText: String? = null
    private var pendingResult: CsvImporter.Result? = null

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data?.data != null) {
            loadFile(r.data!!.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureFlag()
        if (!CryptoManager.isUnlocked()) { finish(); return }
        setContentView(R.layout.activity_import_csv)

        tvFile = findViewById(R.id.tvFile)
        tvPreview = findViewById(R.id.tvPreview)
        tvStatus = findViewById(R.id.tvStatus)
        btnImport = findViewById(R.id.btnImport)

        findViewById<Button>(R.id.btnPickFile).setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(android.content.Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv"))
            }
            picker.launch(intent)
        }

        btnImport.setOnClickListener { doImport() }
    }

    private fun loadFile(uri: Uri) {
        tvStatus.text = "Membaca file…"
        btnImport.isEnabled = false
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { ins -> ins.bufferedReader().readText() }
                        ?: throw IllegalStateException("Tidak bisa membuka file")
                }
                val result = withContext(Dispatchers.IO) { CsvImporter.parse(text) }
                if (result.credentials.isEmpty()) {
                    tvStatus.text = "Tidak ada kredensial valid (format: ${result.format}, dilewati: ${result.skipped})"
                    tvPreview.text = ""
                    btnImport.isEnabled = false
                    return@launch
                }
                pendingUri = uri
                pendingText = text
                pendingResult = result
                tvFile.text = uri.lastPathSegment ?: uri.toString()
                tvPreview.text = "Format terdeteksi: ${result.format}\n" +
                        "Siap diimpor: ${result.credentials.size} kredensial\n" +
                        "Dilewati: ${result.skipped}\n" +
                        "Contoh: ${result.credentials.firstOrNull()?.site ?: "—"}"
                tvStatus.text = ""
                btnImport.isEnabled = true
            } catch (e: Throwable) {
                tvStatus.text = "Error: ${e.message}"
                tvPreview.text = ""
                btnImport.isEnabled = false
            }
        }
    }

    private fun doImport() {
        val result = pendingResult ?: return
        btnImport.isEnabled = false
        tvStatus.text = "Mengimpor ${result.credentials.size}…"
        val repo = (application as PassenDApplication).repository
        lifecycleScope.launch {
            try {
                var ok = 0
                for (c in result.credentials) {
                    try { repo.save(c); ok++ } catch (e: Throwable) {}
                }
                tvStatus.text = "Berhasil: $ok / ${result.credentials.size}"
                btnImport.isEnabled = false
                pendingUri = null; pendingText = null; pendingResult = null
                tvPreview.text = ""
            } catch (e: Throwable) {
                tvStatus.text = "Error: ${e.message}"
                btnImport.isEnabled = true
            }
        }
    }
}