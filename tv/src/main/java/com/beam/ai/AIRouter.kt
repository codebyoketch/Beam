package com.beam.ai

import android.content.Context
import android.util.Log

/**
 * Task types — each has different token requirements
 */
enum class AITask {
    PAGE_ANALYSIS,   // Heavy — large HTML input
    STREAM_EXTRACT,  // Light — small HTML, quick response
    VALIDATION       // Tiny — just checking a key works
}

/**
 * AIRouter selects the best AI provider for each task based on:
 * 1. User preferences (primary/secondary/fallback providers)
 * 2. Current token usage — avoids providers near their daily limit
 * 3. Task requirements — routes heavy tasks to high-capacity providers
 *
 * Users configure up to 3 providers in Settings:
 * - Primary: used for page analysis (needs large context)
 * - Secondary: used for stream extraction (fast, low tokens)
 * - Fallback: used when others are near limit (Ollama recommended)
 */
class AIRouter(
    private val context: Context,
    private val tokenTracker: TokenTracker
) {

    companion object {
        private const val TAG = "AIRouter"

        // Prefs keys
        const val PREF_PRIMARY_PROVIDER = "primary_provider"
        const val PREF_PRIMARY_KEY = "primary_key"
        const val PREF_SECONDARY_PROVIDER = "secondary_provider"
        const val PREF_SECONDARY_KEY = "secondary_key"
        const val PREF_FALLBACK_PROVIDER = "fallback_provider"
        const val PREF_FALLBACK_KEY = "fallback_key"
        const val PREF_OLLAMA_HOST = "ollama_host"
    }

    private val prefs by lazy {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context, "beam_secure_prefs", masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Returns the best available provider for the given task.
     * Falls through primary → secondary → fallback based on token usage.
     */
    suspend fun getProvider(task: AITask): AIProvider? {
        Log.d(TAG, "Selecting provider for task: $task")

        val candidates = when (task) {
            AITask.PAGE_ANALYSIS -> listOf(
                getPrimaryProvider(),
                getSecondaryProvider(),
                getFallbackProvider()
            )
            AITask.STREAM_EXTRACT -> listOf(
                getSecondaryProvider(),
                getPrimaryProvider(),
                getFallbackProvider()
            )
            AITask.VALIDATION -> listOf(
                getSecondaryProvider() ?: getPrimaryProvider(),
                getFallbackProvider()
            )
        }

        for (provider in candidates) {
            if (provider == null) continue

            val providerKey = providerKey(provider)
            val nearLimit = tokenTracker.isNearLimit(providerKey)

            if (!nearLimit) {
                Log.d(TAG, "Selected provider: ${provider.name} for $task")
                return provider
            } else {
                Log.w(TAG, "${provider.name} is near its daily limit — trying next")
            }
        }

        // All providers near limit — return fallback anyway
        Log.w(TAG, "All providers near limit — using fallback")
        return getFallbackProvider() ?: getPrimaryProvider()
    }

    /**
     * Records token usage after a successful AI call.
     */
    suspend fun recordUsage(provider: AIProvider, task: AITask, prompt: String, response: String) {
        tokenTracker.record(providerKey(provider), task.name, prompt, response)
    }

    private fun getPrimaryProvider(): AIProvider? {
        val key = prefs.getString(PREF_PRIMARY_KEY, "") ?: ""
        return when (prefs.getString(PREF_PRIMARY_PROVIDER, "gemini")) {
            "groq" -> if (key.isNotBlank()) GroqProvider(key) else null
            "ollama" -> OllamaProvider(host = prefs.getString(PREF_OLLAMA_HOST, "") ?: "")
            else -> if (key.isNotBlank()) GeminiProvider(key) else null
        }
    }

    private fun getSecondaryProvider(): AIProvider? {
        val key = prefs.getString(PREF_SECONDARY_KEY, "") ?: ""
        if (key.isBlank() && prefs.getString(PREF_SECONDARY_PROVIDER, "") != "ollama") return null
        return when (prefs.getString(PREF_SECONDARY_PROVIDER, "")) {
            "gemini" -> if (key.isNotBlank()) GeminiProvider(key) else null
            "groq" -> if (key.isNotBlank()) GroqProvider(key) else null
            "ollama" -> OllamaProvider(host = prefs.getString(PREF_OLLAMA_HOST, "") ?: "")
            else -> null
        }
    }

    private fun getFallbackProvider(): AIProvider? {
        val key = prefs.getString(PREF_FALLBACK_KEY, "") ?: ""
        return when (prefs.getString(PREF_FALLBACK_PROVIDER, "ollama")) {
            "gemini" -> if (key.isNotBlank()) GeminiProvider(key) else null
            "groq" -> if (key.isNotBlank()) GroqProvider(key) else null
            "ollama" -> OllamaProvider(host = prefs.getString(PREF_OLLAMA_HOST, "") ?: "")
            else -> null
        }
    }

    private fun providerKey(provider: AIProvider): String = when (provider) {
        is GeminiProvider -> "gemini"
        is GroqProvider -> "groq"
        is OllamaProvider -> "ollama"
        else -> "unknown"
    }
}
