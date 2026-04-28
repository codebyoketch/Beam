package com.beam.ui

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.beam.R
import com.beam.server.BeamDiscovery
import com.beam.server.BeamServer

class MainActivity : FragmentActivity() {

    private lateinit var beamServer: BeamServer
    private lateinit var beamDiscovery: BeamDiscovery

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Start local server so phone can send URLs and keys
        beamServer = BeamServer(
            onUrlReceived = { url ->
                runOnUiThread {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, BrowseFragment.newInstance(url))
                        .addToBackStack(null)
                        .commit()
                }
            },
            onKeyReceived = { provider, key ->
                runOnUiThread { saveApiKey(provider, key) }
            }
        )
        beamServer.start()

        // Announce this TV on the local network so phone can discover it
        beamDiscovery = BeamDiscovery(this)
        beamDiscovery.register()

        if (savedInstanceState == null) {
            val startFragment = if (hasApiKey()) HomeFragment() else SettingsFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, startFragment)
                .commit()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        beamServer.stop()
        beamDiscovery.unregister()
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }

    private fun hasApiKey(): Boolean {
        return try {
            getSecurePrefs().contains(SettingsFragment.PREF_API_KEY)
        } catch (e: Exception) { false }
    }

    private fun saveApiKey(provider: String, key: String) {
        try {
            val providerKey = when (provider.lowercase()) {
                "groq" -> SettingsFragment.PROVIDER_GROQ
                "ollama" -> SettingsFragment.PROVIDER_OLLAMA
                else -> SettingsFragment.PROVIDER_GEMINI
            }
            getSecurePrefs().edit()
                .putString(SettingsFragment.PREF_PROVIDER, providerKey)
                .putString(SettingsFragment.PREF_API_KEY, key)
                .apply()
            Log.i("MainActivity", "API key saved for: $providerKey")
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to save API key", e)
        }
    }

    private fun getSecurePrefs(): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            this, "beam_secure_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
