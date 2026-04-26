package com.beam.ai

/**
 * AIProvider defines the contract for any AI backend Beam can use.
 * Users can switch between Gemini, Groq, or Ollama in Settings.
 */
interface AIProvider {

    /**
     * The display name shown in Settings (e.g. "Google Gemini")
     */
    val name: String

    /**
     * A short description shown in Settings
     */
    val description: String

    /**
     * Whether this provider needs an API key (Ollama doesn't)
     */
    val requiresApiKey: Boolean

    /**
     * URL where the user can get a free API key
     */
    val apiKeyUrl: String?

    /**
     * Send a prompt and receive a text response.
     * Throws [AIProviderException] on failure.
     */
    suspend fun complete(prompt: String): String

    /**
     * Validate that the current API key works.
     * Returns true if valid, false otherwise.
     */
    suspend fun validateKey(): Boolean
}

class AIProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)
