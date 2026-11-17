package com.few.passend.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.few.passend.domain.LibraryLicense
import com.few.passend.domain.Licenses
import com.few.passend.domain.crypto.CryptoManager

class LicensesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!CryptoManager.isUnlocked()) { finish(); return }

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(16)
            setPadding(p, p, p, dp(32))
        }

        root.addView(TextView(this).apply {
            text = "Lisensi Open Source"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(6))
        })
        root.addView(TextView(this).apply {
            text = "PassenD dibangun di atas library open-source berikut. Tap item untuk melihat teks lisensi lengkap."
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 0, 0, dp(16))
            setLineSpacing(0f, 1.3f)
        })

        for ((category, libs) in Licenses.grouped()) {
            root.addView(TextView(this).apply {
                text = category
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFF1976D2.toInt())
                setPadding(0, dp(14), 0, dp(6))
            })
            for (lib in libs) {
                root.addView(buildItem(lib))
            }
        }

        root.addView(TextView(this).apply {
            text = "Semua library digunakan sesuai ketentuan lisensi masing-masing.\n" +
                    "PassenD sendiri adalah proyek pribadi."
            textSize = 11f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
            setLineSpacing(0f, 1.4f)
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun buildItem(lib: LibraryLicense): LinearLayout {
        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(12)
            setPadding(p, p, p, p)
            setBackgroundColor(if (isDark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(4)) }
            isClickable = true
            isFocusable = true
        }

        card.addView(TextView(this).apply {
            text = "${lib.name}  ·  ${lib.version}"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        })

        val badge = TextView(this).apply {
            text = lib.licenseName
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(if (lib.licenseName.contains("Apache")) 0xFF2E7D32.toInt()
                                else 0xFF6D4C41.toInt())
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
            addView(badge)
            addView(TextView(this@LicensesActivity).apply {
                text = "  ${lib.url}"
                textSize = 9f
                setTextColor(0xFF888888.toInt())
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        })

        card.setOnClickListener { showLicenseDialog(lib) }
        return card
    }

    private fun showLicenseDialog(lib: LibraryLicense) {
        // FIX: rename 'text' → 'licenseText' agar tidak shadow properti TextView.text
        val licenseText = Licenses.loadText(this, lib.licenseRawId)
        val scroll = ScrollView(this)
        val tv = TextView(this).apply {
            text = licenseText
            textSize = 11f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            typeface = Typeface.MONOSPACE
            setLineSpacing(0f, 1.15f)
        }
        scroll.addView(tv)
        AlertDialog.Builder(this)
            .setTitle("${lib.name} (${lib.licenseName})")
            .setView(scroll)
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}