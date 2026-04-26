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
 * Google Gemini Flash provider.
 * Recommended default — generous free tier, strong HTML understanding.
 * Get a free key at: https://aistudio.google.com
 */
class GeminiProvider(private val apiKey: String) : AIProvider {

    override val name = "Google Gemini"
    override val description = "Gemini 1.5 Flash · Free tier · Recommended"
    override val requiresApiKey = true
    override val apiKeyUrl = "https://aistudio.google.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    override suspend fun complete(prompt: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)       // Low temp = more predictable JSON
                put("maxOutputTokens", 4096)
            })
        }

        val request = Request.Builder()
            .url("$baseUrl?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw AIProviderException("Empty response from Gemini")

        if (!response.isSuccessful) {
            throw AIProviderException("Gemini API error ${response.code}: $responseBody")
        }

        // Extract text from Gemini response structure
        val json = JSONObject(responseBody)
        json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    override suspend fun validateKey(): Boolean = try {
        complete("Reply with the word OK and nothing else.")
        true
    } catch (e: Exception) {
        false
    }
}
