package com.few.passend.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.few.passend.PassenDApplication
import com.few.passend.R
import com.few.passend.domain.backup.CsvExporter
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.util.enableSecureFlag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExportCsvActivity : AppCompatActivity() {

    private lateinit var tvCount: TextView
    private lateinit var tvStatus: TextView
    private lateinit var cbOtp: CheckBox

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
            performExport(result.data!!.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureFlag()
        if (!CryptoManager.isUnlocked()) { finish(); return }
        setContentView(R.layout.activity_export_csv)

        tvCount = findViewById(R.id.tvCount)
        tvStatus = findViewById(R.id.tvStatus)
        cbOtp = findViewById(R.id.cbIncludeOtp)

        findViewById<Button>(R.id.btnExport).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Export CSV plaintext?")
                .setMessage("File CSV akan berisi password dalam bentuk PLAINTEXT. " +
                        "Siapa pun yang membuka file ini bisa melihat semua password Anda. " +
                        "Simpan di tempat aman dan hapus setelah selesai.")
                .setPositiveButton("Lanjutkan") { _, _ -> launchExport() }
                .setNegativeButton("Batal", null)
                .show()
        }

        val repo = (application as PassenDApplication).repository
        lifecycleScope.launch {
            val all = withContext(Dispatchers.IO) { repo.observeAllOnce() }
            tvCount.text = "Tersedia: ${all.size} kredensial"
        }
    }

    private fun launchExport() {
        val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_TITLE, "passend-export-${System.currentTimeMillis()}.csv")
        }
        exportLauncher.launch(intent)
    }

    private fun performExport(uri: android.net.Uri) {
        val repo = (application as PassenDApplication).repository
        val includeOtp = cbOtp.isChecked
        tvStatus.text = "Mengekspor…"
        lifecycleScope.launch {
            try {
                val all = withContext(Dispatchers.IO) { repo.observeAllOnce() }
                val csv = withContext(Dispatchers.Default) { CsvExporter.export(all, includeOtp) }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Tidak bisa membuka file")
                }
                tvStatus.text = "Export berhasil: ${all.size} kredensial"
            } catch (e: Throwable) {
                tvStatus.text = "Gagal: ${e.message}"
            }
        }
    }
}