package com.few.passend.domain.crypto

import android.content.Context
import android.util.Log
import com.few.passend.data.local.AppDatabase
import com.few.passend.domain.AppSettings
import java.io.File

/**
 * Wipe vault contents saat paranoid mode trigger.
 * Yang dihapus:
 *  - Biometric Keystore key
 *  - Database (passend.db + shm + wal)
 *  - SharedPreferences: vault_meta, passend_server
 * Yang DIPERTAHANKAN:
 *  - app_settings (preferensi + max attempts, tapi failed_attempts di-reset)
 *
 * Setelah wipe, `wipe_occurred = true` di app_settings agar SetupActivity bisa
 * menampilkan banner.
 */
object VaultWipe {

    private const val TAG = "VaultWipe"

    fun execute(context: Context): Boolean {
        var ok = true
        try {
            // 1. Reset & close Room
            try { AppDatabase.resetInstance() } catch (_: Throwable) {}
            // 2. Hapus biometric key
            try { BiometricKeyManager.deleteKey() } catch (_: Throwable) {}
            // 3. Hapus database files
            try {
                context.deleteDatabase("passend.db")
                val dbDir = context.getDatabasePath("passend.db").parentFile
                dbDir?.listFiles()?.forEach { f ->
                    if (f.name.startsWith("passend.db")) f.delete()
                }
            } catch (e: Throwable) { Log.w(TAG, "delete db", e); ok = false }
            // 4. Clear specific SharedPreferences
            try {
                clearPrefs(context, "vault_meta")
                clearPrefs(context, "passend_server")
            } catch (e: Throwable) { Log.w(TAG, "clear prefs", e); ok = false }
            // 5. Reset failed attempts + set flag
            try {
                AppSettings.resetFailedAttempts(context)
                AppSettings.setWipeOccurred(context, true)
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            Log.e(TAG, "wipe failed", e); ok = false
        }
        return ok
    }

    private fun clearPrefs(context: Context, name: String) {
        try {
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            File(prefsDir, "$name.xml").delete()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        } catch (_: Throwable) {}
    }
}