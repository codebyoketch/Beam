package com.beam.scraper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.WindowManager
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
 * WebViewStreamExtractor loads a page in a hidden WebView attached to the
 * window manager, intercepts all network requests, and finds video stream URLs.
 *
 * WebView must be attached to a window to properly execute JavaScript and
 * intercept network requests.
 */
class WebViewStreamExtractor(private val context: Context) {

    companion object {
        private const val TAG = "WebViewStreamExtractor"
        private const val TIMEOUT_MS = 30_000L

        private val STREAM_EXTENSIONS = listOf(".m3u8", ".mp4", ".mpd", ".webm")
        private val STREAM_KEYWORDS = listOf(
            "playlist.m3u8", "index.m3u8", "master.m3u8",
            "video/mp4", "video/webm", "application/x-mpegurl",
            "stream.php", "getlink", "player.php"
        )
        private val IGNORE_DOMAINS = setOf(
            "google-analytics.com", "googletagmanager.com", "doubleclick.net",
            "facebook.com", "twitter.com", "googlesyndication.com",
            "fonts.googleapis.com", "fonts.gstatic.com", "amazon-adsystem.com"
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extract(url: String): StreamResult = withContext(Dispatchers.Main) {
        Log.d(TAG, "WebView extraction starting for: $url")

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val result = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->

                val params = WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                )

                val webView = WebView(context)
                var isAdded = false

                fun cleanup() {
                    try {
                        webView.stopLoading()
                        if (isAdded) windowManager.removeView(webView)
                        webView.destroy()
                    } catch (e: Exception) {
                        Log.e(TAG, "Cleanup error", e)
                    }
                }

                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    userAgentString = "Mozilla/5.0 (Linux; Android 13; SHIELD TV) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 Mobile Safari/537.36"
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        val reqUrl = request.url.toString()
                        val lowerUrl = reqUrl.lowercase()

                        if (IGNORE_DOMAINS.any { reqUrl.contains(it) }) return null

                        val isStream = STREAM_EXTENSIONS.any { lowerUrl.contains(it) } ||
                                STREAM_KEYWORDS.any { lowerUrl.contains(it) }

                        if (isStream) {
                            Log.d(TAG, "Intercepted stream: $reqUrl")
                            val type = when {
                                lowerUrl.contains(".m3u8") -> StreamType.HLS
                                lowerUrl.contains(".mpd") -> StreamType.DASH
                                lowerUrl.contains(".mp4") -> StreamType.MP4
                                else -> StreamType.HLS
                            }
                            if (continuation.isActive) {
                                continuation.resume(StreamResult(reqUrl, type))
                                view.post { cleanup() }
                            }
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        Log.d(TAG, "Page loaded: $url")
                    }
                }

                continuation.invokeOnCancellation { cleanup() }

                try {
                    windowManager.addView(webView, params)
                    isAdded = true
                    webView.loadUrl(url)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add WebView to window", e)
                    if (continuation.isActive) {
                        continuation.resume(StreamResult("", StreamType.UNKNOWN))
                    }
                }
            }
        }

        result ?: StreamResult("", StreamType.UNKNOWN).also {
            Log.d(TAG, "WebView timed out for: $url")
        }
    }
}
