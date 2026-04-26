package com.beam.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.beam.R
import com.beam.ai.GeminiProvider
import com.beam.ai.GroqProvider
import com.beam.ai.OllamaProvider
import kotlinx.coroutines.launch

/**
 * Settings screen where users configure their AI provider and API key.
 * This is shown on first launch and accessible from the home screen.
 */
class SettingsFragment : Fragment() {

    companion object {
        const val PREF_PROVIDER = "ai_provider"
        const val PREF_API_KEY = "api_key"
        const val PREF_OLLAMA_HOST = "ollama_host"

        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_GROQ = "groq"
        const val PROVIDER_OLLAMA = "ollama"
    }

    private lateinit var providerSpinner: Spinner
    private lateinit var apiKeyInput: EditText
    private lateinit var ollamaHostInput: EditText
    private lateinit var apiKeySection: LinearLayout
    private lateinit var ollamaSection: LinearLayout
    private lateinit var validateButton: Button
    private lateinit var saveButton: Button
    private lateinit var statusText: TextView
    private lateinit var getKeyButton: Button

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(requireContext())
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            requireContext(),
            "beam_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        providerSpinner = view.findViewById(R.id.providerSpinner)
        apiKeyInput = view.findViewById(R.id.apiKeyInput)
        ollamaHostInput = view.findViewById(R.id.ollamaHostInput)
        apiKeySection = view.findViewById(R.id.apiKeySection)
        ollamaSection = view.findViewById(R.id.ollamaSection)
        validateButton = view.findViewById(R.id.validateButton)
        saveButton = view.findViewById(R.id.saveButton)
        statusText = view.findViewById(R.id.statusText)
        getKeyButton = view.findViewById(R.id.getKeyButton)

        setupProviderSpinner()
        loadSavedSettings()
        setupListeners()
    }

    private fun setupProviderSpinner() {
        val providers = listOf(
            "Google Gemini (Recommended — Free)",
            "Groq — Llama 3 (Free, Fast)",
            "Ollama (Local — No Key Needed)"
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, providers)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        providerSpinner.adapter = adapter

        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                updateUiForProvider(pos)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun updateUiForProvider(providerIndex: Int) {
        when (providerIndex) {
            0 -> { // Gemini
                apiKeySection.visibility = View.VISIBLE
                ollamaSection.visibility = View.GONE
                getKeyButton.text = "Get Free Gemini Key →"
                apiKeyInput.hint = "Paste your Gemini API key here"
            }
            1 -> { // Groq
                apiKeySection.visibility = View.VISIBLE
                ollamaSection.visibility = View.GONE
                getKeyButton.text = "Get Free Groq Key →"
                apiKeyInput.hint = "Paste your Groq API key here"
            }
            2 -> { // Ollama
                apiKeySection.visibility = View.GONE
                ollamaSection.visibility = View.VISIBLE
            }
        }
    }

    private fun setupListeners() {
        validateButton.setOnClickListener { validateCurrentKey() }
        saveButton.setOnClickListener { saveSettings() }

        getKeyButton.setOnClickListener {
            val url = when (providerSpinner.selectedItemPosition) {
                0 -> "https://aistudio.google.com"
                1 -> "https://console.groq.com"
                else -> "https://ollama.com"
            }
            // Open in browser (user will use their phone/another device)
            statusText.text = "Visit: $url"
        }
    }

    private fun validateCurrentKey() {
        statusText.text = "Validating..."
        validateButton.isEnabled = false

        lifecycleScope.launch {
            val isValid = try {
                val provider = buildCurrentProvider()
                provider?.validateKey() ?: false
            } catch (e: Exception) {
                false
            }

            if (isValid) {
                statusText.text = "✓ Connection successful!"
            } else {
                statusText.text = "✗ Could not connect. Check your key and try again."
            }
            validateButton.isEnabled = true
        }
    }

    private fun saveSettings() {
        val providerKey = when (providerSpinner.selectedItemPosition) {
            0 -> PROVIDER_GEMINI
            1 -> PROVIDER_GROQ
            else -> PROVIDER_OLLAMA
        }

        prefs.edit()
            .putString(PREF_PROVIDER, providerKey)
            .putString(PREF_API_KEY, apiKeyInput.text.toString().trim())
            .putString(PREF_OLLAMA_HOST, ollamaHostInput.text.toString().trim())
            .apply()

        statusText.text = "✓ Settings saved!"

        // Navigate back to home after a short delay
        view?.postDelayed({
            parentFragmentManager.popBackStack()
        }, 1000)
    }

    private fun loadSavedSettings() {
        val provider = prefs.getString(PREF_PROVIDER, PROVIDER_GEMINI)
        val apiKey = prefs.getString(PREF_API_KEY, "") ?: ""
        val ollamaHost = prefs.getString(PREF_OLLAMA_HOST, "http://192.168.1.x:11434") ?: ""

        providerSpinner.setSelection(
            when (provider) {
                PROVIDER_GEMINI -> 0
                PROVIDER_GROQ -> 1
                PROVIDER_OLLAMA -> 2
                else -> 0
            }
        )
        apiKeyInput.setText(apiKey)
        ollamaHostInput.setText(ollamaHost)
    }

    private fun buildCurrentProvider() = when (providerSpinner.selectedItemPosition) {
        0 -> GeminiProvider(apiKeyInput.text.toString().trim())
        1 -> GroqProvider(apiKeyInput.text.toString().trim())
        2 -> OllamaProvider(host = ollamaHostInput.text.toString().trim())
        else -> null
    }
}
