package com.beam.phone

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Phone companion app for Beam.
 *
 * Two entry points:
 * 1. Launched normally → user types/pastes a URL manually
 * 2. Launched via Share → auto-fills the URL from the shared link
 *
 * The URL is then sent to the TV app over the local network.
 * (Phase 1: copy to clipboard. Phase 2: send via local network discovery)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var sendButton: Button
    private lateinit var statusText: TextView
    private lateinit var recentLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        sendButton = findViewById(R.id.sendButton)
        statusText = findViewById(R.id.statusText)

        // Check if we were launched via Share
        handleShareIntent(intent)

        sendButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isBlank()) {
                statusText.text = "Please enter a URL first"
                return@setOnClickListener
            }
            sendUrlToTv(url)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /**
     * When the user shares a URL from their browser, it lands here.
     * We auto-fill the URL input so they just hit Send.
     */
    private fun handleShareIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND &&
            intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            // Extract URL from shared text (browsers sometimes include title + URL)
            val url = extractUrl(sharedText)
            if (url != null) {
                urlInput.setText(url)
                statusText.text = "URL ready — hit Send to Beam it to your TV"
            }
        }
    }

    /**
     * Sends the URL to the TV.
     * Phase 1: copies to clipboard (user manually pastes on TV if needed)
     * Phase 2 (coming soon): sends directly via local network
     */
    private fun sendUrlToTv(url: String) {
        // Copy to clipboard as Phase 1 fallback
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Beam URL", url)
        clipboard.setPrimaryClip(clip)

        statusText.text = "✓ URL copied! Open Beam on your TV and paste it."
        Toast.makeText(this, "Beamed to clipboard!", Toast.LENGTH_SHORT).show()

        // TODO Phase 2: Send directly to TV via local network
        // The TV app will run a small local HTTP server
        // Phone will POST the URL to http://<tv-ip>:8765/beam
    }

    private fun extractUrl(text: String): String? {
        val urlRegex = Regex("""https?://[^\s]+""")
        return urlRegex.find(text)?.value
    }
}
