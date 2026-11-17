package com.few.passend.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.few.passend.BuildConfig
import com.few.passend.PassenDApplication
import com.few.passend.R
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.util.enableSecureFlag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureFlag()
        if (!CryptoManager.isUnlocked()) { finish(); return }

        val scroll = android.widget.ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(20)
            setPadding(p, p, p, dp(32))
        }

        // App icon
        val icon = android.widget.ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(80), dp(80)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(8)
            }
        }
        root.addView(icon)

        // App name
        root.addView(TextView(this).apply {
            text = "PassenD"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })

        // Version
        root.addView(TextView(this).apply {
            text = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(16))
        })

        // Tagline
        root.addView(TextView(this).apply {
            text = "Password manager lokal untuk Android.\n" +
                    "Semua data tersimpan terenkripsi di perangkat ini."
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
            setLineSpacing(0f, 1.3f)
        })

        // ============================================================
        // Statistik Vault
        // ============================================================
        val statsCard = cardSection("Statistik Vault")
        val tvStats = TextView(this).apply {
            text = "Memuat..."
            textSize = 13f
            setPadding(0, dp(4), 0, dp(4))
            setLineSpacing(0f, 1.5f)
        }
        statsCard.addView(tvStats)
        root.addView(statsCard)

        // ============================================================
        // Fitur Keamanan
        // ============================================================
        val featSecurity = cardSection("Keamanan")
        featSecurity.addView(featureItem("\uD83D\uDD12", "Enkripsi AES-256-GCM + PBKDF2 (120k iterasi)"))
        featSecurity.addView(featureItem("\uD83E\uDDEC", "Biometrik via Android Keystore"))
        featSecurity.addView(featureItem("\uD83D\uDD10", "Wipe vault setelah N percobaan gagal (opsional)"))
        featSecurity.addView(featureItem("\uD83D\uDD50", "Auto-lock: instan / 1m / 5m / 15m / 30m / 1j / never"))
        featSecurity.addView(featureItem("\uD83D\uDD0C", "USB detach lock (paranoid mode)"))
        featSecurity.addView(featureItem("\uD83D\uDCF1", "FLAG_SECURE (anti screenshot & screen-record)"))
        featSecurity.addView(featureItem("\uD83D\uDCCB", "Auto-clear clipboard setelah N detik"))
        root.addView(featSecurity)

        // ============================================================
        // Fitur OTP & Generator
        // ============================================================
        val featOtp = cardSection("OTP & Generator")
        featOtp.addView(featureItem("\uD83D\uDD22", "TOTP (RFC 6238) - berbasis waktu"))
        featOtp.addView(featureItem("\uD83D\uDD04", "HOTP (RFC 4226) - berbasis counter"))
        featOtp.addView(featureItem("\uD83D\uDCF7", "QR scanner untuk setup OTP"))
        featOtp.addView(featureItem("\uD83C\uDFB2", "Password generator (acak, configurable)"))
        featOtp.addView(featureItem("\uD83D\uDCDD", "Passphrase generator (diceware 200 kata)"))
        root.addView(featOtp)

        // ============================================================
        // Fitur Backup & Sinkronisasi
        // ============================================================
        val featBackup = cardSection("Backup & Sinkronisasi")
        featBackup.addView(featureItem("\uD83D\uDCE4", "Backup terenkripsi (.passend file)"))
        featBackup.addView(featureItem("\uD83D\uDCE5", "Import dari CSV (Chrome, Bitwarden, LastPass)"))
        featBackup.addView(featureItem("\uD83D\uDCE5", "Import dari KeePass XML / KeePassXC"))
        featBackup.addView(featureItem("\uD83D\uDCF1", "Share credential via QR (cross-device)"))
        featBackup.addView(featureItem("\uD83D\uDCE1", "WebSocket server untuk extension browser"))
        featBackup.addView(featureItem("\uD83D\uDD17", "Extension Chrome/Firefox (auto-fill, TOTP, CRUD)"))
        root.addView(featBackup)

        // ============================================================
        // Fitur Lain
        // ============================================================
        val featMisc = cardSection("Fitur Lain")
        featMisc.addView(featureItem("\u2699", "Custom fields (PIN, security Q&A, dll)"))
        featMisc.addView(featureItem("\uD83D\uDCCB", "Field templates (default + editable)"))
        featMisc.addView(featureItem("\uD83D\uDD52", "Password history (5 versi terakhir)"))
        featMisc.addView(featureItem("\uD83D\uDCCA", "Password health report (lemah/reuse/lama)"))
        featMisc.addView(featureItem("\uD83D\uDD17", "App shortcuts (long-press icon)"))
        featMisc.addView(featureItem("\uD83C\uDF10", "Dark mode + Material Design"))
        root.addView(featMisc)

        // ============================================================
        // Buttons
        // ============================================================
        root.addView(Button(this).apply {
            text = "\uD83D\uDCDC Lisensi Open Source"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(8), 0, dp(4)) }
            setOnClickListener {
                startActivity(Intent(this@AboutActivity, LicensesActivity::class.java))
            }
        })

        // Copyright
        root.addView(TextView(this).apply {
            text = "\u00A9 2025 Few\n" +
                    "Dibuat untuk penggunaan pribadi.\n" +
                    "Data 100% lokal - tidak ada telemetri."
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFF888888.toInt())
            setPadding(0, dp(24), 0, 0)
            setLineSpacing(0f, 1.4f)
        })

        scroll.addView(root)
        setContentView(scroll)

        // ============================================================
        // Load stats async
        // ============================================================
        val repo = (application as PassenDApplication).repository
        lifecycleScope.launch {
            try {
                val all = withContext(Dispatchers.IO) { repo.observeAllOnce() }
                val withTotp = all.count { it.otpSecret.isNotEmpty() && it.otpType != "HOTP" }
                val withHotp = all.count { it.otpSecret.isNotEmpty() && it.otpType == "HOTP" }
                val withCf = all.count { it.customFields.isNotEmpty() }
                val totalCf = all.sumOf { it.customFields.size }

                val sb = StringBuilder()
                sb.append("${all.size} kredensial")
                if (all.isNotEmpty()) {
                    sb.append("\n")
                    sb.append("$withTotp TOTP")
                    if (withHotp > 0) sb.append("  |  $withHotp HOTP")
                    if (withCf > 0) {
                        sb.append("\n")
                        sb.append("$withCf punya custom field ($totalCf field total)")
                    }
                }
                tvStats.text = sb.toString()
            } catch (e: Throwable) {
                tvStats.text = "(statistik tidak tersedia)"
            }
        }
    }

    private fun cardSection(title: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(14)
            setPadding(p, p, p, p)
            setBackgroundColor(
                if ((resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES)
                    0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(12)) }
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })
        return card
    }

    private fun featureItem(emoji: String, text: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
            addView(TextView(this@AboutActivity).apply {
                this.text = emoji
                textSize = 14f
                setPadding(0, 0, dp(8), 0)
            })
            addView(TextView(this@AboutActivity).apply {
                this.text = text
                textSize = 13f
                setLineSpacing(0f, 1.2f)
            })
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}