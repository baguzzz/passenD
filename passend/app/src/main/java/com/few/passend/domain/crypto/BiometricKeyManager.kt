package com.few.passend.domain.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object BiometricKeyManager {
    private const val ALIAS = "passend_biometric_key"
    private const val GCM_TAG = 128

    /** Buat kunci baru di Android Keystore, biometric-gated. */
    fun createKey() {
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val builder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        kg.init(builder.build())
        kg.generateKey()
    }

    fun deleteKey() {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
        } catch (_: Throwable) { }
    }

    /**
     * Cipher untuk enkripsi. Bisa melempar KeyPermanentlyInvalidatedException
     * jika biometric enrollment berubah.
     */
    fun getCipherForEncrypt(): Cipher {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = ks.getKey(ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("Biometric key not found")
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
    }

    fun getCipherForDecrypt(iv: ByteArray): Cipher {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = ks.getKey(ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("Biometric key not found")
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG, iv))
        }
    }

    fun isInvalidated(e: Throwable): Boolean =
        e is KeyPermanentlyInvalidatedException ||
        (e.cause is KeyPermanentlyInvalidatedException)
}