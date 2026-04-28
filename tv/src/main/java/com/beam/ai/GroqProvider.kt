package com.beam.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Groq provider — runs Llama 3 with extremely fast inference.
 * Free tier available at: https://console.groq.com
 */
class GroqProvider(private val apiKey: String) : AIProvider {

    override val name = "Groq"
    override val description = "Llama 3 70B · Free tier · Very fast"
    override val requiresApiKey = true
    override val apiKeyUrl = "https://console.groq.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun complete(prompt: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("temperature", 0.1)
            put("max_tokens", 4096)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw AIProviderException("Empty response from Groq")

        if (!response.isSuccessful) {
            throw AIProviderException("Groq API error ${response.code}: $responseBody")
        }

        JSONObject(responseBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    override suspend fun validateKey(): Boolean = try {
        complete("Reply with the word OK and nothing else.")
        true
    } catch (e: Exception) {
        false
    }
}
