package com.few.passend.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.few.passend.R
import com.few.passend.domain.crypto.CryptoManager
import com.few.passend.domain.password.PassphraseGenerator
import com.few.passend.domain.util.enableSecureFlag

class PassphraseActivity : AppCompatActivity() {

    private var currentPassphrase = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureFlag()
        if (!CryptoManager.isUnlocked()) { finish(); return }
        setContentView(R.layout.activity_passphrase)

        val tvOutput = findViewById<TextView>(R.id.tvPassphrase)
        val tvStats = findViewById<TextView>(R.id.tvStats)
        val sbWords = findViewById<SeekBar>(R.id.sbWordCount)
        val tvWordLabel = findViewById<TextView>(R.id.tvWordCount)
        val etSeparator = findViewById<EditText>(R.id.etSeparator)
        val cbCapitalize = findViewById<CheckBox>(R.id.cbCapitalize)
        val cbAppendDigit = findViewById<CheckBox>(R.id.cbAppendDigit)

        // Slider: progress 0..9 → word count 3..12 (offset +3)
        fun countFromSlider(): Int = sbWords.progress + 3

        fun regenerate() {
            val count = countFromSlider()
            val sep = etSeparator.text.toString().ifEmpty { "-" }
            currentPassphrase = PassphraseGenerator.generate(
                wordCount = count,
                separator = sep,
                capitalize = cbCapitalize.isChecked,
                appendDigit = cbAppendDigit.isChecked
            )
            tvOutput.text = currentPassphrase
            val bits = PassphraseGenerator.entropyBits(count, cbAppendDigit.isChecked)
            val quality = when {
                bits < 40 -> "LEMAH"
                bits < 50 -> "SEDANG"
                bits < 60 -> "KUAT"
                else -> "SANGAT KUAT"
            }
            tvStats.text = "$count kata · ~$bits bit · $quality"
            tvWordLabel.text = "$count kata"
        }

        // max = 9 → progress 0..9 → count 3..12
        sbWords.max = 9
        sbWords.progress = 3 // 3+3 = 6 kata default
        sbWords.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                regenerate()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        etSeparator.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { regenerate() }
        })
        cbCapitalize.setOnCheckedChangeListener { _, _ -> regenerate() }
        cbAppendDigit.setOnCheckedChangeListener { _, _ -> regenerate() }

        findViewById<Button>(R.id.btnRegenerate).setOnClickListener { regenerate() }

        findViewById<Button>(R.id.btnCopy).setOnClickListener {
            if (currentPassphrase.isEmpty()) return@setOnClickListener
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("passphrase", currentPassphrase))
            Toast.makeText(this, "Passphrase disalin", Toast.LENGTH_SHORT).show()
        }

        regenerate()
    }
}