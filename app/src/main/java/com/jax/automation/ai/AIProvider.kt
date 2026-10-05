package com.jax.automation.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

// ---------------------------------------------------------------------------
// AI provider abstraction. JAX is never locked to one provider: the planner,
// prompt and QA roles can each point at a different provider/model.
// API keys are NEVER stored here — they live in SecureStorage and are passed
// in per call. Keys must never be written to logs.
// ---------------------------------------------------------------------------

enum class ProviderRole { PLANNER, PROMPT, QA }

data class ResolvedProvider(
    val provider: AIProvider,
    val apiKey: String,
    val model: String
)

class AIProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface AIProvider {
    val id: String
    val displayName: String
    suspend fun chatText(
        systemPrompt: String,
        userPrompt: String,
        apiKey: String,
        model: String
    ): String
    suspend fun chatVision(
        systemPrompt: String,
        userPrompt: String,
        imageBytes: ByteArray,
        mimeType: String,
        apiKey: String,
        model: String
    ): String
    suspend fun testConnection(apiKey: String, model: String): Boolean
}

private fun postJson(
    url: String,
    headers: Map<String, String>,
    body: JSONObject,
    connectTimeoutMs: Int = 20_000,
    readTimeoutMs: Int = 120_000
): JSONObject = runBlockingIo {
    var conn: HttpURLConnection? = null
    try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            throw AIProviderException("HTTP $code: ${text.take(600)}")
        }
        JSONObject(text)
    } finally {
        conn?.disconnect()
    }
}

private fun <T> runBlockingIo(block: () -> T): T =
    kotlinx.coroutines.runBlocking(Dispatchers.IO) { block() }

private fun geminiTextOf(response: JSONObject): String {
    val parts = response.getJSONArray("candidates")
        .getJSONObject(0)
        .getJSONObject("content")
        .getJSONArray("parts")
    val sb = StringBuilder()
    for (i in 0 until parts.length()) {
        sb.append(parts.getJSONObject(i).optString("text"))
    }
    val out = sb.toString().trim()
    if (out.isEmpty()) throw AIProviderException("Gemini returned empty content")
    return out
}

/** https://generativelanguage.googleapis.com/v1beta */
class GeminiProvider : AIProvider {
    override val id = "gemini"
    override val displayName = "Gemini"

    private fun endpoint(model: String, apiKey: String) =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    private fun body(systemPrompt: String, userPrompt: String, image: Pair<ByteArray, String>?): JSONObject {
        val parts = JSONArray()
        if (image != null) {
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", image.second)
                        .put("data", Base64.getEncoder().encodeToString(image.first))
                )
            )
        }
        parts.put(JSONObject().put("text", userPrompt))
        return JSONObject()
            .put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", 0.2))
    }

    override suspend fun chatText(
        systemPrompt: String, userPrompt: String, apiKey: String, model: String
    ): String = withContext(Dispatchers.IO) {
        try {
            geminiTextOf(postJson(endpoint(model, apiKey), emptyMap(), body(systemPrompt, userPrompt, null)))
        } catch (e: AIProviderException) {
            throw e
        } catch (e: Exception) {
            throw AIProviderException("Gemini request failed: ${e.message}", e)
        }
    }

    override suspend fun chatVision(
        systemPrompt: String, userPrompt: String, imageBytes: ByteArray,
        mimeType: String, apiKey: String, model: String
    ): String = withContext(Dispatchers.IO) {
        try {
            geminiTextOf(
                postJson(
                    endpoint(model, apiKey), emptyMap(),
                    body(systemPrompt, userPrompt, imageBytes to mimeType)
                )
            )
        } catch (e: AIProviderException) {
            throw e
        } catch (e: Exception) {
            throw AIProviderException("Gemini vision request failed: ${e.message}", e)
        }
    }

    override suspend fun testConnection(apiKey: String, model: String): Boolean =
        runCatching {
            chatText("You are a connectivity test.", "Reply with exactly: OK", apiKey, model)
        }.map { it.isNotBlank() }.getOrDefault(false)
}

/** https://api.openai.com/v1 — also the base for the "Other" OpenAI-compatible provider. */
open class OpenAIProvider(
    private val baseUrl: String = "https://api.openai.com/v1",
    override val id: String = "openai",
    override val displayName: String = "OpenAI"
) : AIProvider {

    private fun headers(apiKey: String) = mapOf("Authorization" to "Bearer $apiKey")

    private fun userContent(userPrompt: String, image: Pair<ByteArray, String>?): Any {
        if (image == null) return userPrompt
        val dataUri = "data:${image.second};base64,${Base64.getEncoder().encodeToString(image.first)}"
        return JSONArray()
            .put(JSONObject().put("type", "text").put("text", userPrompt))
            .put(
                JSONObject().put("type", "image_url")
                    .put("image_url", JSONObject().put("url", dataUri))
            )
    }

    private fun body(systemPrompt: String, userPrompt: String, model: String, image: Pair<ByteArray, String>?): JSONObject =
        JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .put("max_tokens", 2048)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userContent(userPrompt, image)))
            )

    private fun textOf(response: JSONObject): String {
        val out = response.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .optString("content")
            .trim()
        if (out.isEmpty()) throw AIProviderException("OpenAI-compatible endpoint returned empty content")
        return out
    }

    override suspend fun chatText(
        systemPrompt: String, userPrompt: String, apiKey: String, model: String
    ): String = withContext(Dispatchers.IO) {
        try {
            textOf(postJson("$baseUrl/chat/completions", headers(apiKey), body(systemPrompt, userPrompt, model, null)))
        } catch (e: AIProviderException) {
            throw e
        } catch (e: Exception) {
            throw AIProviderException("OpenAI-compatible request failed: ${e.message}", e)
        }
    }

    override suspend fun chatVision(
        systemPrompt: String, userPrompt: String, imageBytes: ByteArray,
        mimeType: String, apiKey: String, model: String
    ): String = withContext(Dispatchers.IO) {
        try {
            textOf(
                postJson(
                    "$baseUrl/chat/completions", headers(apiKey),
                    body(systemPrompt, userPrompt, model, imageBytes to mimeType)
                )
            )
        } catch (e: AIProviderException) {
            throw e
        } catch (e: Exception) {
            throw AIProviderException("OpenAI-compatible vision request failed: ${e.message}", e)
        }
    }

    override suspend fun testConnection(apiKey: String, model: String): Boolean =
        runCatching {
            chatText("You are a connectivity test.", "Reply with exactly: OK", apiKey, model)
        }.map { it.isNotBlank() }.getOrDefault(false)
}

/** https://api.anthropic.com/v1 */
class ClaudeProvider : AIProvider {
    override val id = "claude"
    override val displayName = "Claude"

    private fun headers(apiKey: String) = mapOf(
        "x-api-key" to apiKey,
        "anthropic-version" to "2023-06-01"
    )

    private fun content(userPrompt: String, image: Pair<ByteArray, String>?): JSONArray {
        val arr = JSONArray()
        if (image != null) {
            arr.put(
                JSONObject().put("type", "image").put(
                    "source",
                    JSONObject()
                        .put("type", "base64")
                        .put("media_type", image.second)
                        .put("data", Base64.getEncoder().encodeToString(image.first))
                )
            )
        }
        arr.put(JSONObject().put("type", "text").put("text", userPrompt))
        return arr
    }

    private fun body(systemPrompt: String, userPrompt: String, model: String, image: Pair<ByteArray, String>?): JSONObject =
        JSONObject()
            .put("model", model)
            .put("max_tokens", 2048)
            .put("temperature", 0.2)
            .put("system", systemPrompt)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", content(userPrompt, image))
                )
            )

    private fun textOf(response: JSONObject): String {
        val blocks = response.getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until blocks.length()) {
            val b = blocks.getJSONObject(i)
            if (b.optString("type") == "text") sb.append(b.optString("text"))
        }
        val out = sb.toString().trim()
        if (out.isEmpty()) throw AIProviderException("Claude returned empty content")
        return out
    }

    override suspend fun chatText(
        systemPrompt: String, userPrompt: String, apiKey: String, model: String
    ): String = withContext(Dispatchers.IO) {
        try {
            textOf(postJson("https://api.anthropic.com/v1/messages", headers(apiKey), body(systemPrompt, userPrompt, model, null)))
        } catch (e: AIProviderException) {
            throw e
        } catch (e: Exception) {
            throw AIProviderException("Claude request failed: ${e.message}", e)
        }
    }

    override suspend fun chatVision(
        systemPrompt: String, userPrompt: String, imageBytes: ByteArray,
        mimeType: String, apiKey: String, model: String
    ): String = withContext(Dispatchers.IO) {
        try {
            textOf(
                postJson(
                    "https://api.anthropic.com/v1/messages", headers(apiKey),
                    body(systemPrompt, userPrompt, model, imageBytes to mimeType)
                )
            )
        } catch (e: AIProviderException) {
            throw e
        } catch (e: Exception) {
            throw AIProviderException("Claude vision request failed: ${e.message}", e)
        }
    }

    override suspend fun testConnection(apiKey: String, model: String): Boolean =
        runCatching {
            chatText("You are a connectivity test.", "Reply with exactly: OK", apiKey, model)
        }.map { it.isNotBlank() }.getOrDefault(false)
}
