package com.few.passend.ui.edit

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.few.passend.PassenDApplication
import com.few.passend.R
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.crypto.Hotp
import com.few.passend.domain.crypto.Totp
import com.few.passend.domain.model.Credential
import com.few.passend.domain.model.CustomField
import com.few.passend.domain.password.PasswordGenerator
import com.few.passend.domain.util.ClipboardHelper
import com.few.passend.domain.util.enableSecureFlag
import com.few.passend.ui.LockActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditCredentialActivity : AppCompatActivity() {

    private var existing: Credential? = null
    private lateinit var etSite: EditText
    private lateinit var etUser: EditText
    private lateinit var etPwd: EditText
    private lateinit var etOtpSecret: EditText
    private lateinit var etOtpCounter: EditText
    private lateinit var spOtpType: Spinner
    private lateinit var llOtpCounter: LinearLayout
    private lateinit var etNotes: EditText
    private lateinit var tvOtpCode: TextView
    private lateinit var pbStrength: ProgressBar
    private lateinit var llCustomFields: LinearLayout
    private lateinit var btnViewHistory: Button

    private var isPasswordVisible = false
    private val customFieldsList = mutableListOf<CustomField>()
    private val customFieldsSensitive = mutableListOf<Boolean>()

    private val otpHandler = Handler(Looper.getMainLooper())
    private val otpRunnable = object : Runnable {
        override fun run() {
            updateOtpDisplay()
            // Repost hanya jika TOTP (index 0)
            if (spOtpType.selectedItemPosition == 0) {
                otpHandler.postDelayed(this, 1000L)
            }
        }
    }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) applyOtpAuthInput(result.contents, silent = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureFlag()
        if (!CryptoManager.isUnlocked()) { redirectToLock(); return }
        setContentView(R.layout.activity_edit)

        etSite = findViewById(R.id.etSite)
        etUser = findViewById(R.id.etUsername)
        etPwd = findViewById(R.id.etPassword)
        etOtpSecret = findViewById(R.id.etOtpSecret)
        etOtpCounter = findViewById(R.id.etOtpCounter)
        spOtpType = findViewById(R.id.spOtpType)
        llOtpCounter = findViewById(R.id.llOtpCounter)
        etNotes = findViewById(R.id.etNotes)
        tvOtpCode = findViewById(R.id.tvOtpCode)
        pbStrength = findViewById(R.id.pbStrength)
        llCustomFields = findViewById(R.id.llCustomFields)
        btnViewHistory = findViewById(R.id.btnViewHistory)

        // Spinner TOTP/HOTP
        spOtpType.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("TOTP (waktu)", "HOTP (counter)")
        )
        spOtpType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                llOtpCounter.visibility = if (pos == 1) View.VISIBLE else View.GONE
                otpHandler.removeCallbacks(otpRunnable)
                if (pos == 0) otpHandler.post(otpRunnable) else updateOtpDisplay()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        etOtpCounter.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updateOtpDisplay() }
        })

        etPwd.doAfterTextChanged { text ->
            val p = text?.toString() ?: ""
            val s = passwordStrength(p)
            pbStrength.progress = s
            pbStrength.progressTintList = android.content.res.ColorStateList.valueOf(
                when (s) {
                    0, 1 -> 0xFFEF5350.toInt()
                    2 -> 0xFFFF9800.toInt()
                    3 -> 0xFFFFEB3B.toInt()
                    4 -> 0xFF8BC34A.toInt()
                    else -> 0xFF4CAF50.toInt()
                })
        }

        findViewById<Button>(R.id.btnRevealPassword).setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            etPwd.inputType = if (isPasswordVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            etPwd.setSelection(etPwd.text.length)
        }

        findViewById<Button>(R.id.btnGeneratePassword).setOnClickListener {
            val pwd = PasswordGenerator.generate(length = 20, symbols = true)
            etPwd.setText(pwd); etPwd.setSelection(pwd.length)
            Toast.makeText(this, "Password baru dibuat", Toast.LENGTH_SHORT).show()
        }

        btnViewHistory.setOnClickListener { showPasswordHistory() }

        findViewById<Button>(R.id.btnOtpPaste).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
            if (text.isEmpty()) { toast("Clipboard kosong"); return@setOnClickListener }
            applyOtpAuthInput(text, silent = true)
        }

        findViewById<Button>(R.id.btnOtpScan).setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan QR OTP"); setBeepEnabled(false); setOrientationLocked(false)
            }
            try { scanLauncher.launch(options) } catch (e: Throwable) { toast("Kamera tidak tersedia") }
        }

        tvOtpCode.setOnClickListener {
            val raw = tvOtpCode.text.toString().replace(" ", "")
            if (raw.length == 6 && raw.all { it.isDigit() })
                ClipboardHelper.copyAndAutoClearDefault(this, "otp", raw)
        }

        findViewById<Button>(R.id.btnAddCustomField).setOnClickListener {
            syncCustomFieldsFromViews()
            customFieldsList.add(CustomField("", "", false))
            customFieldsSensitive.add(false)
            renderCustomFields(autoFocusLast = true)
        }

        findViewById<Button>(R.id.btnCustomFieldTemplates).setOnClickListener {
            showTemplatesMenu()
        }

        val repo = (application as PassenDApplication).repository
        val id = intent.getLongExtra("cred_id", 0L)
        if (id > 0L) {
            lifecycleScope.launch {
                repo.findById(id)?.let {
                    existing = it
                    etSite.setText(it.site); etUser.setText(it.username)
                    etPwd.setText(it.password); etNotes.setText(it.notes)
                    etOtpSecret.setText(it.otpSecret)
                    spOtpType.setSelection(if (it.otpType == "HOTP") 1 else 0)
                    etOtpCounter.setText(it.otpCounter.toString())
                    customFieldsList.clear(); customFieldsSensitive.clear()
                    for (f in it.customFields) {
                        customFieldsList.add(f); customFieldsSensitive.add(f.isSensitive)
                    }
                    renderCustomFields()
                    findViewById<Button>(R.id.btnDelete).visibility = View.VISIBLE
                    val history = repo.getPasswordHistory(it.id)
                    btnViewHistory.visibility = if (history.isNotEmpty()) View.VISIBLE else View.GONE
                }
            }
        } else { renderCustomFields() }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            syncCustomFieldsFromViews()
            val site = etSite.text.toString().trim()
            val pwd = etPwd.text.toString()
            if (site.isEmpty() || pwd.isEmpty()) { toast("Situs & password wajib"); return@setOnClickListener }
            val otpRaw = etOtpSecret.text.toString().trim()
            val otpSecret = if (otpRaw.isEmpty()) "" else {
                if (!Totp.isValidSecret(otpRaw)) { toast("OTP secret tidak valid"); return@setOnClickListener }
                otpRaw.replace(" ", "").uppercase()
            }
            val otpType = if (spOtpType.selectedItemPosition == 1) "HOTP" else "TOTP"
            val otpCounter = etOtpCounter.text.toString().toLongOrNull() ?: 0L
            val cleanCf = customFieldsList.mapIndexedNotNull { idx, f ->
                if (f.label.isBlank() && f.value.isBlank()) null
                else CustomField(f.label, f.value, customFieldsSensitive.getOrElse(idx) { false })
            }
            lifecycleScope.launch {
                repo.save(Credential(
                    id = existing?.id ?: 0L, site = site,
                    username = etUser.text.toString().trim(),
                    password = pwd, notes = etNotes.text.toString(),
                    otpSecret = otpSecret,
                    otpType = otpType, otpCounter = otpCounter,
                    customFields = cleanCf,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                ))
                toast("Tersimpan"); finish()
            }
        }

        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            existing?.let { c ->
                AlertDialog.Builder(this).setTitle("Hapus kredensial?")
                    .setMessage(c.site)
                    .setPositiveButton("Hapus") { _, _ ->
                        lifecycleScope.launch { repo.delete(c.id); finish() }
                    }.setNegativeButton("Batal", null).show()
            }
        }
    }

    private fun showPasswordHistory() {
        val id = existing?.id ?: return
        val repo = (application as PassenDApplication).repository
        lifecycleScope.launch {
            val history = repo.getPasswordHistory(id)
            if (history.isEmpty()) { toast("Belum ada history"); return@launch }
            val fm = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
            val root = LinearLayout(this@EditCredentialActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(8))
            }
            root.addView(TextView(this@EditCredentialActivity).apply {
                text = "Tap password untuk reveal + copy otomatis."
                textSize = 11f; setTextColor(0xFF888888.toInt())
                setPadding(0, 0, 0, dp(8))
            })
            for (h in history) {
                val row = LinearLayout(this@EditCredentialActivity).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(6))
                }
                row.addView(TextView(this@EditCredentialActivity).apply {
                    text = "Diganti: ${fm.format(Date(h.replacedAt))}"
                    textSize = 11f; setTextColor(0xFF888888.toInt())
                })
                val pwdView = TextView(this@EditCredentialActivity).apply {
                    text = "••••••••"; textSize = 15f; setPadding(0, dp(2), 0, 0)
                }
                var revealed = false
                pwdView.setOnClickListener {
                    if (revealed) { pwdView.text = "••••••••"; revealed = false }
                    else {
                        pwdView.text = h.password; revealed = true
                        ClipboardHelper.copyAndAutoClearDefault(this@EditCredentialActivity, "history", h.password)
                    }
                }
                row.addView(pwdView)
                root.addView(row)
            }
            AlertDialog.Builder(this@EditCredentialActivity)
                .setTitle("History Password")
                .setView(root).setPositiveButton("Tutup", null).show()
        }
    }

    private fun renderCustomFields(autoFocusLast: Boolean = false) {
        llCustomFields.removeAllViews()

        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val fieldBg = if (isDark) 0xFF2A2A2A.toInt() else 0xFFF5F5F5.toInt()
        val textColor = if (isDark) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
        val subColor = if (isDark) 0xFFAAAAAA.toInt() else 0xFF666666.toInt()
        val dangerColor = 0xFFC62828.toInt()

        for ((i, f) in customFieldsList.withIndex()) {
            val wrapper = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(fieldBg)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, dp(8)) }
            }

            val rowTop = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val isSens = customFieldsSensitive.getOrElse(i) { false }

            val etLabel = EditText(this).apply {
                hint = "Label"; setText(f.label); textSize = 14f
                setTextColor(textColor); setHintTextColor(subColor)
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(dp(4), dp(8), dp(4), dp(8))
                isSingleLine = true
                inputType = InputType.TYPE_CLASS_TEXT
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val etValue = EditText(this).apply {
                hint = "Value"; setText(f.value); textSize = 14f
                setTextColor(textColor); setHintTextColor(subColor)
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(dp(4), dp(8), dp(4), dp(8))
                isSingleLine = true
                inputType = if (isSens)
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else InputType.TYPE_CLASS_TEXT
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)
                    .apply { marginStart = dp(6) }
            }
            rowTop.addView(etLabel); rowTop.addView(etValue)

            val divider = View(this).apply {
                setBackgroundColor(if (isDark) 0xFF3A3A3A.toInt() else 0xFFE5E5E5.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                ).apply { setMargins(0, dp(2), 0, dp(2)) }
            }

            val rowBot = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val cbSens = CheckBox(this).apply {
                text = "Sensitive"; textSize = 11f
                setTextColor(subColor); isChecked = isSens
                setPadding(0, 0, 0, 0)
                buttonTintList = android.content.res.ColorStateList.valueOf(subColor)
                setOnCheckedChangeListener { _, checked ->
                    while (customFieldsSensitive.size <= i) customFieldsSensitive.add(false)
                    customFieldsSensitive[i] = checked
                    etValue.inputType = if (checked)
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    else InputType.TYPE_CLASS_TEXT
                    etValue.setSelection(etValue.text.length)
                }
            }
            val spacer = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            }

            val btnUp = createIconButton("↑", textColor, i > 0) {
                syncCustomFieldsFromViews()
                if (i > 0) {
                    val tmpF = customFieldsList[i]; customFieldsList[i] = customFieldsList[i - 1]; customFieldsList[i - 1] = tmpF
                    val tmpS = customFieldsSensitive[i]; customFieldsSensitive[i] = customFieldsSensitive[i - 1]; customFieldsSensitive[i - 1] = tmpS
                }
                renderCustomFields()
            }
            val btnDown = createIconButton("↓", textColor, i < customFieldsList.size - 1) {
                syncCustomFieldsFromViews()
                if (i < customFieldsList.size - 1) {
                    val tmpF = customFieldsList[i]; customFieldsList[i] = customFieldsList[i + 1]; customFieldsList[i + 1] = tmpF
                    val tmpS = customFieldsSensitive[i]; customFieldsSensitive[i] = customFieldsSensitive[i + 1]; customFieldsSensitive[i + 1] = tmpS
                }
                renderCustomFields()
            }
            val btnDel = createIconButton("✕", dangerColor, true) {
                syncCustomFieldsFromViews()
                if (i < customFieldsList.size) customFieldsList.removeAt(i)
                if (i < customFieldsSensitive.size) customFieldsSensitive.removeAt(i)
                renderCustomFields()
            }

            rowBot.addView(cbSens); rowBot.addView(spacer)
            rowBot.addView(btnUp); rowBot.addView(btnDown); rowBot.addView(btnDel)

            wrapper.addView(rowTop); wrapper.addView(divider); wrapper.addView(rowBot)
            llCustomFields.addView(wrapper)
        }

        if (autoFocusLast && llCustomFields.childCount > 0) {
            val lastWrapper = llCustomFields.getChildAt(llCustomFields.childCount - 1) as? LinearLayout
            val lastRowTop = lastWrapper?.getChildAt(0) as? LinearLayout
            val lastEtLabel = lastRowTop?.getChildAt(0) as? EditText
            lastEtLabel?.let {
                it.requestFocus()
                it.postDelayed({
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
                            as? android.view.inputmethod.InputMethodManager
                    imm?.showSoftInput(it, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }, 100)
            }
        }
    }

    private fun createIconButton(label: String, color: Int, enabled: Boolean, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label; textSize = 16f; setTextColor(color)
            isAllCaps = false
            minWidth = dp(40); minHeight = dp(36)
            minimumWidth = dp(40); minimumHeight = dp(36)
            setPadding(dp(6), 0, dp(6), 0)
            setBackgroundColor(Color.TRANSPARENT)
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.35f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(2) }
            setOnClickListener { if (enabled) onClick() }
        }
    }

    private fun syncCustomFieldsFromViews() {
        for (i in 0 until llCustomFields.childCount) {
            val wrapper = llCustomFields.getChildAt(i) as? LinearLayout ?: continue
            if (wrapper.childCount < 3) continue
            val rowTop = wrapper.getChildAt(0) as? LinearLayout ?: continue
            if (rowTop.childCount < 2) continue
            val etLabel = rowTop.getChildAt(0) as? EditText ?: continue
            val etValue = rowTop.getChildAt(1) as? EditText ?: continue
            if (i < customFieldsList.size) {
                customFieldsList[i] = CustomField(
                    etLabel.text.toString(),
                    etValue.text.toString(),
                    customFieldsSensitive.getOrElse(i) { false }
                )
            }
        }
    }

    private fun showTemplatesMenu() {
        val templates = com.few.passend.domain.FieldTemplates.all(this)
        if (templates.isEmpty()) { toast("Belum ada template — buat di Pengaturan"); return }
        val names = templates.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Template Custom Field")
            .setItems(names) { _, which ->
                syncCustomFieldsFromViews()
                val t = templates[which]
                for (f in t.fields) {
                    customFieldsList.add(CustomField(f.label, f.value, f.isSensitive))
                    customFieldsSensitive.add(f.isSensitive)
                }
                renderCustomFields(autoFocusLast = true)
            }.show()
    }

    private fun applyOtpAuthInput(text: String, silent: Boolean) {
        val (secret, issuer, account) = parseOtpAuth(text)
        if (secret.isEmpty() || !Totp.isValidSecret(secret)) {
            if (!silent) toast("QR bukan OTP valid"); return
        }
        etOtpSecret.setText(secret)
        if (etSite.text.isBlank() && issuer.isNotEmpty()) etSite.setText(issuer)
        if (etUser.text.isBlank() && account.isNotEmpty()) etUser.setText(account)
        updateOtpDisplay()
        if (!silent) toast("OTP terdeteksi${if (issuer.isNotEmpty()) " — $issuer" else ""}")
    }

    private fun parseOtpAuth(text: String): Triple<String, String, String> {
        if (!text.startsWith("otpauth://", ignoreCase = true))
            return Triple(text.replace(" ", "").uppercase(), "", "")
        try {
            val uri = Uri.parse(text)
            val secret = uri.getQueryParameter("secret")?.replace(" ", "")?.uppercase() ?: ""
            val issuerParam = uri.getQueryParameter("issuer") ?: ""
            val label = uri.path?.trimStart('/') ?: ""
            val issuerFromLabel = if (label.contains(":")) label.substringBefore(":").trim() else ""
            val account = if (label.contains(":")) label.substringAfter(":").trim() else label.trim()
            // Detect HOTP via otpauth://hotp/
            val isHotp = text.lowercase().startsWith("otpauth://hotp")
            if (isHotp) spOtpType.setSelection(1)
            val counterParam = uri.getQueryParameter("counter")?.toLongOrNull() ?: 0L
            if (isHotp && counterParam >= 0L) etOtpCounter.setText(counterParam.toString())
            return Triple(secret, issuerParam.ifEmpty { issuerFromLabel }, account)
        } catch (e: Throwable) { return Triple("", "", "") }
    }

    override fun onResume() {
        super.onResume()
        if (!CryptoManager.isUnlocked()) { redirectToLock(); return }
        otpHandler.removeCallbacks(otpRunnable)
        if (spOtpType.selectedItemPosition == 0) otpHandler.post(otpRunnable) else updateOtpDisplay()
    }
    override fun onPause() { super.onPause(); otpHandler.removeCallbacks(otpRunnable) }

    private fun updateOtpDisplay() {
        val secret = etOtpSecret.text.toString().trim().replace(" ", "")
        if (secret.isEmpty() || !Totp.isValidSecret(secret)) { tvOtpCode.text = "—"; return }
        try {
            if (spOtpType.selectedItemPosition == 1) {
                val counter = etOtpCounter.text.toString().toLongOrNull() ?: 0L
                val code = Hotp.generate(secret, counter)
                tvOtpCode.text = "${code.substring(0, 3)} ${code.substring(3)}"
            } else {
                val r = Totp.generate(secret)
                tvOtpCode.text = "${r.code.substring(0, 3)} ${r.code.substring(3)}"
            }
        } catch (e: Throwable) { tvOtpCode.text = "—" }
    }

    private fun passwordStrength(p: String): Int {
        var s = 0
        if (p.length >= 8) s++
        if (p.length >= 12) s++
        if (p.length >= 16) s++
        if (p.any { it.isLowerCase() } && p.any { it.isUpperCase() }) s++
        if (p.any { it.isDigit() }) s++
        if (p.any { !it.isLetterOrDigit() }) s++
        return s.coerceAtMost(5)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun redirectToLock() {
        startActivity(Intent(this, LockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }); finish()
    }
}