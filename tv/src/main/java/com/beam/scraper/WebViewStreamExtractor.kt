package com.beam.scraper

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.beam.model.StreamResult
import com.beam.model.StreamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * WebViewStreamExtractor loads a page in a hidden WebView and intercepts
 * all network requests to find video stream URLs.
 *
 * This works for JavaScript-heavy sites like Goojara where the video
 * player is loaded dynamically and can't be found in static HTML.
 *
 * Must be called from the main thread since WebView requires it.
 */
class WebViewStreamExtractor(private val context: Context) {

    companion object {
        private const val TAG = "WebViewStreamExtractor"
        private const val TIMEOUT_MS = 30_000L // 30 seconds max wait

        // URL patterns that indicate a video stream
        private val STREAM_PATTERNS = listOf(
            ".m3u8", ".mp4", ".mpd", ".ts",
            "playlist", "manifest", "stream",
            "video/mp4", "video/webm", "application/x-mpegURL"
        )

        // Domains to ignore — not video streams
        private val IGNORE_DOMAINS = setOf(
            "google-analytics.com", "googletagmanager.com",
            "facebook.com", "twitter.com", "doubleclick.net",
            "googlesyndication.com", "adservice.google.com",
            "fonts.googleapis.com", "fonts.gstatic.com"
        )
    }

    /**
     * Loads the given URL in a hidden WebView and waits for a stream URL
     * to be intercepted from network requests.
     *
     * Returns StreamResult with the found URL, or empty if nothing found.
     * Must be called from the main (UI) thread.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extract(url: String): StreamResult = withContext(Dispatchers.Main) {
        Log.d(TAG, "Starting WebView extraction for: $url")

        val result = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val webView = WebView(context)

                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = "Mozilla/5.0 (Linux; Android 13; SHIELD TV) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 Mobile Safari/537.36"
                }

                webView.webViewClient = object : WebViewClient() {

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        val requestUrl = request.url.toString()

                        // Skip ignored domains
                        if (IGNORE_DOMAINS.any { requestUrl.contains(it) }) {
                            return null
                        }

                        // Check if this looks like a video stream
                        val lowerUrl = requestUrl.lowercase()
                        val isStream = STREAM_PATTERNS.any { pattern ->
                            lowerUrl.contains(pattern)
                        }

                        if (isStream) {
                            Log.d(TAG, "Found stream URL: $requestUrl")

                            val streamType = when {
                                lowerUrl.contains(".m3u8") -> StreamType.HLS
                                lowerUrl.contains(".mpd") -> StreamType.DASH
                                lowerUrl.contains(".mp4") -> StreamType.MP4
                                else -> StreamType.HLS
                            }

                            // Resume the coroutine with the found stream
                            if (continuation.isActive) {
                                continuation.resume(StreamResult(requestUrl, streamType))
                                // Clean up WebView
                                view.post {
                                    view.stopLoading()
                                    view.destroy()
                                }
                            }
                        }

                        return null
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        Log.d(TAG, "Page finished loading: $url")
                        // Page loaded but no stream found yet — keep waiting
                    }
                }

                continuation.invokeOnCancellation {
                    webView.stopLoading()
                    webView.destroy()
                }

                webView.loadUrl(url)
            }
        }

        if (result == null) {
            Log.d(TAG, "WebView extraction timed out for: $url")
            StreamResult("", StreamType.UNKNOWN)
        } else {
            Log.d(TAG, "WebView extraction success: ${result.url}")
            result
        }
    }
}
