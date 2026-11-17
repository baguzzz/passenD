package com.few.passend.ui

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.few.passend.R
import com.few.passend.domain.AppSettings
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.crypto.VaultPrefs
import com.few.passend.domain.util.enableSecureFlag

class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureFlag()

        // FIX: deteksi apakah vault sudah diinisialisasi.
        // - Belum init (fresh install / setelah wipe) → tampilkan form "Buat Vault Baru"
        // - Sudah init → tampilkan halaman Pengaturan
        if (!VaultPrefs.isInitialized(this)) {
            showFirstTimeSetup()
        } else {
            showSettings()
        }
    }

    // ============================================================
    //  MODE 1: Buat Vault Baru (first-time / setelah wipe)
    // ============================================================
    private fun showFirstTimeSetup() {
        setContentView(R.layout.activity_setup)

        val etPwd = findViewById<EditText>(R.id.etMasterPassword)
        val etConf = findViewById<EditText>(R.id.etMasterConfirm)
        val btnCreate = findViewById<Button>(R.id.btnCreate)

        btnCreate.setOnClickListener {
            val p = etPwd.text.toString()
            val c = etConf.text.toString()
            if (p.length < 8) {
                Toast.makeText(this, "Master password minimal 8 karakter", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (p != c) {
                Toast.makeText(this, "Konfirmasi password tidak cocok", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val salt = CryptoManager.generateSalt()
                val kek = CryptoManager.deriveKek(p.toCharArray(), salt)
                val vk = CryptoManager.generateVaultKey()
                val (encVk, iv) = CryptoManager.wrapVaultKey(vk, kek)
                VaultPrefs.saveSetup(this, salt, encVk, iv)
                kek.fill(0)
                CryptoManager.setVaultKey(vk)
                vk.fill(0)

                Toast.makeText(this, "Vault berhasil dibuat", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, VaultActivity::class.java))
                finish()
            } catch (e: Throwable) {
                Toast.makeText(this, "Gagal membuat vault: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ============================================================
    //  MODE 2: Pengaturan (vault sudah ada)
    // ============================================================
    private fun showSettings() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, pad, pad, dp(32))
        }

        root.addView(TextView(this).apply {
            text = "Pengaturan"; textSize = 24f
            setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, dp(20))
        })

        // Wipe banner
        if (AppSettings.isWipeOccurred(this)) {
            root.addView(TextView(this).apply {
                text = "⚠ Vault sebelumnya DIHAPUS karena terlalu banyak percobaan gagal.\n" +
                        "Semua kredensial hilang. Silakan restore dari backup."
                textSize = 12f; setTextColor(0xFFC62828.toInt())
                setBackgroundColor(0xFFFFEBEE.toInt())
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setLineSpacing(0f, 1.3f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, dp(16)) }
            })
            AppSettings.setWipeOccurred(this, false)
        }

        // ---------- Auto-lock ----------
        root.addView(sectionTitle("Auto-lock"))
        root.addView(TextView(this).apply {
            text = "Kunci vault saat app ke background:"
            textSize = 13f; setPadding(0, dp(4), 0, dp(6))
        })
        val lockOptions = listOf(
            "Instan (langsung)", "1 menit", "5 menit", "15 menit",
            "30 menit", "1 jam", "Tidak pernah"
        )
        val lockValues = listOf(-1, 1, 5, 15, 30, 60, 0)
        val curLock = AppSettings.getAutoLockMinutes(this)
        val curLockIdx = lockValues.indexOfFirst { it == curLock }.takeIf { it >= 0 } ?: 2
        val lockSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SetupActivity, android.R.layout.simple_spinner_dropdown_item, lockOptions)
            setSelection(curLockIdx)
        }
        root.addView(lockSpinner)
        root.addView(Button(this).apply {
            text = "Simpan Auto-lock"
            setOnClickListener {
                AppSettings.setAutoLockMinutes(this@SetupActivity, lockValues[lockSpinner.selectedItemPosition])
                Toast.makeText(this@SetupActivity, "Tersimpan", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(TextView(this).apply {
            text = "Lock saat layar mati selalu 5 detik (tidak bisa diubah)."
            textSize = 11f; setPadding(0, dp(4), 0, dp(12))
        })
        val cbUsb = CheckBox(this).apply {
            text = "Lock saat kabel USB/charger dilepas"
            textSize = 13f
            isChecked = AppSettings.isUsbDetachLock(this@SetupActivity)
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, checked ->
                AppSettings.setUsbDetachLock(this@SetupActivity, checked)
            }
        }
        root.addView(cbUsb)
        root.addView(TextView(this).apply {
            text = "Paranoid mode: vault terkunci saat kabel dicabut."
            textSize = 11f; setPadding(0, 0, 0, dp(16))
        })

        // ---------- Paranoid: wipe after N failed ----------
        root.addView(sectionTitle("Wipe Vault (Paranoid)"))
        val cbWipe = CheckBox(this).apply {
            text = "Wipe vault setelah batas percobaan gagal"
            textSize = 13f
            isChecked = AppSettings.isWipeEnabled(this@SetupActivity)
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(cbWipe)

        root.addView(TextView(this).apply {
            text = "Batas percobaan:"; textSize = 13f; setPadding(0, dp(4), 0, dp(6))
        })
        val maxOptions = listOf("5 kali", "10 kali", "20 kali")
        val maxValues = listOf(5, 10, 20)
        val curMax = AppSettings.getMaxFailedAttempts(this)
        val curMaxIdx = maxValues.indexOfFirst { it == curMax }.takeIf { it >= 0 } ?: 1
        val maxSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SetupActivity, android.R.layout.simple_spinner_dropdown_item, maxOptions)
            setSelection(curMaxIdx)
            isEnabled = cbWipe.isChecked
        }
        root.addView(maxSpinner)
        cbWipe.setOnCheckedChangeListener { _, checked ->
            maxSpinner.isEnabled = checked
            if (checked && !AppSettings.isWipeEnabled(this@SetupActivity)) {
                AlertDialog.Builder(this@SetupActivity)
                    .setTitle("⚠ Aktifkan Wipe?")
                    .setMessage("Setelah " + maxValues[maxSpinner.selectedItemPosition] + " kali password salah, " +
                            "SEMUA DATA akan dihapus permanen.\n\n" +
                            "Pastikan Anda punya backup terenkripsi dan ingat master password. " +
                            "Fitur ini tidak bisa dibatalkan.")
                    .setPositiveButton("Saya mengerti") { _, _ ->
                        AppSettings.setWipeEnabled(this@SetupActivity, true)
                    }
                    .setNegativeButton("Batal") { _, _ -> cbWipe.isChecked = false }
                    .show()
            } else if (!checked) {
                AppSettings.setWipeEnabled(this@SetupActivity, false)
                AppSettings.resetFailedAttempts(this@SetupActivity)
            }
        }
        root.addView(Button(this).apply {
            text = "Simpan Batas Percobaan"
            setOnClickListener {
                AppSettings.setMaxFailedAttempts(this@SetupActivity, maxValues[maxSpinner.selectedItemPosition])
                Toast.makeText(this@SetupActivity, "Tersimpan", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(TextView(this).apply {
            text = "⚠ Fitur destruktif. Hanya aktifkan jika rutin backup."
            textSize = 11f; setTextColor(0xFFC62828.toInt())
            setPadding(0, dp(4), 0, dp(16))
        })

        // ---------- Clipboard ----------
        root.addView(sectionTitle("Clipboard"))
        root.addView(TextView(this).apply {
            text = "Hapus otomatis isi clipboard setelah:"
            textSize = 13f; setPadding(0, dp(4), 0, dp(6))
        })
        val clipOptions = listOf("5 detik", "15 detik", "30 detik", "60 detik", "120 detik", "Tidak pernah")
        val clipValues = listOf(5, 15, 30, 60, 120, 0)
        val curClip = AppSettings.getClipboardTimeoutSec(this)
        val curClipIdx = clipValues.indexOfFirst { it == curClip }.takeIf { it >= 0 } ?: 2
        val clipSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SetupActivity, android.R.layout.simple_spinner_dropdown_item, clipOptions)
            setSelection(curClipIdx)
        }
        root.addView(clipSpinner)
        root.addView(Button(this).apply {
            text = "Simpan Clipboard"
            setOnClickListener {
                AppSettings.setClipboardTimeoutSec(this@SetupActivity, clipValues[clipSpinner.selectedItemPosition])
                Toast.makeText(this@SetupActivity, "Tersimpan", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(TextView(this).apply {
            text = "Berlaku untuk semua copy."; textSize = 11f; setPadding(0, dp(4), 0, dp(16))
        })

        // ---------- Backup Reminder ----------
        root.addView(sectionTitle("Pengingat Backup"))
        root.addView(TextView(this).apply {
            text = "Ingatkan backup setiap:"; textSize = 13f; setPadding(0, dp(4), 0, dp(6))
        })
        val remOptions = listOf("7 hari", "14 hari", "30 hari", "60 hari", "90 hari")
        val remValues = listOf(7, 14, 30, 60, 90)
        val curRem = AppSettings.getBackupReminderDays(this)
        val curRemIdx = remValues.indexOfFirst { it == curRem }.takeIf { it >= 0 } ?: 2
        val remSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SetupActivity, android.R.layout.simple_spinner_dropdown_item, remOptions)
            setSelection(curRemIdx)
        }
        root.addView(remSpinner)
        root.addView(Button(this).apply {
            text = "Simpan Pengingat"
            setOnClickListener {
                AppSettings.setBackupReminderDays(this@SetupActivity, remValues[remSpinner.selectedItemPosition])
                Toast.makeText(this@SetupActivity, "Tersimpan", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(TextView(this).apply {
            text = "Banner peringatan muncul di vault jika melewati interval."
            textSize = 11f; setPadding(0, dp(4), 0, dp(16))
        })

        // ---------- Tema ----------
        root.addView(sectionTitle("Tema Aplikasi"))
        val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = mode == Configuration.UI_MODE_NIGHT_YES
        root.addView(Button(this).apply {
            text = if (isDark) "Ubah ke Mode Terang" else "Ubah ke Mode Gelap"
            setOnClickListener {
                AppCompatDelegate.setDefaultNightMode(
                    if (isDark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
                )
                recreate()
            }
        })

        scroll.addView(root); setContentView(scroll)
    }

    private fun sectionTitle(s: String) = TextView(this).apply {
        text = s; textSize = 16f
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(16), 0, dp(8))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}