package com.beam.ui

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.beam.R
import com.beam.ai.GeminiProvider
import com.beam.ai.GroqProvider
import com.beam.ai.OllamaProvider
import com.beam.ai.PageAnalyzer
import com.beam.model.ContentItem
import com.beam.model.ParsedPage
import com.beam.scraper.HtmlFetcher
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.launch

class BrowseFragment : BrowseSupportFragment() {

    companion object {
        const val ARG_URL = "url"
        private const val TAG = "BrowseFragment"

        fun newInstance(url: String): BrowseFragment {
            return BrowseFragment().apply {
                arguments = Bundle().apply { putString(ARG_URL, url) }
            }
        }
    }

    private val targetUrl by lazy { arguments?.getString(ARG_URL) ?: "" }
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")
        setupBrowseFragment()
        loadContent()
    }

    private fun setupBrowseFragment() {
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        adapter = rowsAdapter

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is ContentItem) {
                openDetail(item)
            }
        }
    }

    private fun loadContent() {
        Log.d(TAG, "loadContent called for: $targetUrl")
        title = "Loading..."

        if (targetUrl.isBlank()) {
            Log.e(TAG, "targetUrl is blank!")
            title = "Error — no URL provided"
            return
        }

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Reading secure prefs...")
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

                Log.d(TAG, "Provider: $providerKey, hasKey: ${apiKey.isNotBlank()}")

                if (apiKey.isBlank() && providerKey != SettingsFragment.PROVIDER_OLLAMA) {
                    activity?.runOnUiThread {
                        title = "No API key set — go to Settings"
                    }
                    return@launch
                }

                val aiProvider = when (providerKey) {
                    SettingsFragment.PROVIDER_GROQ -> GroqProvider(apiKey)
                    SettingsFragment.PROVIDER_OLLAMA -> OllamaProvider(host = ollamaHost)
                    else -> GeminiProvider(apiKey)
                }

                Log.d(TAG, "Starting analysis with provider: ${aiProvider.name}")
                val analyzer = PageAnalyzer(aiProvider, HtmlFetcher())
                val result = analyzer.analyze(targetUrl) { progress ->
                    Log.d(TAG, "Progress: $progress")
                    activity?.runOnUiThread { title = progress }
                }

                result.onSuccess { page ->
                    Log.d(TAG, "Analysis success: ${page.siteName}, rows: ${page.rows.size}")
                    renderPage(page)
                }
                result.onFailure { error ->
                    Log.e(TAG, "Analysis failed", error)
                    activity?.runOnUiThread {
                        title = "Failed to load — ${error.message}"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in loadContent", e)
                activity?.runOnUiThread {
                    title = "Error — ${e.message}"
                }
            }
        }
    }

    private fun renderPage(page: ParsedPage) {
        activity?.runOnUiThread {
            title = page.siteName
            rowsAdapter.clear()

            val presenter = ContentCardPresenter()

            page.rows.forEach { row ->
                val itemsAdapter = ArrayObjectAdapter(presenter)
                row.items.forEach { item -> itemsAdapter.add(item) }
                val header = HeaderItem(row.title)
                rowsAdapter.add(ListRow(header, itemsAdapter))
            }

            if (page.rows.isEmpty()) {
                title = "${page.siteName} — No content found"
            }
        }
    }

    private fun openDetail(item: ContentItem) {
        val fragment = DetailFragment.newInstance(item)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}

class ContentCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
        val cardView = androidx.leanback.widget.ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(320, 180)
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val card = viewHolder.view as androidx.leanback.widget.ImageCardView
        val contentItem = item as ContentItem

        card.titleText = contentItem.title
        card.contentText = contentItem.description

        if (contentItem.thumbnailUrl.isNotBlank()) {
            Glide.with(card.context)
                .load(contentItem.thumbnailUrl)
                .centerCrop()
                .into(object : SimpleTarget<Drawable>() {
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        card.mainImage = resource
                    }
                })
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as androidx.leanback.widget.ImageCardView
        card.mainImage = null
    }
}
