package com.scanpro.pdfdocscanner.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.scanpro.pdfdocscanner.security.GeminiCredentialVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class GeminiDialog { CREATE_PIN, SAVE_KEY, CHANGE_PIN, DELETE_KEY }

/** Settings UI for a device-local Gemini key. The key itself is never displayed. */
@Composable
fun GeminiSecurityCard(context: Context) {
    val vault = remember(context) { GeminiCredentialVault(context) }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<GeminiDialog?>(null) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var oldPin by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun resetFields() {
        pin = ""
        confirmPin = ""
        oldPin = ""
        apiKey = ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Gemini AI Security", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "Set a six-digit PIN before saving your Gemini API key. The key is encrypted with Android Keystore and is not included in the APK.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (vault.hasPin()) "PIN protection: enabled" else "PIN protection: not set",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (vault.hasApiKey()) "Gemini API key: saved (hidden)" else "Gemini API key: not added",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!vault.hasPin()) {
                Button(onClick = { resetFields(); dialog = GeminiDialog.CREATE_PIN }) {
                    Text("Set 6-Digit PIN")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { resetFields(); dialog = GeminiDialog.SAVE_KEY }) {
                        Text(if (vault.hasApiKey()) "Update API Key" else "Add API Key")
                    }
                    OutlinedButton(onClick = { resetFields(); dialog = GeminiDialog.CHANGE_PIN }) {
                        Text("Change PIN")
                    }
                }
                if (vault.hasApiKey()) {
                    OutlinedButton(onClick = { resetFields(); dialog = GeminiDialog.DELETE_KEY }) {
                        Text("Remove API Key")
                    }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        }
    }

    when (dialog) {
        GeminiDialog.CREATE_PIN -> AlertDialog(
            onDismissRequest = { if (!busy) dialog = null },
            title = { Text("Create 6-Digit PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinField("New 6-digit PIN", pin) { pin = it }
                    PinField("Confirm PIN", confirmPin) { confirmPin = it }
                    Text("Choose a PIN you can remember. If you forget it, the saved key cannot be recovered.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    if (pin.length != 6 || pin.any { !it.isDigit() }) message = "PIN must contain exactly six digits."
                    else if (pin != confirmPin) message = "PINs do not match."
                    else {
                        busy = true
                        scope.launch {
                            val result = runCatching { withContext(Dispatchers.IO) { vault.createPin(pin) } }
                            busy = false
                            message = if (result.isSuccess) "PIN protection enabled." else result.exceptionOrNull()?.message ?: "Could not create PIN."
                            if (result.isSuccess) dialog = null
                        }
                    }
                }) { Text(if (busy) "Please wait…" else "Create PIN") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { dialog = null }) { Text("Cancel") } },
        )
        GeminiDialog.SAVE_KEY -> AlertDialog(
            onDismissRequest = { if (!busy) dialog = null },
            title = { Text("Add Gemini API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinField("6-digit PIN", pin) { pin = it }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Gemini API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Text("The key stays hidden and is encrypted on this device. Never share it in screenshots.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    if (pin.length != 6 || apiKey.isBlank()) message = "Enter your six-digit PIN and API key."
                    else {
                        busy = true
                        scope.launch {
                            val result = runCatching { withContext(Dispatchers.IO) { vault.saveApiKey(pin, apiKey) } }
                            busy = false
                            message = if (result.isSuccess) "Gemini API key saved securely." else result.exceptionOrNull()?.message ?: "Could not save API key."
                            if (result.isSuccess) dialog = null
                        }
                    }
                }) { Text(if (busy) "Saving…" else "Save securely") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { dialog = null }) { Text("Cancel") } },
        )
        GeminiDialog.CHANGE_PIN -> AlertDialog(
            onDismissRequest = { if (!busy) dialog = null },
            title = { Text("Change PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinField("Current PIN", oldPin) { oldPin = it }
                    PinField("New 6-digit PIN", pin) { pin = it }
                    PinField("Confirm new PIN", confirmPin) { confirmPin = it }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    if (pin.length != 6 || pin.any { !it.isDigit() }) message = "New PIN must contain six digits."
                    else if (pin != confirmPin) message = "New PINs do not match."
                    else {
                        busy = true
                        scope.launch {
                            val result = runCatching { withContext(Dispatchers.IO) { vault.changePin(oldPin, pin) } }
                            busy = false
                            message = if (result.isSuccess) "PIN changed." else result.exceptionOrNull()?.message ?: "Could not change PIN."
                            if (result.isSuccess) dialog = null
                        }
                    }
                }) { Text(if (busy) "Please wait…" else "Change PIN") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { dialog = null }) { Text("Cancel") } },
        )
        GeminiDialog.DELETE_KEY -> AlertDialog(
            onDismissRequest = { if (!busy) dialog = null },
            title = { Text("Remove Gemini API Key?") },
            text = { PinField("6-digit PIN", pin) { pin = it } },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    if (pin.length != 6) message = "Enter your six-digit PIN."
                    else {
                        busy = true
                        scope.launch {
                            val result = runCatching { withContext(Dispatchers.IO) { vault.clearApiKey(pin) } }
                            busy = false
                            message = if (result.isSuccess) "API key removed." else result.exceptionOrNull()?.message ?: "Could not remove API key."
                            if (result.isSuccess) dialog = null
                        }
                    }
                }) { Text(if (busy) "Removing…" else "Remove key") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { dialog = null }) { Text("Cancel") } },
        )
        null -> Unit
    }
}

@Composable
private fun PinField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter(Char::isDigit).take(6)) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}
