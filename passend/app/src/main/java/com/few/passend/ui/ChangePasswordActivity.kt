package com.few.passend.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.few.passend.R
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.crypto.VaultPrefs
import com.few.passend.domain.util.enableSecureFlag

class ChangePasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureFlag()
        if (!CryptoManager.isUnlocked()) { finish(); return }
        setContentView(R.layout.activity_change_password)

        val etCurrent = findViewById<EditText>(R.id.etCurrentPassword)
        val etNew = findViewById<EditText>(R.id.etNewPassword)
        val etConfirm = findViewById<EditText>(R.id.etConfirmNewPassword)

        findViewById<Button>(R.id.btnChange).setOnClickListener {
            val cur = etCurrent.text.toString()
            val nw = etNew.text.toString()
            val cf = etConfirm.text.toString()
            if (cur.isEmpty()) { toast("Masukkan password saat ini"); return@setOnClickListener }
            if (nw.length < 8) { toast("Password baru minimal 8 karakter"); return@setOnClickListener }
            if (nw != cf) { toast("Konfirmasi tidak cocok"); return@setOnClickListener }
            if (nw == cur) { toast("Password baru harus berbeda"); return@setOnClickListener }
            if (VaultPrefs.changeMasterPassword(this, cur, nw)) {
                toast("Master password berhasil diubah")
                etCurrent.text.clear(); etNew.text.clear(); etConfirm.text.clear()
                finish()
            } else {
                toast("Password saat ini salah")
                etCurrent.text.clear()
            }
        }
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}