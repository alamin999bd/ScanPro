package com.scanpro.pdfdocscanner.security

import java.security.KeyStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeminiCredentialVaultTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var vault: GeminiCredentialVault

    @Before
    fun setUp() {
        context.getSharedPreferences("gemini_credential_vault", 0).edit().clear().commit()
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            if (containsAlias("document_scanner_gemini_key_v1")) {
                deleteEntry("document_scanner_gemini_key_v1")
            }
        }
        vault = GeminiCredentialVault(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("gemini_credential_vault", 0).edit().clear().commit()
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            if (containsAlias("document_scanner_gemini_key_v1")) {
                deleteEntry("document_scanner_gemini_key_v1")
            }
        }
    }

    @Test
    fun rejectsNonSixDigitPin() {
        val error = runCatching { vault.createPin("12345") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertFalse(vault.hasPin())
    }

    @Test
    fun encryptsAndReadsApiKeyOnlyWithCorrectPin() {
        vault.createPin("482916")
        vault.saveApiKey("482916", "test-gemini-key-not-real")
        assertTrue(vault.hasApiKey())
        assertEquals("test-gemini-key-not-real", vault.readApiKey("482916"))
        assertFalse(vault.verifyPin("111111"))
    }

    @Test
    fun changingPinKeepsEncryptedApiKeyAvailable() {
        vault.createPin("482916")
        vault.saveApiKey("482916", "test-gemini-key-not-real")
        vault.changePin("482916", "731405")
        assertFalse(vault.verifyPin("482916"))
        assertEquals("test-gemini-key-not-real", vault.readApiKey("731405"))
    }

    @Test
    fun removesApiKeyWithoutRemovingPin() {
        vault.createPin("482916")
        vault.saveApiKey("482916", "test-gemini-key-not-real")
        vault.clearApiKey("482916")
        assertFalse(vault.hasApiKey())
        assertTrue(vault.hasPin())
    }
}
