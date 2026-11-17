package com.few.passend.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.few.passend.PassenDApplication
import com.few.passend.R
import com.few.passend.domain.ShareCrypto
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.crypto.Totp
import com.few.passend.domain.model.Credential
import com.few.passend.domain.model.CustomField
import com.few.passend.domain.util.enableSecureFlag
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ShareQrActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CRED_ID = "share_cred_id"
        const val PREFIX = "passend://share?data="
    }

    private var mode = "IMPORT"
    private var credId: Long = 0L

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) handleScanned(result.contents)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureFlag()
        if (!CryptoManager.isUnlocked()) { finish(); return }
        setContentView(R.layout.activity_share_qr)

        credId = intent.getLongExtra(EXTRA_CRED_ID, 0L)
        mode = if (credId > 0L) "EXPORT" else "IMPORT"

        if (mode == "EXPORT") setupExportMode() else setupImportMode()
    }

    // ============================================================
    //  EXPORT MODE
    // ============================================================
    private fun setupExportMode() {
        findViewById<TextView>(R.id.tvTitle).text = "Share via QR"
        findViewById<TextView>(R.id.tvHint).text =
            "Masukkan passphrase sementara (min 8 karakter). " +
            "Passphrase ini dipakai untuk mengenkripsi data di QR dan harus diketik lagi di device penerima."
        findViewById<TextView>(R.id.tvHint2).text =
            "Hati-hati: hanya tampilkan QR ini ke orang/perangkat yang dipercaya."

        val llForm = findViewById<LinearLayout>(R.id.llForm)
        llForm.visibility = View.VISIBLE

        val etPass = findViewById<EditText>(R.id.etPass)
        val etConfirm = findViewById<EditText>(R.id.etPassConfirm)
        val btnAction = findViewById<Button>(R.id.btnAction)
        btnAction.text = "Generate QR"
        btnAction.setOnClickListener {
            val p = etPass.text.toString()
            val c = etConfirm.text.toString()
            if (p.length < 8) { toast("Passphrase minimal 8 karakter"); return@setOnClickListener }
            if (p != c) { toast("Konfirmasi tidak cocok"); return@setOnClickListener }
            generateQrForCredential(p.toCharArray())
            llForm.visibility = View.GONE
        }
    }

    private fun generateQrForCredential(passphrase: CharArray) {
        val repo = (application as PassenDApplication).repository
        lifecycleScope.launch {
            try {
                val cred = withContext(Dispatchers.IO) { repo.findById(credId) }
                    ?: run { toast("Kredensial tidak ditemukan"); return@launch }
                val json = withContext(Dispatchers.Default) { buildJson(cred) }
                val enc = withContext(Dispatchers.Default) { ShareCrypto.encrypt(json, passphrase) }
                passphrase.fill('\u0000')
                val payload = PREFIX + enc
                if (payload.length > 2200) {
                    AlertDialog.Builder(this@ShareQrActivity)
                        .setTitle("Terlalu besar untuk QR")
                        .setMessage("Payload ${payload.length} karakter melebihi batas QR (sekitar 2200). " +
                                "Hapus notes atau custom fields, atau gunakan Backup File untuk transfer.")
                        .setPositiveButton("OK", null).show()
                    return@launch
                }
                val qr = withContext(Dispatchers.Default) { generateQr(payload) }
                if (qr == null) { toast("Gagal generate QR"); return@launch }

                findViewById<ImageView>(R.id.ivQr).setImageBitmap(qr)
                findViewById<ImageView>(R.id.ivQr).visibility = View.VISIBLE
                findViewById<TextView>(R.id.tvHint2).text =
                    "Passphrase: (diketik manual di device penerima)\nUkuran payload: ${payload.length} bytes"

                findViewById<Button>(R.id.btnCopyText).apply {
                    visibility = View.VISIBLE
                    setOnClickListener {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("passend share", payload))
                        toast("Payload disalin ke clipboard")
                    }
                }
            } catch (e: Throwable) {
                toast("Gagal: ${e.message}")
            }
        }
    }

    // FIX: include otpType + otpCounter (HOTP support)
    private fun buildJson(c: Credential): String {
        val obj = JSONObject().apply {
            put("site", c.site)
            put("username", c.username)
            put("password", c.password)
            put("notes", c.notes)
            if (c.otpSecret.isNotEmpty()) {
                put("otpSecret", c.otpSecret)
                put("otpType", c.otpType)
                put("otpCounter", c.otpCounter)
            }
            if (c.customFields.isNotEmpty()) {
                put("customFields", JSONArray().apply {
                    for (f in c.customFields) put(JSONObject().apply {
                        put("label", f.label); put("value", f.value); put("sensitive", f.isSensitive)
                    })
                })
            }
        }
        return obj.toString()
    }

    // ============================================================
    //  IMPORT MODE
    // ============================================================
    private fun setupImportMode() {
        findViewById<TextView>(R.id.tvTitle).text = "Import dari QR"
        findViewById<TextView>(R.id.tvHint).text =
            "Scan QR dari device lain, lalu masukkan passphrase yang sama dengan yang dipakai saat generate QR."
        findViewById<TextView>(R.id.tvHint2).text = ""
        findViewById<LinearLayout>(R.id.llForm).visibility = View.VISIBLE

        findViewById<TextView>(R.id.tvPassLabel).text = "Passphrase (dari device pengirim):"
        val etPass = findViewById<EditText>(R.id.etPass)
        findViewById<EditText>(R.id.etPassConfirm).visibility = View.GONE
        findViewById<TextView>(R.id.tvPassConfirmLabel).visibility = View.GONE

        val btnAction = findViewById<Button>(R.id.btnAction)
        btnAction.text = "Scan QR"
        btnAction.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan QR PassenD Share")
                setBeepEnabled(false)
                setOrientationLocked(false)
            }
            try { scanLauncher.launch(options) } catch (e: Throwable) {
                toast("Kamera tidak tersedia: ${e.message}")
            }
        }
        etPass.setOnFocusChangeListener { _, _ -> }
    }

    private fun handleScanned(contents: String) {
        if (!contents.startsWith(PREFIX)) {
            toast("QR bukan PassenD Share"); return
        }
        val etPass = findViewById<EditText>(R.id.etPass)
        val passphrase = etPass.text.toString()
        if (passphrase.length < 8) {
            toast("Masukkan passphrase dulu (min 8 karakter)"); return
        }
        val data = contents.substring(PREFIX.length)
        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.Default) {
                    ShareCrypto.decrypt(data, passphrase.toCharArray())
                }
                val cred = withContext(Dispatchers.Default) { parseJson(json) }
                confirmImport(cred)
            } catch (e: Throwable) {
                AlertDialog.Builder(this@ShareQrActivity)
                    .setTitle("Gagal decrypt")
                    .setMessage("Passphrase salah atau QR rusak.\n\nDetail: ${e.message}")
                    .setPositiveButton("OK", null).show()
            }
        }
    }

    // FIX: baca otpType + otpCounter (HOTP support)
    private fun parseJson(json: String): Credential {
        val o = JSONObject(json)
        val cfs = mutableListOf<CustomField>()
        val arr = o.optJSONArray("customFields")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                cfs.add(CustomField(
                    label = f.optString("label", ""),
                    value = f.optString("value", ""),
                    isSensitive = f.optBoolean("sensitive", false)
                ))
            }
        }
        val otp = o.optString("otpSecret", "")
        val otpType = o.optString("otpType", "TOTP")
        val otpCounter = o.optLong("otpCounter", 0L)
        if (otp.isNotEmpty() && !Totp.isValidSecret(otp)) {
            // tolerate invalid secret
        }
        return Credential(
            site = o.optString("site", ""),
            username = o.optString("username", ""),
            password = o.optString("password", ""),
            notes = o.optString("notes", ""),
            otpSecret = otp,
            otpType = if (otpType == "HOTP") "HOTP" else "TOTP",
            otpCounter = otpCounter.coerceAtLeast(0L),
            customFields = cfs
        )
    }

    private fun confirmImport(cred: Credential) {
        val otpInfo = when {
            cred.otpSecret.isEmpty() -> "tidak"
            cred.otpType == "HOTP" -> "HOTP (counter ${cred.otpCounter})"
            else -> "TOTP"
        }
        AlertDialog.Builder(this)
            .setTitle("Import ${cred.site}?")
            .setMessage("Username: ${cred.username}\n" +
                    "Password: ${"*".repeat(minOf(4, cred.password.length))}${cred.password.takeLast(2)}\n" +
                    "OTP: $otpInfo\n" +
                    "Custom fields: ${cred.customFields.size}")
            .setPositiveButton("Import") { _, _ ->
                val repo = (application as PassenDApplication).repository
                lifecycleScope.launch {
                    try {
                        repo.save(cred)
                        toast("Berhasil import ${cred.site}")
                        finish()
                    } catch (e: Throwable) {
                        toast("Gagal simpan: ${e.message}")
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ============================================================
    //  Utils
    // ============================================================
    private fun generateQr(content: String): Bitmap? {
        return try {
            val size = 900
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
                mapOf(com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
                      com.google.zxing.EncodeHintType.MARGIN to 1))
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) for (y in 0 until size)
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            bmp
        } catch (e: Throwable) { null }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}