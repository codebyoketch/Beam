package com.beam.ai

import org.jsoup.Jsoup

/**
 * Builds the prompt sent to the AI provider.
 * Cleans and trims the HTML before sending to keep token usage low.
 */
object PromptBuilder {

    // Max characters of HTML to send — keeps costs/tokens low
    private const val MAX_HTML_LENGTH = 12_000

    fun buildPageAnalysisPrompt(url: String, rawHtml: String): String {
        val cleanedHtml = cleanHtml(rawHtml)

        return """
You are an AI that converts streaming websites into structured TV app content.

Analyze the HTML below from this URL: $url

Your job is to find all movies, TV shows, videos, or any media content on the page.
Group them into logical rows/categories if possible (e.g. "Trending", "Action", "New Releases").

Return ONLY a valid JSON object. No explanation, no markdown, no backticks. Just raw JSON.

Use this exact structure:
{
  "siteName": "Name of the website",
  "rows": [
    {
      "title": "Row/Category name",
      "items": [
        {
          "title": "Title of the movie or show",
          "thumbnailUrl": "https://absolute-url-to-thumbnail.jpg",
          "detailUrl": "https://absolute-url-to-the-video-page.com/video",
          "description": "Short description if available, empty string if not"
        }
      ]
    }
  ]
}

Rules:
- thumbnailUrl MUST be an absolute URL (starting with https:// or http://)
- detailUrl MUST be an absolute URL to where the video can be found
- If you cannot find thumbnails, use empty string ""
- Skip any items that are clearly ads or non-video content
- Return at least 1 row even if content is minimal
- If the page has no video content at all, return: {"siteName": "", "rows": []}

HTML to analyze:
$cleanedHtml
        """.trimIndent()
    }

    fun buildStreamExtractionPrompt(url: String, rawHtml: String): String {
        val cleanedHtml = cleanHtml(rawHtml)

        return """
You are an expert at finding video stream URLs inside HTML pages.

Analyze this HTML from: $url

Find the direct video stream URL. This could be:
- A .m3u8 HLS stream URL
- A .mp4 direct video URL
- A .mpd DASH stream URL
- A src attribute inside a <video> tag
- A URL inside a JavaScript variable like "file:", "src:", "source:", "url:"
- An iframe src pointing to a video host

Return ONLY a JSON object like this:
{
  "streamUrl": "https://the-direct-video-url.com/video.m3u8",
  "type": "hls"
}

Where type is one of: "hls", "mp4", "dash", "iframe", "unknown"

If no stream URL is found, return:
{
  "streamUrl": "",
  "type": "unknown"
}

Return ONLY raw JSON. No explanation.

HTML:
$cleanedHtml
        """.trimIndent()
    }

    /**
     * Cleans HTML to reduce token usage:
     * - Removes script and style tags (keep script src for hints)
     * - Removes comments
     * - Collapses whitespace
     * - Trims to max length
     */
    private fun cleanHtml(html: String): String {
        return try {
            val doc = Jsoup.parse(html)

            // Remove elements that waste tokens
            doc.select("style, noscript, footer, nav, header").remove()

            // Keep scripts — they sometimes contain stream URLs
            // but strip the content of long ones
            doc.select("script").forEach { script ->
                if (script.html().length > 500) {
                    script.remove()
                }
            }

            // Get clean text representation
            val cleaned = doc.html()
                .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("\\s+"), " ")
                .trim()

            // Trim to max length
            if (cleaned.length > MAX_HTML_LENGTH) {
                cleaned.substring(0, MAX_HTML_LENGTH) + "\n... [HTML truncated]"
            } else {
                cleaned
            }
        } catch (e: Exception) {
            // Fallback: just truncate raw HTML
            html.take(MAX_HTML_LENGTH)
        }
    }
}
