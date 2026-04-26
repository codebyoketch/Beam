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
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

/**
 * Detail screen showing info about a selected video.
 * Has a Play button that extracts the stream and launches playback.
 */
class DetailFragment : Fragment() {

    companion object {
        private const val ARG_ITEM = "content_item_title"
        private const val ARG_DETAIL_URL = "detail_url"
        private const val ARG_THUMBNAIL = "thumbnail_url"
        private const val ARG_DESCRIPTION = "description"

        fun newInstance(item: ContentItem): DetailFragment {
            return DetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ITEM, item.title)
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

        val title = arguments?.getString(ARG_ITEM) ?: ""
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

                val extractor = StreamExtractor(HtmlFetcher(), aiProvider)
                val stream = extractor.extract(detailUrl)

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
}
