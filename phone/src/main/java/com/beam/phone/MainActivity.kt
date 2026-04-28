package com.beam.phone

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREF_TV_IP = "tv_ip"
        const val PREF_TV_NAME = "tv_name"
        const val DEFAULT_PORT = 8765
    }

    private lateinit var scanButton: Button
    private lateinit var deviceListLayout: LinearLayout
    private lateinit var selectedTvText: TextView
    private lateinit var urlInput: EditText
    private lateinit var sendUrlButton: Button
    private lateinit var apiKeyInput: EditText
    private lateinit var providerSpinner: Spinner
    private lateinit var sendKeyButton: Button
    private lateinit var statusText: TextView
    private lateinit var noDevicesText: TextView

    private val prefs by lazy { getSharedPreferences("beam_phone_prefs", MODE_PRIVATE) }
    private val discoveredDevices = mutableListOf<BeamDevice>()
    private var selectedDevice: BeamDevice? = null
    private var scanner: BeamScanner? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scanButton = findViewById(R.id.scanButton)
        deviceListLayout = findViewById(R.id.deviceListLayout)
        selectedTvText = findViewById(R.id.selectedTvText)
        urlInput = findViewById(R.id.urlInput)
        sendUrlButton = findViewById(R.id.sendUrlButton)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        providerSpinner = findViewById(R.id.providerSpinner)
        sendKeyButton = findViewById(R.id.sendKeyButton)
        statusText = findViewById(R.id.statusText)
        noDevicesText = findViewById(R.id.noDevicesText)

        // Restore last selected device
        val savedIp = prefs.getString(PREF_TV_IP, null)
        val savedName = prefs.getString(PREF_TV_NAME, null)
        if (savedIp != null && savedName != null) {
            selectedDevice = BeamDevice(savedName, savedIp, DEFAULT_PORT)
            selectedTvText.text = "📺 $savedName"
        }

        val providers = listOf("Google Gemini", "Groq", "Ollama")
        providerSpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item, providers).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        handleShareIntent(intent)

        scanButton.setOnClickListener { startScan() }
        sendUrlButton.setOnClickListener { sendUrl() }
        sendKeyButton.setOnClickListener { sendApiKey() }

        // Auto scan on launch
        startScan()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner?.stopScanning()
    }

    private fun startScan() {
        scanner?.stopScanning()
        discoveredDevices.clear()
        deviceListLayout.removeAllViews()
        noDevicesText.visibility = View.VISIBLE
        scanButton.text = "Scanning..."
        scanButton.isEnabled = false
        statusText.text = "Looking for Beam TVs on your network..."

        scanner = BeamScanner(
            context = this,
            onDeviceFound = { device ->
                runOnUiThread {
                    if (discoveredDevices.none { it.ip == device.ip }) {
                        discoveredDevices.add(device)
                        addDeviceCard(device)
                        noDevicesText.visibility = View.GONE
                        statusText.text = "Found ${discoveredDevices.size} TV(s)"
                    }
                }
            },
            onDeviceLost = { name ->
                runOnUiThread {
                    discoveredDevices.removeAll { it.name == name }
                    rebuildDeviceList()
                    if (discoveredDevices.isEmpty()) noDevicesText.visibility = View.VISIBLE
                }
            }
        )
        scanner?.startScanning()

        // Re-enable scan button after 10 seconds
        scanButton.postDelayed({
            scanButton.text = "Scan Again"
            scanButton.isEnabled = true
            if (discoveredDevices.isEmpty()) {
                statusText.text = "No Beam TVs found. Make sure Beam is open on your TV."
                noDevicesText.visibility = View.VISIBLE
            }
        }, 10_000)
    }

    private fun addDeviceCard(device: BeamDevice) {
        val button = Button(this).apply {
            text = "📺 ${device.name}  (${device.ip})"
            textSize = 14f
            setTextColor(0xFF000000.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (selectedDevice?.ip == device.ip) 0xFF4CAF50.toInt() else 0xFFFFFFFF.toInt()
            )
            setPadding(32, 24, 32, 24)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 8)
            layoutParams = params
            setOnClickListener { selectDevice(device) }
        }
        deviceListLayout.addView(button)
    }

    private fun rebuildDeviceList() {
        deviceListLayout.removeAllViews()
        discoveredDevices.forEach { addDeviceCard(it) }
    }

    private fun selectDevice(device: BeamDevice) {
        selectedDevice = device
        prefs.edit()
            .putString(PREF_TV_IP, device.ip)
            .putString(PREF_TV_NAME, device.name)
            .apply()
        selectedTvText.text = "📺 ${device.name}"
        statusText.text = "✓ Connected to ${device.name}"
        rebuildDeviceList()
    }

    private fun handleShareIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val url = Regex("""https?://[^\s]+""").find(sharedText)?.value ?: sharedText.trim()
            urlInput.setText(url)
            statusText.text = "URL ready — tap Send to TV"
        }
    }

    private fun sendUrl() {
        val device = selectedDevice ?: run {
            statusText.text = "No TV selected — tap Scan and pick your TV first"
            return
        }
        val url = urlInput.text.toString().trim()
        if (url.isBlank()) { statusText.text = "Please enter a URL"; return }
        val normalized = if (url.startsWith("http")) url else "https://$url"
        statusText.text = "Sending to ${device.name}..."
        lifecycleScope.launch {
            val ok = doPost(device, "/beam", JSONObject().put("url", normalized).toString())
            statusText.text = if (ok) "✓ Beamed! Check your TV." else "✗ Could not reach ${device.name}. Is Beam open?"
        }
    }

    private fun sendApiKey() {
        val device = selectedDevice ?: run {
            statusText.text = "No TV selected — tap Scan and pick your TV first"
            return
        }
        val key = apiKeyInput.text.toString().trim()
        if (key.isBlank()) { statusText.text = "Please enter an API key"; return }
        val provider = when (providerSpinner.selectedItemPosition) {
            1 -> "groq"; 2 -> "ollama"; else -> "gemini"
        }
        statusText.text = "Sending API key to ${device.name}..."
        lifecycleScope.launch {
            val body = JSONObject().put("provider", provider).put("key", key).toString()
            val ok = doPost(device, "/key", body)
            statusText.text = if (ok) "✓ API key saved on TV! You're ready to Beam." else "✗ Could not reach ${device.name}."
        }
    }

    private suspend fun doPost(device: BeamDevice, path: String, body: String) = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("http://${device.ip}:${device.port}$path")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }
}
