package com.few.passend.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.few.passend.R
import com.few.passend.domain.AppSettings
import com.few.passend.domain.crypto.BiometricKeyManager
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.crypto.VaultPrefs
import com.few.passend.domain.crypto.VaultWipe
import com.few.passend.domain.util.enableSecureFlag
import com.few.passend.ui.edit.EditCredentialActivity

class LockActivity : AppCompatActivity() {

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var btnBiometric: Button
    private lateinit var tvBiometricStatus: TextView
    private lateinit var tvAttemptWarning: TextView
    private var wiping = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureFlag()
        if (!VaultPrefs.isInitialized(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish(); return
        }
        if (CryptoManager.isUnlocked()) { go(); return }

        setContentView(R.layout.activity_lock)
        btnBiometric = findViewById(R.id.btnBiometric)
        tvBiometricStatus = findViewById(R.id.tvBiometricStatus)
        tvAttemptWarning = findViewById(R.id.tvAttemptWarning)
        val etPwd = findViewById<EditText>(R.id.etMasterPassword)

        setupBiometricPrompt()
        refreshBiometricButton()
        refreshAttemptWarning()

        findViewById<Button>(R.id.btnUnlock).setOnClickListener {
            if (wiping) return@setOnClickListener
            val p = etPwd.text.toString()
            if (p.isEmpty()) return@setOnClickListener
            if (VaultPrefs.tryUnlock(this, p)) {
                AppSettings.resetFailedAttempts(this)
                go()
            } else {
                handleFailedAttempt()
                etPwd.text.clear()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (VaultPrefs.isInitialized(this) && CryptoManager.isUnlocked()) go()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (CryptoManager.isUnlocked()) go()
    }

    private fun refreshAttemptWarning() {
        if (!AppSettings.isWipeEnabled(this)) { tvAttemptWarning.visibility = View.GONE; return }
        val max = AppSettings.getMaxFailedAttempts(this)
        val current = AppSettings.getFailedAttempts(this)
        val remaining = (max - current).coerceAtLeast(0)
        if (remaining <= 3) {
            tvAttemptWarning.visibility = View.VISIBLE
            tvAttemptWarning.text = "⚠ $remaining percobaan tersisa sebelum vault di-wipe"
            tvAttemptWarning.setTextColor(0xFFC62828.toInt())
        } else {
            tvAttemptWarning.visibility = View.GONE
        }
    }

    private fun handleFailedAttempt() {
        if (!AppSettings.isWipeEnabled(this)) {
            Toast.makeText(this, "Password salah", Toast.LENGTH_SHORT).show()
            return
        }
        val attempts = AppSettings.incrementFailedAttempts(this)
        val max = AppSettings.getMaxFailedAttempts(this)
        val remaining = (max - attempts).coerceAtLeast(0)

        if (remaining <= 0) {
            confirmWipeAndExecute()
        } else if (remaining <= 3) {
            Toast.makeText(this, "⚠ Password salah. $remaining percobaan tersisa!", Toast.LENGTH_LONG).show()
            refreshAttemptWarning()
        } else {
            Toast.makeText(this, "Password salah", Toast.LENGTH_SHORT).show()
            refreshAttemptWarning()
        }
    }

    private fun confirmWipeAndExecute() {
        wiping = true
        AlertDialog.Builder(this)
            .setTitle("⚠ WIPE VAULT")
            .setMessage("Batas percobaan habis. Semua data akan DIHAPUS PERMANEN. Lanjutkan?")
            .setCancelable(false)
            .setPositiveButton("Wipe Sekarang") { _, _ -> executeWipe() }
            .setNegativeButton("Batal") { _, _ ->
                // User cancel — reset counter agar bisa coba lagi, tapi ini melanggar paranoid.
                // Trade-off: beri user kesempatan terakhir. Counter tetap di max-1 agar
                // percobaan berikutnya langsung wipe.
                AppSettings.setFailedAttempts(this, max - 1)
                wiping = false
                refreshAttemptWarning()
            }
            .show()
    }

    private fun executeWipe() {
        val ok = VaultWipe.execute(this)
        if (!ok) {
            Toast.makeText(this, "Wipe gagal sebagian — coba manual via Settings", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Vault telah dihapus", Toast.LENGTH_LONG).show()
        }
        val intent = Intent(this, LockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private val max: Int get() = AppSettings.getMaxFailedAttempts(this)

    private fun setupBiometricPrompt() {
        biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val cipher = result.cryptoObject?.cipher ?: return
                    if (VaultPrefs.tryUnlockBiometric(this@LockActivity, cipher)) {
                        AppSettings.resetFailedAttempts(this@LockActivity)
                        go()
                    } else handleInvalidatedKey()
                }
                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    Toast.makeText(this@LockActivity, msg, Toast.LENGTH_SHORT).show()
                }
                override fun onAuthenticationFailed() {}
            })
    }

    private fun refreshBiometricButton() {
        if (!VaultPrefs.isBiometricEnabled(this)) {
            btnBiometric.visibility = View.GONE; tvBiometricStatus.visibility = View.GONE; return
        }
        val canAuth = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            btnBiometric.visibility = View.GONE
            tvBiometricStatus.visibility = View.VISIBLE
            tvBiometricStatus.text = "Biometrik tidak tersedia"
            return
        }
        btnBiometric.visibility = View.VISIBLE
        tvBiometricStatus.visibility = View.VISIBLE
        btnBiometric.setOnClickListener { promptBiometric() }
    }

    private fun promptBiometric() {
        try {
            val iv = VaultPrefs.biometricIv(this) ?: run { handleInvalidatedKey(); return }
            val cipher = BiometricKeyManager.getCipherForDecrypt(iv)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Buka PassenD")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Gunakan Master Password")
                .build()
            biometricPrompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        } catch (e: Throwable) {
            if (BiometricKeyManager.isInvalidated(e)) handleInvalidatedKey()
            else Toast.makeText(this, "Biometrik gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleInvalidatedKey() {
        BiometricKeyManager.deleteKey()
        VaultPrefs.clearBiometricWrap(this)
        btnBiometric.visibility = View.GONE
        tvBiometricStatus.visibility = View.GONE
        Toast.makeText(this, "Biometrik berubah. Masuk dengan master password.", Toast.LENGTH_LONG).show()
    }

    private fun go() {
        val target = intent?.getStringExtra("shortcut_target")
        val next = when (target) {
            "new_credential" -> Intent(this, EditCredentialActivity::class.java)
            "generator" -> Intent(this, PassphraseActivity::class.java)
            "import_qr" -> Intent(this, ShareQrActivity::class.java)
            else -> Intent(this, VaultActivity::class.java)
        }
        startActivity(next); finish()
    }
}