package com.few.passend.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.few.passend.R
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.util.ClipboardHelper
import com.few.passend.domain.util.enableSecureFlag
import com.few.passend.server.PairingTokenManager
import com.few.passend.service.WebSocketServerService
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface

class PairingActivity : AppCompatActivity() {

    private lateinit var qrImage: ImageView
    private lateinit var tvUrl: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnCopy: Button
    private lateinit var btnRegen: Button
    private lateinit var tvIp: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureFlag()
        if (!CryptoManager.isUnlocked()) { finish(); return }
        setContentView(R.layout.activity_pairing)

        qrImage = findViewById(R.id.ivQr)
        tvUrl = findViewById(R.id.tvUrl)
        tvStatus = findViewById(R.id.tvStatus)
        btnToggle = findViewById(R.id.btnToggleServer)
        btnCopy = findViewById(R.id.btnCopyUrl)
        btnRegen = findViewById(R.id.btnRegenerate)
        tvIp = findViewById(R.id.tvIpPort)

        btnToggle.setOnClickListener { onToggleServer() }
        btnCopy.setOnClickListener { copyUrl() }
        btnRegen.setOnClickListener { confirmRegenerate() }
        refreshUi()
    }

    override fun onResume() { super.onResume(); refreshUi() }

    private fun onToggleServer() {
        val on = !PairingTokenManager.isEnabled(this)
        PairingTokenManager.setEnabled(this, on)
        if (on) startForegroundService(Intent(this, WebSocketServerService::class.java))
        else stopService(Intent(this, WebSocketServerService::class.java))
        refreshUi()
    }

    private fun refreshUi() {
        val enabled = PairingTokenManager.isEnabled(this)
        btnToggle.text = if (enabled) "Matikan Server" else "Aktifkan Server"
        tvStatus.text = if (enabled) "Server: AKTIF" else "Server: NONAKTIF"

        val ip = getLocalIp()
        val port = PairingTokenManager.port(this)
        if (enabled && ip != null) {
            val token = PairingTokenManager.tokenBase64(this)
            val name = android.net.Uri.encode(android.os.Build.MODEL ?: "Android")
            val payload = "passend://pair?ip=$ip&port=$port&token=$token&name=$name&v=1"
            tvUrl.text = payload
            tvIp.text = "$ip:$port"
            val qr = generateQr(payload)
            if (qr != null) qrImage.setImageBitmap(qr)
            btnCopy.isEnabled = true
        } else {
            tvUrl.text = if (ip == null) "Tidak terhubung ke jaringan." else "Aktifkan server untuk memulai pairing."
            tvIp.text = if (ip == null) "—" else "$ip:$port"
            qrImage.setImageDrawable(null)
            btnCopy.isEnabled = false
        }
    }

    private fun copyUrl() {
        val text = tvUrl.text?.toString() ?: return
        if (text.isEmpty()) return
        // FIX: pakai varian "Default" yang baca timeout dari AppSettings
        ClipboardHelper.copyAndAutoClearDefault(this, "passend pairing", text)
    }

    private fun confirmRegenerate() {
        AlertDialog.Builder(this).setTitle("Regenerate Token?")
            .setMessage("Semua klien browser yang sudah paired harus pairing ulang.")
            .setPositiveButton("Regenerate") { _, _ ->
                PairingTokenManager.regenerateToken(this)
                Toast.makeText(this, "Token baru dibuat", Toast.LENGTH_SHORT).show()
                refreshUi()
            }.setNegativeButton("Batal", null).show()
    }

    private fun getLocalIp(): String? {
        try {
            for (iface in NetworkInterface.getNetworkInterfaces()) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    private fun generateQr(content: String): Bitmap? {
        return try {
            val size = 720
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) for (y in 0 until size)
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            bmp
        } catch (e: Throwable) { null }
    }
}