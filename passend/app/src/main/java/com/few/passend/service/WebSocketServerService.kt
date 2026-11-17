package com.few.passend.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.few.passend.PassenDApplication
import com.few.passend.domain.crypto.VaultEvents
import com.few.passend.server.LocalWebSocketServer
import com.few.passend.server.PairingTokenManager
import com.few.passend.ui.VaultActivity

class WebSocketServerService : Service() {

    private var server: LocalWebSocketServer? = null
    private var actualPort: Int = 0
    private val lockListener: () -> Unit = {
        try { server?.broadcastLock() } catch (_: Throwable) {}
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        VaultEvents.onLock(lockListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (server != null) return START_NOT_STICKY

        // FIX: startForeground() SEBELUM setup server.
        // Android 14+ mewajibkan startForeground() dalam 5 detik setelah
        // startForegroundService() dipanggil. Loop coba 11 port bisa makan
        // waktu lama kalau port bentrok -> crash ForegroundServiceDidNotStartInTimeException.
        val initialPort = PairingTokenManager.port(this)
        try {
            val initialNotif = buildNotification(initialPort)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, initialNotif)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }

        val app = application as PassenDApplication
        val token = PairingTokenManager.getOrCreateToken(this)
        val preferred = PairingTokenManager.port(this)
        var lastError: Throwable? = null
        var started: LocalWebSocketServer? = null

        for (p in preferred..(preferred + 10)) {
            try {
                val s = LocalWebSocketServer(p, app.repository, token, app)
                s.start(5000, false)
                started = s
                actualPort = p
                Log.i(TAG, "server started on port $p")
                break
            } catch (e: Throwable) {
                Log.w(TAG, "port $p bind failed: ${e.message}")
                lastError = e
            }
        }

        if (started == null) {
            Log.e(TAG, "all ports failed", lastError)
            stopSelf()
            return START_NOT_STICKY
        }
        server = started
        PairingTokenManager.setPort(this, actualPort)

        // Update notifikasi dengan port final (kalau berbeda dari initial)
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification(actualPort))
        } catch (_: Throwable) {}

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        VaultEvents.offLock(lockListener)
        server?.shutdown(); server = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "PassenD Server", NotificationManager.IMPORTANCE_LOW
        ))
    }

    private fun buildNotification(port: Int) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("PassenD server aktif")
        .setContentText("Port $port - menunggu koneksi")
        .setSmallIcon(android.R.drawable.ic_lock_lock)
        .setContentIntent(PendingIntent.getActivity(this, 0,
            Intent(this, VaultActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setOngoing(true)
        .build()

    companion object {
        private const val TAG = "PassenDServerSvc"
        private const val CHANNEL_ID = "passend_server_channel"
        private const val NOTIF_ID = 1001
    }
}