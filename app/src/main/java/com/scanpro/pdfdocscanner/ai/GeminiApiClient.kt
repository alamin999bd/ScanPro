package com.scanpro.pdfdocscanner.ai
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** User-triggered Gemini REST client. API keys are never logged or persisted here. */
object GeminiApiClient {
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"
    suspend fun generate(apiKey: String, prompt: String): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Gemini API key is not configured." }
        require(prompt.isNotBlank()) { "The request is empty." }
        val payload = JSONObject().put("contents", org.json.JSONArray().put(
            JSONObject().put("role", "user").put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt)))
        )).put("generationConfig", JSONObject().put("temperature", 0.3).put("maxOutputTokens", 2048))
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15_000; readTimeout = 90_000; doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (status !in 200..299) {
                val detail = json?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                throw IllegalStateException(when (status) {
                    400 -> "Gemini request was rejected. Check the API key and request."
                    401, 403 -> "Gemini API key is invalid or does not have access."
                    429 -> "Gemini usage limit reached. Try again later."
                    else -> detail?.take(240) ?: "Gemini request failed (HTTP $status)."
                })
            }
            val parts = json?.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IllegalStateException("Gemini returned no answer.")
            val answer = buildString {
                for (i in 0 until parts.length()) parts.optJSONObject(i)?.optString("text")
                    ?.takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append("\n"); append(it) }
            }.trim()
            answer.ifBlank { throw IllegalStateException("Gemini returned an empty answer.") }
        } finally { connection.disconnect() }
    }
}
