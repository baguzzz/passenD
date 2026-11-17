package com.few.passend.domain.password

import java.security.SecureRandom
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Diceware-style passphrase generator: kata acak yang mudah diingat.
 * Wordlist 200 kata umum Inggris. Entropy:
 *  - 3 kata  = ~23 bit (LEMAH)
 *  - 4 kata  = ~31 bit (LEMAH)
 *  - 6 kata  = ~46 bit (SEDANG)
 *  - 8 kata  = ~61 bit (KUAT)
 *  - 10 kata = ~76 bit (SANGAT KUAT)
 *  - 12 kata = ~92 bit (SANGAT KUAT)
 */
object PassphraseGenerator {
    private val WORDS = listOf(
        "abacus","abbey","about","acorn","actor","adapt","adult","after","again","agent",
        "agile","agree","ahead","alarm","album","alert","alien","align","alive","allow",
        "alpha","altar","amber","amino","ample","angel","anger","angle","angry","ankle",
        "apple","apply","apron","arbor","arena","argue","arise","armor","aroma","array",
        "arrow","aside","asset","atlas","audio","audit","avail","avert","awake","award",
        "aware","axis","azure","badge","bagel","baker","bamboo","banjo","basic","basil",
        "basin","batch","beach","beard","beast","begin","below","bench","berry","blank",
        "blaze","blend","blink","block","bloom","board","bonus","boost","booth","bounce",
        "bound","brain","brake","brave","bread","break","breed","brick","bride","brief",
        "bring","brisk","broad","brook","brush","budget","build","bulk","bunch","bundle",
        "burrow","burst","cabin","cable","cache","cadet","camel","canal","candy","cannon",
        "cargo","carve","catch","cause","cedar","chain","chair","chalk","charm","chart",
        "chase","cheer","chess","chest","chief","child","chill","choice","choir","chord",
        "cider","cigar","civic","civil","claim","clash","clasp","class","claw","clay",
        "clean","clear","clerk","click","cliff","climb","cling","cloak","clock","close",
        "cloth","cloud","clown","coach","coast","cobra","cocoa","colon","color","combo",
        "comet","comic","coral","core","corner","couch","cough","count","court","cover",
        "craft","crane","crash","crate","crawl","crazy","cream","creek","crest","crew",
        "crisp","crop","cross","crowd","crown","crust","cubic","curly","curve","cycle"
    )

    private val rng = SecureRandom()

    /**
     * @param wordCount jumlah kata (3-12)
     * @param separator pemisah antar kata (contoh: "-", ".", " ", "_")
     * @param capitalize kapitalisasi tiap kata
     * @param appendDigit tambahkan 1 digit acak di akhir
     */
    fun generate(
        wordCount: Int = 6,
        separator: String = "-",
        capitalize: Boolean = true,
        appendDigit: Boolean = false
    ): String {
        val count = wordCount.coerceIn(3, 12)
        val words = ArrayList<String>(count)
        for (i in 0 until count) {
            val idx = rng.nextInt(WORDS.size)
            var w = WORDS[idx]
            if (capitalize) w = w.replaceFirstChar { it.uppercase() }
            words.add(w)
        }
        var result = words.joinToString(separator)
        if (appendDigit) result = result + rng.nextInt(10).toString()
        return result
    }

    /**
     * Estimasi entropy dalam bit untuk word count tertentu.
     * FIX: pakai log2 presisi (bukan truncated int) + roundToInt.
     * Hasil konsisten dengan extension (popup.js pakai Math.log2(200)).
     */
    fun entropyBits(wordCount: Int, appendDigit: Boolean = false): Int {
        val bitsPerWord = log2(WORDS.size.toDouble())       // 7.6438...
        val digitBits = if (appendDigit) log2(10.0) else 0.0  // 3.3219...
        return (wordCount.coerceIn(3, 12) * bitsPerWord + digitBits).roundToInt()
    }
}