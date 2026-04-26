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

        // Known video hosting domains (for iframe detection)
        private val VIDEO_HOSTS = setOf(
            "youtube.com", "youtu.be", "vimeo.com", "dailymotion.com",
            "streamtape.com", "doodstream.com", "mixdrop.co", "upstream.to",
            "filemoon.sx", "vidplay.online", "voe.sx"
        )
    }

    /**
     * Extracts a playable stream URL from a detail page URL.
     */
    suspend fun extract(detailUrl: String): StreamResult {
        val html = htmlFetcher.fetch(detailUrl)
            ?: return StreamResult("", StreamType.UNKNOWN)

        // Try each strategy in order
        findHlsStream(html)?.let { return StreamResult(it, StreamType.HLS) }
        findMp4Stream(html)?.let { return StreamResult(it, StreamType.MP4) }
        findDashStream(html)?.let { return StreamResult(it, StreamType.DASH) }
        findVideoTag(html)?.let { return StreamResult(it, StreamType.MP4) }
        findIframe(html)?.let { return StreamResult(it, StreamType.IFRAME) }

        // Last resort: ask AI
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

    private fun findIframe(html: String): String? {
        val doc = Jsoup.parse(html)
        return doc.select("iframe[src]")
            .map { it.attr("src") }
            .firstOrNull { src ->
                VIDEO_HOSTS.any { host -> src.contains(host) }
            }
    }

    private suspend fun aiExtract(url: String, html: String): StreamResult {
        return try {
            val prompt = PromptBuilder.buildStreamExtractionPrompt(url, html)
            val response = aiProvider.complete(prompt)
            val cleaned = response
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = JSONObject(cleaned)
            val streamUrl = json.optString("streamUrl", "")
            val typeStr = json.optString("type", "unknown")

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
