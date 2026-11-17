package com.few.passend.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.few.passend.PassenDApplication
import com.few.passend.R
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.model.Credential
import com.few.passend.domain.util.enableSecureFlag
import com.few.passend.ui.edit.EditCredentialActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PasswordHealthActivity : AppCompatActivity() {

    private data class Issue(val tag: String, val color: Int, val credential: Credential)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureFlag()
        if (!CryptoManager.isUnlocked()) { finish(); return }
        setContentView(R.layout.activity_password_health)

        val tvSummary = findViewById<TextView>(R.id.tvSummary)
        val llIssues = findViewById<LinearLayout>(R.id.llIssues)

        val repo = (application as PassenDApplication).repository
        lifecycleScope.launch {
            tvSummary.text = "Menganalisis…"
            try {
                val all = withContext(Dispatchers.IO) { repo.observeAllOnce() }
                val issues = withContext(Dispatchers.Default) { analyze(all) }

                if (issues.isEmpty()) {
                    tvSummary.text = "Tidak ada masalah ditemukan. Hebat!"
                    return@launch
                }
                val countWeak = issues.count { it.tag == "LEMAH" }
                val countReused = issues.count { it.tag == "DIPAKAI ULANG" }
                val countOld = issues.count { it.tag == "LAMA" }
                tvSummary.text = "$countWeak lemah · $countReused dipakai ulang · $countOld lama"

                llIssues.removeAllViews()
                val priority = mapOf("LEMAH" to 0, "DIPAKAI ULANG" to 1, "LAMA" to 2)
                for (issue in issues.sortedBy { priority[it.tag] ?: 99 }) llIssues.addView(buildRow(issue))
            } catch (e: Throwable) {
                tvSummary.text = "Error: ${e.message ?: "gagal analisis"}"
            }
        }
    }

    private fun analyze(all: List<Credential>): List<Issue> {
        val yearAgo = System.currentTimeMillis() - 365L * 24 * 3600 * 1000
        val pwdCounts = all.groupingBy { it.password }.eachCount()
        val out = mutableListOf<Issue>()
        for (c in all) {
            if (strength(c.password) <= 2) out.add(Issue("LEMAH", 0xFFEF5350.toInt(), c))
            if ((pwdCounts[c.password] ?: 0) > 1) out.add(Issue("DIPAKAI ULANG", 0xFFFF9800.toInt(), c))
            if (c.updatedAt < yearAgo) out.add(Issue("LAMA", 0xFF9E9E9E.toInt(), c))
        }
        return out
    }

    private fun buildRow(issue: Issue): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(if ((resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES)
                0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(4)) }
        }
        row.addView(TextView(this).apply {
            text = issue.tag; textSize = 10f; setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(issue.color)
            setPadding(dp(6), dp(3), dp(6), dp(3))
        })
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(this).apply {
            text = issue.credential.site; textSize = 14f; setTypeface(null, Typeface.BOLD)
        })
        info.addView(TextView(this).apply {
            text = issue.credential.username.ifEmpty { "(tanpa username)" }
            textSize = 11f; setTextColor(0xFF888888.toInt())
        })
        row.addView(info)
        row.setOnClickListener {
            startActivity(Intent(this, EditCredentialActivity::class.java)
                .putExtra("cred_id", issue.credential.id))
        }
        return row
    }

    private fun strength(p: String): Int {
        var s = 0
        if (p.length >= 8) s++
        if (p.length >= 12) s++
        if (p.length >= 16) s++
        if (p.any { it.isLowerCase() } && p.any { it.isUpperCase() }) s++
        if (p.any { it.isDigit() }) s++
        if (p.any { !it.isLetterOrDigit() }) s++
        return s.coerceAtMost(5)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}