package com.few.passend.domain.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast
import com.few.passend.domain.AppSettings

object ClipboardHelper {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingClear: Runnable? = null

    /** Salin memakai timeout dari AppSettings (default 30 dtk). */
    fun copyAndAutoClearDefault(
        context: Context,
        label: String,
        text: String,
        showToast: Boolean = true
    ) {
        val sec = AppSettings.getClipboardTimeoutSec(context)
        copyAndAutoClear(context, label, text, showToast, sec * 1000L)
    }

    /** Salin dengan timeout kustom. timeoutMs = 0 → tidak auto-clear. */
    fun copyAndAutoClear(
        context: Context,
        label: String,
        text: String,
        showToast: Boolean = true,
        timeoutMs: Long
    ) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val extras = PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
                clip.description.extras = extras
            } catch (_: Throwable) {}
        }

        cm.setPrimaryClip(clip)

        pendingClear?.let { handler.removeCallbacks(it) }
        pendingClear = null

        if (timeoutMs <= 0L) {
            if (showToast) Toast.makeText(context, "Disalin", Toast.LENGTH_SHORT).show()
            return
        }

        pendingClear = Runnable {
            try {
                val current = cm.primaryClip
                if (current != null && current.itemCount > 0) {
                    val currentText = current.getItemAt(0).text?.toString() ?: ""
                    if (currentText == text) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            cm.clearPrimaryClip()
                        } else {
                            cm.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
        handler.postDelayed(pendingClear!!, timeoutMs)

        if (showToast) {
            Toast.makeText(
                context,
                "Disalin — otomatis dihapus dalam ${timeoutMs / 1000} detik",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}