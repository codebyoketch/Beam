package com.beam.scraper

import android.util.Log
import com.beam.ai.AIProvider
import com.beam.ai.PromptBuilder
import com.beam.model.StreamResult
import com.beam.model.StreamType
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * StreamExtractor finds the actual playable video URL from a detail page.
 *
 * Strategy (in order):
 * 1. Regex scan for known video URL patterns (.m3u8, .mp4, .mpd)
 * 2. Check <video> tags for src attributes
 * 3. Look for common JS player variable patterns
 * 4. Check for iframe embeds pointing to known video hosts
 * 5. Fall back to AI analysis if nothing found
 */
class StreamExtractor(
    private val htmlFetcher: HtmlFetcher,
    private val aiProvider: AIProvider
) {

    companion object {
        private const val TAG = "StreamExtractor"

        // Regex patterns for direct stream URLs
        private val HLS_PATTERN = Regex("""https?://[^\s"'\\]+\.m3u8[^\s"'\\]*""")
        private val MP4_PATTERN = Regex("""https?://[^\s"'\\]+\.mp4[^\s"'\\]*""")
        private val DASH_PATTERN = Regex("""https?://[^\s"'\\]+\.mpd[^\s"'\\]*""")

        // JS variable patterns that often contain stream URLs
        private val JS_FILE_PATTERN = Regex(""""file"\s*:\s*"(https?://[^"]+)"""")
        private val JS_SRC_PATTERN = Regex(""""src"\s*:\s*"(https?://[^"]+)"""")
        private val JS_SOURCE_PATTERN = Regex(""""source"\s*:\s*"(https?://[^"]+)"""")

        // Known video hosting domains (for iframe detection)
        private val VIDEO_HOSTS = setOf(
            "youtube.com", "youtu.be", "vimeo.com", "dailymotion.com",
            "streamtape.com", "doodstream.com", "mixdrop.co", "upstream.to",
            "filemoon.sx", "vidplay.online", "voe.sx", "2embed.cc",
            "embedsito.com", "moviesapi.club", "multiembed.mov",
            "smashystream.com", "autoembed.co", "embed.su",
            "goojara.to", "ww1.goojara.to", "ww2.goojara.to",
            "streamwish.com", "filelions.com", "vtube.to",
            "febbox.com", "vidsrc.to", "vidsrc.me"
        )
    }

    /**
     * Extracts a playable stream URL from a detail page URL.
     */
    suspend fun extract(detailUrl: String): StreamResult {
        Log.d(TAG, "Extracting stream from: $detailUrl")

        val html = htmlFetcher.fetch(detailUrl)
        if (html == null) {
            Log.e(TAG, "Failed to fetch detail page: $detailUrl")
            return StreamResult("", StreamType.UNKNOWN)
        }

        Log.d(TAG, "Detail page HTML length: ${html.length} chars")

        // Try each strategy in order
        findHlsStream(html)?.let {
            Log.d(TAG, "Found HLS stream: $it")
            return StreamResult(it, StreamType.HLS)
        }

        findMp4Stream(html)?.let {
            Log.d(TAG, "Found MP4 stream: $it")
            return StreamResult(it, StreamType.MP4)
        }

        findDashStream(html)?.let {
            Log.d(TAG, "Found DASH stream: $it")
            return StreamResult(it, StreamType.DASH)
        }

        findVideoTag(html)?.let {
            Log.d(TAG, "Found video tag src: $it")
            return StreamResult(it, StreamType.MP4)
        }

        findJsFileUrl(html)?.let {
            Log.d(TAG, "Found JS file URL: $it")
            return StreamResult(it, StreamType.MP4)
        }

        findIframe(html)?.let {
            Log.d(TAG, "Found iframe embed: $it")
            // If iframe points to another page, try to extract from there too
            if (it.startsWith("http") && !VIDEO_HOSTS.any { host -> it.contains(host) && it.endsWith(host) }) {
                Log.d(TAG, "Following iframe to: $it")
                val iframeHtml = htmlFetcher.fetch(it)
                if (iframeHtml != null) {
                    findHlsStream(iframeHtml)?.let { s -> return StreamResult(s, StreamType.HLS) }
                    findMp4Stream(iframeHtml)?.let { s -> return StreamResult(s, StreamType.MP4) }
                    findJsFileUrl(iframeHtml)?.let { s -> return StreamResult(s, StreamType.MP4) }
                }
            }
            return StreamResult(it, StreamType.IFRAME)
        }

        Log.d(TAG, "No stream found with regex — trying AI extraction")
        return aiExtract(detailUrl, html)
    }

    private fun findHlsStream(html: String): String? =
        HLS_PATTERN.find(html)?.value?.cleanUrl()

    private fun findMp4Stream(html: String): String? =
        MP4_PATTERN.find(html)?.value?.cleanUrl()

    private fun findDashStream(html: String): String? =
        DASH_PATTERN.find(html)?.value?.cleanUrl()

    private fun findVideoTag(html: String): String? {
        val doc = Jsoup.parse(html)
        return doc.select("video source[src], video[src]")
            .firstOrNull()
            ?.attr("src")
            ?.takeIf { it.isNotBlank() }
    }

    private fun findJsFileUrl(html: String): String? {
        JS_FILE_PATTERN.find(html)?.groupValues?.get(1)?.let { return it.cleanUrl() }
        JS_SRC_PATTERN.find(html)?.groupValues?.get(1)?.let { return it.cleanUrl() }
        JS_SOURCE_PATTERN.find(html)?.groupValues?.get(1)?.let { return it.cleanUrl() }
        return null
    }

    private fun findIframe(html: String): String? {
        val doc = Jsoup.parse(html)
        val iframes = doc.select("iframe[src]")
        Log.d(TAG, "Found ${iframes.size} iframes on page")

        iframes.forEach { iframe ->
            val src = iframe.absUrl("src").ifBlank { iframe.attr("src") }
            Log.d(TAG, "iframe src: $src")
        }

        return iframes
            .map { it.absUrl("src").ifBlank { it.attr("src") } }
            .firstOrNull { src ->
                src.isNotBlank() && VIDEO_HOSTS.any { host -> src.contains(host) }
            } ?: iframes
            .map { it.absUrl("src").ifBlank { it.attr("src") } }
            .firstOrNull { src -> src.isNotBlank() && src.startsWith("http") }
    }

    private suspend fun aiExtract(url: String, html: String): StreamResult {
        return try {
            Log.d(TAG, "Sending to AI for stream extraction")
            val prompt = PromptBuilder.buildStreamExtractionPrompt(url, html)
            val response = aiProvider.complete(prompt)
            val cleaned = response
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            Log.d(TAG, "AI stream response: $cleaned")

            val json = JSONObject(cleaned)
            val streamUrl = json.optString("streamUrl", "")
            val typeStr = json.optString("type", "unknown")

            Log.d(TAG, "AI found stream: $streamUrl (type: $typeStr)")

            val type = when (typeStr.lowercase()) {
                "hls" -> StreamType.HLS
                "mp4" -> StreamType.MP4
                "dash" -> StreamType.DASH
                "iframe" -> StreamType.IFRAME
                else -> StreamType.UNKNOWN
            }

            StreamResult(streamUrl, type)
        } catch (e: Exception) {
            Log.e(TAG, "AI stream extraction failed for $url", e)
            StreamResult("", StreamType.UNKNOWN)
        }
    }

    // Remove common junk appended after the URL
    private fun String.cleanUrl(): String =
        this.trimEnd('"', '\'', '\\', ',', ';', ')')
}
