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
import com.beam.ai.AIRouter
import com.beam.ai.AITask
import com.beam.ai.TokenTracker
import com.beam.model.ContentItem
import com.beam.model.StreamType
import com.beam.scraper.HtmlFetcher
import com.beam.scraper.StreamExtractor
import com.beam.scraper.WebViewStreamExtractor
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = arguments?.getString(ARG_TITLE) ?: ""
        val detailUrl = arguments?.getString(ARG_DETAIL_URL) ?: ""
        val thumbnailUrl = arguments?.getString(ARG_THUMBNAIL) ?: ""
        val description = arguments?.getString(ARG_DESCRIPTION) ?: ""

        view.findViewById<TextView>(R.id.titleText).text = title
        view.findViewById<TextView>(R.id.descriptionText).text = description

        if (thumbnailUrl.isNotBlank()) {
            Glide.with(this).load(thumbnailUrl).into(view.findViewById(R.id.thumbnailImage))
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
            val tokenTracker = TokenTracker(requireContext())
            val router = AIRouter(requireContext(), tokenTracker)

            // Get best provider for stream extraction (light task — use secondary)
            val aiProvider = router.getProvider(AITask.STREAM_EXTRACT)

            // Step 1: Try static HTML extraction (fast)
            activity?.runOnUiThread { statusText.text = "Searching for stream..." }

            var stream = if (aiProvider != null) {
                StreamExtractor(HtmlFetcher(), aiProvider).extract(detailUrl)
            } else {
                com.beam.model.StreamResult("", StreamType.UNKNOWN)
            }

            // Step 2: WebView fallback for JS-heavy sites
            if (stream.url.isBlank()) {
                activity?.runOnUiThread {
                    statusText.text = "Loading video player... (up to 30s)"
                }
                stream = WebViewStreamExtractor(requireContext()).extract(detailUrl)
            }

            activity?.runOnUiThread {
                if (stream.url.isNotBlank()) {
                    statusText.text = ""
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, PlaybackFragment.newInstance(stream.url, stream.type, title))
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
