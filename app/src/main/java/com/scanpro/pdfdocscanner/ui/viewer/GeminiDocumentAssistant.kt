package com.scanpro.pdfdocscanner.ui.viewer
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.scanpro.pdfdocscanner.ai.GeminiApiClient
import com.scanpro.pdfdocscanner.security.GeminiCredentialVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GeminiDocumentAssistant(context: Context, onExtractText: suspend () -> String, onDismiss: () -> Unit) {
    val vault = remember(context) { GeminiCredentialVault(context) }
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun ask(task: String) {
        if (busy) return
        if (pin.length != 6 || pin.any { !it.isDigit() }) { error = "Enter your six-digit PIN."; return }
        busy = true; error = null; answer = ""
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val key = vault.readApiKey(pin) ?: throw IllegalStateException("No Gemini API key is saved. Add it in Settings.")
                    val text = onExtractText()
                    val prompt = """
                        Use only the supplied document for document-specific facts. If information is missing, say so.
                        Task: $task
                        User question: \${question.trim().ifBlank { "None" }}
                        Reply in clear, simple Bengali unless another language is explicitly requested.
                        Document text:
                        $text
                    """.trimIndent()
                    GeminiApiClient.generate(key, prompt)
                }
                answer = result
            } catch (e: Exception) { error = e.message ?: "Could not contact Gemini." }
            finally { busy = false }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Gemini PDF Assistant") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Chosen PDF text will be sent to Google Gemini over the internet. Do not send private or sensitive documents.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit PIN") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(question, { question = it.take(500) }, label = { Text("Ask a question (optional)") }, modifier = Modifier.fillMaxWidth())
                Text("Quick actions", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(enabled = !busy, onClick = { ask("Summarize this document with its key points.") }) { Text("Summary") }
                    Button(enabled = !busy, onClick = { ask("Translate the document into Bengali, preserving names and numbers.") }) { Text("বাংলা") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(enabled = !busy, onClick = { ask("Create concise, organized smart notes from this document.") }) { Text("Smart Notes") }
                    Button(enabled = !busy, onClick = { ask("Explain this document in simple terms.") }) { Text("Explain") }
                }
                Button(enabled = !busy && question.isNotBlank(), onClick = { ask("Answer the user's question using only the document.") },
                    modifier = Modifier.fillMaxWidth()) { Text("Ask PDF") }
                if (busy) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(); Text("Contacting Gemini…")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (answer.isNotBlank()) { Text("AI response", style = MaterialTheme.typography.titleSmall); Text(answer) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Close") } }
    )
}
