package com.few.passend.ui

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.few.passend.PassenDApplication
import com.few.passend.R
import com.few.passend.domain.AppSettings
import com.few.passend.domain.backup.VaultBackupManager
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.util.enableSecureFlag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupActivity : AppCompatActivity() {

    private lateinit var etPass: EditText
    private lateinit var etPassConfirm: EditText
    private lateinit var tvStatus: TextView
    private lateinit var btnExport: Button
    private lateinit var btnImport: Button
    private lateinit var btnPickImport: Button

    private var pendingImportUri: Uri? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
            doExport(result.data!!.data!!)
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
            pendingImportUri = result.data!!.data
            btnImport.visibility = View.VISIBLE
            tvStatus.text = "File dipilih. Masukkan passphrase lalu tap 'Import'."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureFlag()
        if (!CryptoManager.isUnlocked()) { finish(); return }
        setContentView(R.layout.activity_backup)

        etPass = findViewById(R.id.etPassphrase)
        etPassConfirm = findViewById(R.id.etPassphraseConfirm)
        tvStatus = findViewById(R.id.tvStatus)
        btnExport = findViewById(R.id.btnExport)
        btnImport = findViewById(R.id.btnImport)
        btnPickImport = findViewById(R.id.btnPickImport)

        btnExport.setOnClickListener {
            val p = etPass.text.toString()
            val c = etPassConfirm.text.toString()
            if (p.length < 8) { status("Passphrase minimal 8 karakter"); return@setOnClickListener }
            if (p != c) { status("Passphrase tidak sama"); return@setOnClickListener }
            val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(android.content.Intent.EXTRA_TITLE, "passend-backup-${System.currentTimeMillis()}.passend")
            }
            exportLauncher.launch(intent)
        }

        btnPickImport.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            importLauncher.launch(intent)
        }

        btnImport.setOnClickListener {
            val uri = pendingImportUri ?: return@setOnClickListener
            val p = etPass.text.toString()
            if (p.length < 8) { status("Passphrase minimal 8 karakter"); return@setOnClickListener }
            doImport(uri, p.toCharArray())
        }
    }

    private fun doExport(uri: Uri) {
        val p = etPass.text.toString().toCharArray()
        val repo = (application as PassenDApplication).repository
        tvStatus.text = "Mengekspor…"
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { VaultBackupManager.exportJson(repo, p) }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { os -> os.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: throw IllegalStateException("Tidak bisa membuka file")
                }
                p.fill('\u0000')
                // FIX: catat last backup time agar banner reminder hilang
                AppSettings.setLastBackupAt(this@BackupActivity, System.currentTimeMillis())
                AppSettings.setBackupReminderSnoozedUntil(this@BackupActivity, 0L)
                status("Export berhasil")
            } catch (e: Throwable) {
                p.fill('\u0000')
                status("Export gagal: ${e.message}")
            }
        }
    }

    private fun doImport(uri: Uri, passphrase: CharArray) {
        val repo = (application as PassenDApplication).repository
        tvStatus.text = "Mengimpor…"
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { ins -> ins.bufferedReader().readText() }
                        ?: throw IllegalStateException("Tidak bisa membuka file")
                }
                val count = withContext(Dispatchers.IO) {
                    VaultBackupManager.importJson(repo, text, passphrase)
                }
                passphrase.fill('\u0000')
                status("Import berhasil: $count kredensial")
                btnImport.visibility = View.GONE
                pendingImportUri = null
            } catch (e: Throwable) {
                passphrase.fill('\u0000')
                status("Import gagal: ${e.message}")
            }
        }
    }

    private fun status(msg: String) { tvStatus.text = msg }
}