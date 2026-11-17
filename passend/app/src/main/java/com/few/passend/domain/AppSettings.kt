package com.few.passend.domain

import android.content.Context

object AppSettings {
    private const val P = "app_settings"
    private const val K_AUTO_LOCK = "auto_lock_minutes"
    private const val K_CLIPBOARD_TIMEOUT = "clipboard_timeout_sec"
    private const val K_LAST_BACKUP_AT = "last_backup_at"
    private const val K_BACKUP_SNOOZED_UNTIL = "backup_snoozed_until"
    private const val K_BACKUP_REMINDER_DAYS = "backup_reminder_days"
    private const val K_USB_DETACH_LOCK = "usb_detach_lock"
    private const val K_FAILED_ATTEMPTS = "failed_attempts"
    private const val K_MAX_FAILED = "max_failed_attempts"
    private const val K_WIPE_ENABLED = "wipe_enabled"
    private const val K_WIPE_OCCURRED = "wipe_occurred"
    private const val DEFAULT_LOCK_MIN = 5
    private const val DEFAULT_CLIPBOARD_SEC = 30
    private const val DEFAULT_REMINDER_DAYS = 30
    private const val DEFAULT_MAX_FAILED = 10

    fun getAutoLockMinutes(c: Context): Int = p(c).getInt(K_AUTO_LOCK, DEFAULT_LOCK_MIN)
    fun setAutoLockMinutes(c: Context, m: Int) { p(c).edit().putInt(K_AUTO_LOCK, m).apply() }

    fun getClipboardTimeoutSec(c: Context): Int = p(c).getInt(K_CLIPBOARD_TIMEOUT, DEFAULT_CLIPBOARD_SEC)
    fun setClipboardTimeoutSec(c: Context, s: Int) { p(c).edit().putInt(K_CLIPBOARD_TIMEOUT, s).apply() }

    fun getLastBackupAt(c: Context): Long = p(c).getLong(K_LAST_BACKUP_AT, 0L)
    fun setLastBackupAt(c: Context, ts: Long) { p(c).edit().putLong(K_LAST_BACKUP_AT, ts).apply() }

    fun getBackupReminderSnoozedUntil(c: Context): Long = p(c).getLong(K_BACKUP_SNOOZED_UNTIL, 0L)
    fun setBackupReminderSnoozedUntil(c: Context, ts: Long) { p(c).edit().putLong(K_BACKUP_SNOOZED_UNTIL, ts).apply() }

    fun getBackupReminderDays(c: Context): Int = p(c).getInt(K_BACKUP_REMINDER_DAYS, DEFAULT_REMINDER_DAYS)
    fun setBackupReminderDays(c: Context, d: Int) {
        p(c).edit().putInt(K_BACKUP_REMINDER_DAYS, d.coerceIn(1, 365)).apply()
    }

    fun isUsbDetachLock(c: Context): Boolean = p(c).getBoolean(K_USB_DETACH_LOCK, false)
    fun setUsbDetachLock(c: Context, on: Boolean) { p(c).edit().putBoolean(K_USB_DETACH_LOCK, on).apply() }

    // ---------- Fase 16: Wipe after N failures ----------
    fun getFailedAttempts(c: Context): Int = p(c).getInt(K_FAILED_ATTEMPTS, 0)
    fun setFailedAttempts(c: Context, n: Int) { p(c).edit().putInt(K_FAILED_ATTEMPTS, n.coerceAtLeast(0)).apply() }
    fun incrementFailedAttempts(c: Context): Int {
        val n = getFailedAttempts(c) + 1
        setFailedAttempts(c, n)
        return n
    }
    fun resetFailedAttempts(c: Context) { setFailedAttempts(c, 0) }

    fun getMaxFailedAttempts(c: Context): Int = p(c).getInt(K_MAX_FAILED, DEFAULT_MAX_FAILED)
    fun setMaxFailedAttempts(c: Context, n: Int) { p(c).edit().putInt(K_MAX_FAILED, n.coerceIn(3, 50)).apply() }

    fun isWipeEnabled(c: Context): Boolean = p(c).getBoolean(K_WIPE_ENABLED, false)
    fun setWipeEnabled(c: Context, on: Boolean) { p(c).edit().putBoolean(K_WIPE_ENABLED, on).apply() }

    fun isWipeOccurred(c: Context): Boolean = p(c).getBoolean(K_WIPE_OCCURRED, false)
    fun setWipeOccurred(c: Context, on: Boolean) { p(c).edit().putBoolean(K_WIPE_OCCURRED, on).apply() }

    private fun p(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)
}