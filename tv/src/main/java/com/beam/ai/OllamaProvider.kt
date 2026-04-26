package com.beam.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Ollama provider — runs AI models fully locally on your own machine.
 * No API key needed. Requires Ollama running on the same network.
 * Get it at: https://ollama.com
 *
 * Setup: run `ollama pull llama3` on your computer, then set the
 * host IP in Beam's settings (e.g. http://192.168.1.10:11434)
 */
class OllamaProvider(
    private val host: String = "http://localhost:11434",
    private val model: String = "llama3"
) : AIProvider {

    override val name = "Ollama (Local)"
    override val description = "Runs on your own machine · No key needed · Private"
    override val requiresApiKey = false
    override val apiKeyUrl = "https://ollama.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // Local models can be slow
        .build()

    override suspend fun complete(prompt: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
            put("stream", false)
            put("options", JSONObject().apply {
                put("temperature", 0.1)
            })
        }

        val request = Request.Builder()
            .url("$host/api/generate")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw AIProviderException("Empty response from Ollama")

        if (!response.isSuccessful) {
            throw AIProviderException("Ollama error ${response.code}: $responseBody")
        }

        JSONObject(responseBody).getString("response")
    }

    override suspend fun validateKey(): Boolean = try {
        // Just check if Ollama is reachable
        val request = Request.Builder().url("$host/api/tags").build()
        client.newCall(request).execute().isSuccessful
    } catch (e: Exception) {
        false
    }
}
