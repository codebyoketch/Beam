package com.beam.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.beam.R
import com.beam.ai.GeminiProvider
import com.beam.ai.GroqProvider
import com.beam.ai.OllamaProvider
import com.beam.model.ContentItem
import com.beam.model.StreamType
import com.beam.scraper.HtmlFetcher
import com.beam.scraper.StreamExtractor
import com.beam.scraper.WebViewStreamExtractor
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

/**
 * Detail screen showing info about a selected video.
 * Has a Play button that extracts the stream and launches playback.
 *
 * Stream extraction strategy:
 * 1. Static HTML extraction (fast)
 * 2. WebView extraction fallback (slower but works on JS sites)
 */
class DetailFragment : Fragment() {

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_DETAIL_URL = "detail_url"
        private const val ARG_THUMBNAIL = "thumbnail_url"
        private const val ARG_DESCRIPTION = "description"

        fun newInstance(item: ContentItem): DetailFragment {
            return DetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, item.title)
                    putString(ARG_DETAIL_URL, item.detailUrl)
                    putString(ARG_THUMBNAIL, item.thumbnailUrl)
                    putString(ARG_DESCRIPTION, item.description)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = arguments?.getString(ARG_TITLE) ?: ""
        val detailUrl = arguments?.getString(ARG_DETAIL_URL) ?: ""
        val thumbnailUrl = arguments?.getString(ARG_THUMBNAIL) ?: ""
        val description = arguments?.getString(ARG_DESCRIPTION) ?: ""

        view.findViewById<TextView>(R.id.titleText).text = title
        view.findViewById<TextView>(R.id.descriptionText).text = description

        val thumbnail = view.findViewById<ImageView>(R.id.thumbnailImage)
        if (thumbnailUrl.isNotBlank()) {
            Glide.with(this).load(thumbnailUrl).into(thumbnail)
        }

        val playButton = view.findViewById<Button>(R.id.playButton)
        val statusText = view.findViewById<TextView>(R.id.statusText)

        playButton.setOnClickListener {
            playButton.isEnabled = false
            statusText.text = "Finding stream..."
            extractAndPlay(detailUrl, title, playButton, statusText)
        }
    }

    private fun extractAndPlay(
        detailUrl: String,
        title: String,
        playButton: Button,
        statusText: TextView
    ) {
        lifecycleScope.launch {
            val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                requireContext(),
                "beam_secure_prefs",
                androidx.security.crypto.MasterKey.Builder(requireContext())
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val providerKey = prefs.getString(SettingsFragment.PREF_PROVIDER, SettingsFragment.PROVIDER_GEMINI)
            val apiKey = prefs.getString(SettingsFragment.PREF_API_KEY, "") ?: ""
            val ollamaHost = prefs.getString(SettingsFragment.PREF_OLLAMA_HOST, "") ?: ""

            val aiProvider = when (providerKey) {
                SettingsFragment.PROVIDER_GROQ -> GroqProvider(apiKey)
                SettingsFragment.PROVIDER_OLLAMA -> OllamaProvider(host = ollamaHost)
                else -> GeminiProvider(apiKey)
            }

            // Step 1: Try static HTML extraction (fast)
            activity?.runOnUiThread { statusText.text = "Searching for stream..." }
            val extractor = StreamExtractor(HtmlFetcher(), aiProvider)
            var stream = extractor.extract(detailUrl)

            // Step 2: If not found, try WebView (slower but works on JS sites)
            if (stream.url.isBlank()) {
                activity?.runOnUiThread {
                    statusText.text = "Loading video player... (this may take up to 30s)"
                }
                val webViewExtractor = WebViewStreamExtractor(requireContext())
                stream = webViewExtractor.extract(detailUrl)
            }

            activity?.runOnUiThread {
                if (stream.url.isNotBlank()) {
                    statusText.text = ""
                    val fragment = PlaybackFragment.newInstance(stream.url, stream.type, title)
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit()
                } else {
                    statusText.text = "Could not find a playable stream on this page."
                    playButton.isEnabled = true
                }
            }
        }
    }
}
