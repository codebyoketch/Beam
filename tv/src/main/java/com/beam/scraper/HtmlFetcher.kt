package com.beam.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches raw HTML from a URL using OkHttp.
 *
 * Limitation: This only works for server-rendered HTML.
 * Sites that load content via JavaScript will return empty content.
 * A WebView-based fallback is planned for a future version.
 */
class HtmlFetcher {

    companion object {
        private const val TAG = "HtmlFetcher"

        // Mimic a real browser to avoid bot detection
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; SHIELD TV) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Fetches the HTML content of a URL.
     * Returns null if the request fails or the content is not HTML.
     */
    suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,/;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP ${response.code} for $url")
                return@withContext null
            }

            val contentType = response.header("Content-Type", "")
            if (contentType?.contains("text/html") == false &&
                contentType?.contains("application/xhtml") == false) {
                Log.w(TAG, "Non-HTML content type: $contentType for $url")
            }

            val content = response.body?.string()
            Log.d(TAG, "Fetched ${content?.length ?: 0} chars from $url")
            return@withContext content

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch $url", e)
            null
        }
    }
}
