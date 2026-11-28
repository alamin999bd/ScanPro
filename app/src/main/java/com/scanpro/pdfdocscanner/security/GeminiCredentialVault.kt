package com.scanpro.pdfdocscanner.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Device-local storage for a user's Gemini API key.
 *
 * The API key is never compiled into the APK. It is encrypted with an Android Keystore AES key;
 * the six-digit PIN is checked before any decrypt operation. Clearing app data / uninstalling
 * removes the device-bound Keystore key, so the encrypted value cannot be migrated to another
 * installation by copying the APK or preferences.
 */
class GeminiCredentialVault(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    @Synchronized
    fun hasPin(): Boolean = prefs.contains(KEY_PIN_SALT) && prefs.contains(KEY_PIN_HASH)

    @Synchronized
    fun hasApiKey(): Boolean = prefs.contains(KEY_CIPHER_TEXT) && prefs.contains(KEY_IV)

    @Synchronized
    fun createPin(pin: String) {
        requireValidPin(pin)
        check(!hasPin()) { "A PIN is already configured." }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derivePinHash(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply()
    }

    @Synchronized
    fun verifyPin(pin: String): Boolean {
        requireValidPin(pin)
        val saltText = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val hashText = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = Base64.decode(saltText, Base64.NO_WRAP)
        val expected = Base64.decode(hashText, Base64.NO_WRAP)
        val actual = derivePinHash(pin, salt)
        val valid = constantTimeEquals(expected, actual)
        if (valid) prefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).apply()
        else prefs.edit().putInt(KEY_FAILED_ATTEMPTS, failedAttempts() + 1).apply()
        return valid
    }

    @Synchronized
    fun saveApiKey(pin: String, apiKey: String) {
        require(apiKey.isNotBlank()) { "API key cannot be empty." }
        check(verifyPin(pin)) { "Incorrect PIN." }
        val iv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(KEY_CIPHER_TEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun readApiKey(pin: String): String? {
        check(verifyPin(pin)) { "Incorrect PIN." }
        val ivText = prefs.getString(KEY_IV, null) ?: return null
        val cipherText = prefs.getString(KEY_CIPHER_TEXT, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateAesKey(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivText, Base64.NO_WRAP))
        )
        return cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    @Synchronized
    fun changePin(oldPin: String, newPin: String) {
        requireValidPin(newPin)
        check(verifyPin(oldPin)) { "Incorrect current PIN." }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derivePinHash(newPin, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun clearApiKey(pin: String) {
        check(verifyPin(pin)) { "Incorrect PIN." }
        prefs.edit().remove(KEY_IV).remove(KEY_CIPHER_TEXT).apply()
    }

    /** Deletes the credential and PIN. The app's unrelated documents/settings are untouched. */
    @Synchronized
    fun resetSecurity(pin: String) {
        check(verifyPin(pin)) { "Incorrect PIN." }
        prefs.edit().clear().apply()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun failedAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    private fun getOrCreateAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun derivePinHash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun requireValidPin(pin: String) {
        require(pin.length == 6 && pin.all(Char::isDigit)) { "PIN must contain exactly six digits." }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private companion object {
        const val PREFS = "gemini_credential_vault"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val KEY_IV = "api_key_iv"
        const val KEY_CIPHER_TEXT = "api_key_cipher_text"
        const val KEY_ALIAS = "document_scanner_gemini_key_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PBKDF2 = "PBKDF2WithHmacSHA256"
        const val PBKDF2_ITERATIONS = 310_000
        const val HASH_BITS = 256
        const val SALT_BYTES = 16
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
