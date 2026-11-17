package com.few.passend

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.few.passend.data.local.AppDatabase
import com.few.passend.data.repository.CredentialRepository
import com.few.passend.domain.AppSettings
import com.few.passend.domain.crypto.CryptoManager

class PassenDApplication : Application() {

    val database by lazy { AppDatabase.get(this) }
    val repository by lazy {
        CredentialRepository(database.credentialDao(), database.passwordHistoryDao())
    }

    private val lockHandler = Handler(Looper.getMainLooper())
    private val lockRunnable = Runnable { CryptoManager.clearVaultKey() }

    @Volatile private var screenOn: Boolean = true

    override fun onCreate() {
        super.onCreate()

        screenOn = try {
            (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        } catch (_: Throwable) { true }

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                screenOn = i.action == Intent.ACTION_SCREEN_ON
                if (!screenOn && CryptoManager.isUnlocked()) {
                    lockHandler.removeCallbacks(lockRunnable)
                    lockHandler.postDelayed(lockRunnable, SCREEN_OFF_LOCK_MS)
                }
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })

        // USB / charger detach → instant lock (jika setting aktif)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (!AppSettings.isUsbDetachLock(this@PassenDApplication)) return
                if (!CryptoManager.isUnlocked()) return
                lockHandler.removeCallbacks(lockRunnable)
                lockHandler.post(lockRunnable)
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        })

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                lockHandler.removeCallbacks(lockRunnable)
            }
            override fun onStop(owner: LifecycleOwner) {
                if (!CryptoManager.isUnlocked()) return
                if (!screenOn) return
                val minutes = AppSettings.getAutoLockMinutes(this@PassenDApplication)
                lockHandler.removeCallbacks(lockRunnable)
                when {
                    minutes < 0 -> lockHandler.post(lockRunnable)
                    minutes == 0 -> { /* never */ }
                    else -> lockHandler.postDelayed(lockRunnable, minutes * 60_000L)
                }
            }
        })
    }

    companion object {
        const val SCREEN_OFF_LOCK_MS = 5_000L
    }
}