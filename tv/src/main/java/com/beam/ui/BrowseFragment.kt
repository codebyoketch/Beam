package com.beam.ui

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
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
import com.beam.scraper.StreamExtractor
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.launch

/**
 * The main browsing screen.
 * Shows content rows extracted from the target website.
 * Uses the AndroidX Leanback BrowseSupportFragment for native TV UI.
 */
class BrowseFragment : BrowseSupportFragment() {

    companion object {
        const val ARG_URL = "url"

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
        setupBrowseFragment()
        loadContent()
    }

    private fun setupBrowseFragment() {
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        adapter = rowsAdapter

        // Handle item clicks → go to detail / play
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is ContentItem) {
                openDetail(item)
            }
        }
    }

    private fun loadContent() {
        title = "Loading..."

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

            val analyzer = PageAnalyzer(aiProvider, HtmlFetcher())
            val result = analyzer.analyze(targetUrl) { progress ->
                activity?.runOnUiThread { title = progress }
            }

            result.onSuccess { page -> renderPage(page) }
            result.onFailure { error ->
                activity?.runOnUiThread {
                    title = "Failed to load"
                    // TODO: Show error card in the browse view
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

/**
 * Presents a ContentItem as a TV image card with title.
 */
class ContentCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
        val cardView = androidx.leanback.widget.ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(320, 180)
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val card = viewHolder.view as androidx.leanback.widget.ImageCardView
        val contentItem = item as ContentItem

        card.titleText = contentItem.title
        card.contentText = contentItem.description

        if (contentItem.thumbnailUrl.isNotBlank()) {
            Glide.with(card.context)
                .load(contentItem.thumbnailUrl)
                .centerCrop()
                .into(object : SimpleTarget<android.graphics.drawable.Drawable>() {
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
