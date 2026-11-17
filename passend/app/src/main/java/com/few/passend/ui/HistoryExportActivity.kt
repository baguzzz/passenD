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
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.util.enableSecureFlag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryExportActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnExport: Button
    private var credentialCount: Int = 0

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.data != null)
            doExport(result.data!!.data!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureFlag()
        if (!CryptoManager.isUnlocked()) { finish(); return }
        setContentView(R.layout.activity_history_export)

        tvInfo = findViewById(R.id.tvInfo)
        tvStatus = findViewById(R.id.tvStatus)
        btnExport = findViewById(R.id.btnExport)

        loadInfo()

        btnExport.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_TITLE,
                    "passend-history-${System.currentTimeMillis()}.csv")
            }
            exportLauncher.launch(intent)
        }
    }

    private fun loadInfo() {
        val repo = (application as PassenDApplication).repository
        lifecycleScope.launch {
            tvInfo.text = "Memuat…"
            try {
                val all = withContext(Dispatchers.IO) { repo.observeAllOnce() }
                var totalHistory = 0
                var withHistory = 0
                for (c in all) {
                    val h = withContext(Dispatchers.IO) { repo.getPasswordHistory(c.id) }
                    if (h.isNotEmpty()) {
                        withHistory++
                        totalHistory += h.size
                    }
                }
                credentialCount = all.size
                if (totalHistory == 0) {
                    tvInfo.text = "${all.size} kredensial · belum ada riwayat perubahan password"
                    btnExport.isEnabled = false
                } else {
                    tvInfo.text = "${all.size} kredensial · " +
                            "$withHistory punya riwayat · " +
                            "$totalHistory entri history"
                    btnExport.isEnabled = true
                }
            } catch (e: Throwable) {
                tvInfo.text = "Error: ${e.message}"
                btnExport.isEnabled = false
            }
        }
    }

    private fun doExport(uri: Uri) {
        val repo = (application as PassenDApplication).repository
        tvStatus.text = "Mengekspor…"
        btnExport.isEnabled = false
        lifecycleScope.launch {
            try {
                val csv = withContext(Dispatchers.IO) {
                    buildCsv(repo)
                }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Tidak bisa membuka file")
                }
                tvStatus.text = "Export berhasil"
            } catch (e: Throwable) {
                tvStatus.text = "Gagal: ${e.message}"
            } finally {
                btnExport.isEnabled = true
            }
        }
    }

    private suspend fun buildCsv(repo: com.few.passend.data.repository.CredentialRepository): String {
        val sb = StringBuilder()
        sb.append("site,username,replaced_at,password_length,strength\n")
        val fm = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val all = repo.observeAllOnce()
        for (c in all) {
            val history = repo.getPasswordHistory(c.id)
            for (h in history) {
                val len = h.password.length
                val str = strengthLabel(h.password)
                sb.append(escapeCsv(c.site)).append(',')
                sb.append(escapeCsv(c.username)).append(',')
                sb.append(fm.format(Date(h.replacedAt))).append(',')
                sb.append(len).append(',')
                sb.append(str).append('\n')
            }
        }
        return sb.toString()
    }

    private fun strengthLabel(p: String): String {
        var s = 0
        if (p.length >= 8) s++
        if (p.length >= 12) s++
        if (p.length >= 16) s++
        if (p.any { it.isLowerCase() } && p.any { it.isUpperCase() }) s++
        if (p.any { it.isDigit() }) s++
        if (p.any { !it.isLetterOrDigit() }) s++
        return when {
            s <= 2 -> "lemah"
            s <= 3 -> "sedang"
            s <= 4 -> "kuat"
            else -> "sangat kuat"
        }
    }

    private fun escapeCsv(s: String): String {
        if (s.isEmpty()) return ""
        val needQuote = s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')
        if (!needQuote) return s
        return "\"" + s.replace("\"", "\"\"") + "\""
    }
}