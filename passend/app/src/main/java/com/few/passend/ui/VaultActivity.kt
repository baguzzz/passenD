package com.few.passend.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.few.passend.PassenDApplication
import com.few.passend.R
import com.few.passend.domain.AppSettings
import com.few.passend.domain.crypto.BiometricKeyManager
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.crypto.Hotp
import com.few.passend.domain.crypto.Totp
import com.few.passend.domain.crypto.VaultPrefs
import com.few.passend.domain.model.Credential
import com.few.passend.domain.util.ClipboardHelper
import com.few.passend.domain.util.enableSecureFlag
import com.few.passend.server.PairingTokenManager
import com.few.passend.service.WebSocketServerService
import com.few.passend.ui.edit.EditCredentialActivity
import com.few.passend.ui.vault.VaultAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VaultActivity : AppCompatActivity() {

    private lateinit var adapter: VaultAdapter
    private lateinit var emptyView: TextView
    private lateinit var btnBiometricToggle: Button
    private lateinit var llBackupReminder: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureFlag()
        if (!CryptoManager.isUnlocked()) { redirectToLock(); return }
        setContentView(R.layout.activity_vault)

        adapter = VaultAdapter(
            onClick = { cred ->
                startActivity(Intent(this, EditCredentialActivity::class.java).putExtra("cred_id", cred.id))
            },
            onLongClick = { cred -> showQuickActions(cred) }
        )
        findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(this@VaultActivity)
            adapter = this@VaultActivity.adapter
        }
        emptyView = findViewById(R.id.tvEmpty)
        btnBiometricToggle = findViewById(R.id.btnBiometricToggle)
        llBackupReminder = findViewById(R.id.llBackupReminder)

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, EditCredentialActivity::class.java))
        }
        findViewById<Button>(R.id.btnLock).setOnClickListener {
            CryptoManager.clearVaultKey(); redirectToLock()
        }
        findViewById<Button>(R.id.btnMenu).setOnClickListener { showMenu() }
        btnBiometricToggle.setOnClickListener { onBiometricToggleClicked() }

        findViewById<Button>(R.id.btnBackupNow).setOnClickListener {
            startActivity(Intent(this, BackupActivity::class.java))
        }
        findViewById<Button>(R.id.btnBackupDismiss).setOnClickListener {
            val snoozeUntil = System.currentTimeMillis() + 7L * 24 * 3600 * 1000
            AppSettings.setBackupReminderSnoozedUntil(this, snoozeUntil)
            llBackupReminder.visibility = View.GONE
            Toast.makeText(this, "Pengingat ditunda 7 hari", Toast.LENGTH_SHORT).show()
        }

        // FIX: guard startForegroundService untuk Android 12+ (BackgroundStartNotAllowed)
        if (PairingTokenManager.isEnabled(this)) {
            try {
                startForegroundService(Intent(this, WebSocketServerService::class.java))
            } catch (e: Throwable) {
                Toast.makeText(this, "Server gagal start: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }

        val repo = (application as PassenDApplication).repository
        lifecycleScope.launch {
            repo.observeAll().collectLatest { list ->
                adapter.submit(list)
                emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                refreshBackupReminder()
            }
        }
        refreshBiometricButton()
    }

    private fun refreshBackupReminder() {
        if (adapter.itemCount == 0) { llBackupReminder.visibility = View.GONE; return }
        val now = System.currentTimeMillis()
        val snoozedUntil = AppSettings.getBackupReminderSnoozedUntil(this)
        if (now < snoozedUntil) { llBackupReminder.visibility = View.GONE; return }
        val lastBackup = AppSettings.getLastBackupAt(this)
        val reminderDays = AppSettings.getBackupReminderDays(this).coerceAtLeast(1)
        val thresholdMs = reminderDays.toLong() * 24 * 3600 * 1000
        val show = (lastBackup == 0L) || (now - lastBackup > thresholdMs)
        llBackupReminder.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showQuickActions(cred: Credential) {
        val options = mutableListOf<String>()
        if (cred.username.isNotEmpty()) options.add("Copy username")
        options.add("Copy password")
        if (cred.otpSecret.isNotEmpty()) {
            val otpLabel = if (cred.otpType == "HOTP") "Copy HOTP code" else "Copy TOTP code"
            options.add(otpLabel)
        }
        if (cred.customFields.isNotEmpty()) options.add("Show custom fields")
        options.add("Share via QR")

        AlertDialog.Builder(this).setTitle(cred.site)
            .setItems(options.toTypedArray()) { _, which ->
                val choice = options[which]
                when {
                    choice == "Copy username" -> ClipboardHelper.copyAndAutoClearDefault(this, "username", cred.username)
                    choice == "Copy password" -> ClipboardHelper.copyAndAutoClearDefault(this, "password", cred.password)
                    choice == "Copy TOTP code" || choice == "Copy HOTP code" -> copyOtpFresh(cred)
                    choice == "Show custom fields" -> showCustomFieldsDialog(cred)
                    choice == "Share via QR" -> startActivity(Intent(this, ShareQrActivity::class.java).apply {
                        putExtra(ShareQrActivity.EXTRA_CRED_ID, cred.id)
                    })
                }
            }.show()
    }

    private fun copyOtpFresh(cred: Credential) {
        val repo = (application as PassenDApplication).repository
        lifecycleScope.launch {
            try {
                val fresh = repo.findById(cred.id)
                if (fresh == null || fresh.otpSecret.isEmpty()) {
                    Toast.makeText(this@VaultActivity, "OTP tidak tersedia", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (fresh.otpType == "HOTP") {
                    val counter = fresh.otpCounter
                    val code = Hotp.generate(fresh.otpSecret, counter)
                    ClipboardHelper.copyAndAutoClearDefault(this@VaultActivity, "HOTP", code)
                    repo.updateOtpCounter(fresh.id, counter + 1)
                    Toast.makeText(
                        this@VaultActivity,
                        "HOTP $code - counter $counter -> ${counter + 1}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val r = Totp.generate(fresh.otpSecret)
                    ClipboardHelper.copyAndAutoClearDefault(this@VaultActivity, "TOTP", r.code)
                }
            } catch (e: Throwable) {
                Toast.makeText(this@VaultActivity, "OTP error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCustomFieldsDialog(cred: Credential) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        for (f in cred.customFields) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(6))
            }
            row.addView(TextView(this).apply {
                text = f.label.ifEmpty { "(tanpa label)" }
                textSize = 12f; setTextColor(0xFF888888.toInt())
            })
            val valueView = TextView(this).apply {
                text = if (f.isSensitive) "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022" else f.value
                textSize = 15f; setPadding(0, dp(2), 0, 0)
                setTextIsSelectable(true)
            }
            valueView.setOnClickListener {
                if (f.isSensitive) {
                    AlertDialog.Builder(this)
                        .setTitle(f.label.ifEmpty { "Field" })
                        .setMessage(f.value)
                        .setPositiveButton("Copy") { _, _ ->
                            ClipboardHelper.copyAndAutoClearDefault(this, "field", f.value)
                        }.setNegativeButton("Tutup", null).show()
                } else {
                    ClipboardHelper.copyAndAutoClearDefault(this, f.label.ifEmpty { "field" }, f.value)
                }
            }
            row.addView(valueView)
            root.addView(row)
        }
        root.addView(TextView(this).apply {
            text = "Tap field untuk copy. Sensitive memerlukan konfirmasi."
            textSize = 10f; setTextColor(0xFF888888.toInt())
            setPadding(0, dp(12), 0, 0)
        })
        AlertDialog.Builder(this).setTitle("Custom Fields - ${cred.site}")
            .setView(root).setPositiveButton("Tutup", null).show()
    }

    private fun showMenu() {
        val menuItems: List<Pair<String, () -> Unit>> = listOf(
            "Pairing Browser" to { startActivity(Intent(this, PairingActivity::class.java)) },
            "Import dari QR (share)" to { startActivity(Intent(this, ShareQrActivity::class.java)) },
            "Backup / Import Vault" to { startActivity(Intent(this, BackupActivity::class.java)) },
            "Import CSV" to { startActivity(Intent(this, ImportCsvActivity::class.java)) },
            "Import KeePass XML" to { startActivity(Intent(this, ImportXmlActivity::class.java)) },
            "Export CSV (plaintext)" to { startActivity(Intent(this, ExportCsvActivity::class.java)) },
            "Kesehatan Password" to { startActivity(Intent(this, PasswordHealthActivity::class.java)) },
            "Ganti Master Password" to { startActivity(Intent(this, ChangePasswordActivity::class.java)) },
            "Field Templates" to { startActivity(Intent(this, FieldTemplatesActivity::class.java)) },
            "Passphrase Generator" to { startActivity(Intent(this, PassphraseActivity::class.java)) },
            "Pengaturan" to { startActivity(Intent(this, SetupActivity::class.java)) },
            "Export History" to { startActivity(Intent(this, HistoryExportActivity::class.java)) },
            "Tentang & Lisensi" to { startActivity(Intent(this, AboutActivity::class.java)) }
        )
        val labels = menuItems.map { it.first }.toTypedArray()
        AlertDialog.Builder(this).setItems(labels) { _, which ->
            menuItems.getOrNull(which)?.second?.invoke()
        }.show()
    }

    override fun onResume() {
        super.onResume()
        if (!CryptoManager.isUnlocked()) { redirectToLock(); return }
        refreshBiometricButton(); refreshBackupReminder()
    }

    private fun refreshBiometricButton() {
        btnBiometricToggle.text = if (VaultPrefs.isBiometricEnabled(this)) "Bio: ON" else "Bio: OFF"
    }
    private fun onBiometricToggleClicked() {
        if (VaultPrefs.isBiometricEnabled(this)) confirmDisableBiometric() else enableBiometric()
    }

    private fun enableBiometric() {
        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Biometrik tidak tersedia", Toast.LENGTH_LONG).show(); return
        }
        try {
            BiometricKeyManager.deleteKey(); VaultPrefs.clearBiometricWrap(this)
            BiometricKeyManager.createKey()
            val cipher = BiometricKeyManager.getCipherForEncrypt()
            val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val c = result.cryptoObject?.cipher
                        val pair = c?.let { CryptoManager.wrapWithCipherForBiometric(it) }
                        if (pair == null) {
                            BiometricKeyManager.deleteKey(); VaultPrefs.clearBiometricWrap(this@VaultActivity)
                            Toast.makeText(this@VaultActivity, "Gagal", Toast.LENGTH_LONG).show(); return
                        }
                        VaultPrefs.saveBiometricWrap(this@VaultActivity, pair.first, pair.second)
                        Toast.makeText(this@VaultActivity, "Biometrik aktif", Toast.LENGTH_SHORT).show()
                        refreshBiometricButton()
                    }
                    override fun onAuthenticationError(code: Int, msg: CharSequence) {
                        BiometricKeyManager.deleteKey(); VaultPrefs.clearBiometricWrap(this@VaultActivity)
                        Toast.makeText(this@VaultActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle("Aktifkan Biometrik")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Batal").build(),
                BiometricPrompt.CryptoObject(cipher))
        } catch (e: Throwable) {
            BiometricKeyManager.deleteKey(); VaultPrefs.clearBiometricWrap(this)
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDisableBiometric() {
        AlertDialog.Builder(this).setTitle("Matikan Biometrik?")
            .setPositiveButton("Matikan") { _, _ ->
                BiometricKeyManager.deleteKey(); VaultPrefs.clearBiometricWrap(this)
                Toast.makeText(this, "Dimatikan", Toast.LENGTH_SHORT).show()
                refreshBiometricButton()
            }.setNegativeButton("Batal", null).show()
    }

    private fun redirectToLock() {
        startActivity(Intent(this, LockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }); finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}