package com.beam.ui

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.beam.R
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
        const val ARG_FORCE_REFRESH = "force_refresh"
        private const val TAG = "BrowseFragment"

        fun newInstance(url: String, forceRefresh: Boolean = false): BrowseFragment {
            return BrowseFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                    putBoolean(ARG_FORCE_REFRESH, forceRefresh)
                }
            }
        }
    }

    private val targetUrl by lazy { arguments?.getString(ARG_URL) ?: "" }
    private val forceRefresh by lazy { arguments?.getBoolean(ARG_FORCE_REFRESH, false) ?: false }
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated for: $targetUrl")
        setupBrowseFragment()
        loadContent()
    }

    private fun setupBrowseFragment() {
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        adapter = rowsAdapter
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is ContentItem) openDetail(item)
        }
    }

    private fun loadContent() {
        Log.d(TAG, "loadContent called for: $targetUrl")
        title = "Loading..."
        if (targetUrl.isBlank()) { title = "Error — no URL provided"; return }

        lifecycleScope.launch {
            try {
                val analyzer = PageAnalyzer(HtmlFetcher(), requireContext())
                val result = analyzer.analyze(url = targetUrl, forceRefresh = forceRefresh) { progress ->
                    Log.d(TAG, "Progress: $progress")
                    activity?.runOnUiThread { title = progress }
                }
                result.onSuccess { page ->
                    Log.d(TAG, "Success: ${page.siteName}, rows: ${page.rows.size}")
                    renderPage(page)
                }
                result.onFailure { error ->
                    Log.e(TAG, "Failed", error)
                    activity?.runOnUiThread { title = "Failed — ${error.message}" }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                activity?.runOnUiThread { title = "Error — ${e.message}" }
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
                row.items.forEach { itemsAdapter.add(it) }
                rowsAdapter.add(ListRow(HeaderItem(row.title), itemsAdapter))
            }
            if (page.rows.isEmpty()) title = "${page.siteName} — No content found"
        }
    }

    private fun openDetail(item: ContentItem) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DetailFragment.newInstance(item))
            .addToBackStack(null).commit()
    }
}

class ContentCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
        val cardView = androidx.leanback.widget.ImageCardView(parent.context).apply {
            isFocusable = true; isFocusableInTouchMode = true
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
            Glide.with(card.context).load(contentItem.thumbnailUrl).centerCrop()
                .into(object : SimpleTarget<Drawable>() {
                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                        card.mainImage = resource
                    }
                })
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        (viewHolder.view as androidx.leanback.widget.ImageCardView).mainImage = null
    }
}
