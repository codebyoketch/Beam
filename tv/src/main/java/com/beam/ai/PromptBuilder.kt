package com.beam.ai

import android.util.Log
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Builds the prompt sent to the AI provider.
 * Cleans and trims the HTML before sending to keep token usage low.
 */
object PromptBuilder {

    private const val TAG = "PromptBuilder"

    // Increased limit — Groq handles larger context well
    private const val MAX_HTML_LENGTH = 20_000

    fun buildPageAnalysisPrompt(url: String, rawHtml: String): String {
        val cleanedHtml = cleanHtml(rawHtml)
        Log.d(TAG, "Cleaned HTML length: ${cleanedHtml.length} chars (original: ${rawHtml.length})")

        return """
You are an AI that extracts media content from websites and formats it for a TV app.

Analyze the HTML below from: $url

Find ANY of the following on the page:
- Movies, TV shows, episodes, videos, films
- Links to pages that likely contain videos (e.g. /movie/, /watch/, /film/, /video/)
- Titles with associated links, even if no thumbnail exists
- Lists of media content, even if just text links

Group items into rows by category if possible. If no categories exist, put everything in one row called "Content".

IMPORTANT RULES:
- detailUrl is the most important field — always include it if a link exists
- thumbnailUrl is optional — use empty string "" if not found
- description is optional — use empty string "" if not found  
- Include ALL links that could be media content, even if you are not 100% sure
- The base URL is $url — convert relative URLs like /movie/abc to absolute URLs
- Do NOT return empty rows array unless the page truly has zero links

Return ONLY raw JSON with this structure, no markdown, no backticks:
{
  "siteName": "Name of the website",
  "rows": [
    {
      "title": "Category name or Content",
      "items": [
        {
          "title": "Title of the item",
          "thumbnailUrl": "",
          "detailUrl": "https://absolute-url-to-page",
          "description": ""
        }
      ]
    }
  ]
}

HTML:
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
     * Extracts meaningful text and links from HTML.
     * Uses Jsoup to get clean content instead of raw HTML tags.
     */
    private fun cleanHtml(html: String): String {
        return try {
            val doc = Jsoup.parse(html)

            // Remove noise elements
            doc.select("style, script, noscript, footer, nav, head, iframe[src*=ad]").remove()

            // Extract all links with their text — this is the most useful signal
            val links = StringBuilder()
            doc.select("a[href]").forEach { element ->
                val href = element.absUrl("href").ifBlank { element.attr("href") }
                val text = element.text().trim()
                val img = element.select("img[src]").firstOrNull()?.absUrl("src") ?: ""

                if (text.isNotBlank() && href.isNotBlank() && href.startsWith("http")) {
                    if (img.isNotBlank()) {
                        links.append("LINK: $text | URL: $href | IMG: $img\n")
                    } else {
                        links.append("LINK: $text | URL: $href\n")
                    }
                }
            }

            // Also get the page title and headings for context
            val title = doc.title()
            val headings = doc.select("h1, h2, h3")
                .take(10)
                .joinToString("\n") { it.text().trim() }
                .ifBlank { "" }

            val result = buildString {
                append("PAGE TITLE: $title\n\n")
                if (headings.isNotBlank()) {
                    append("HEADINGS:\n$headings\n\n")
                }
                append("LINKS FOUND:\n")
                append(links.toString())
            }

            Log.d(TAG, "Extracted ${result.lines().size} lines of content")

            // Trim to max length
            if (result.length > MAX_HTML_LENGTH) {
                result.substring(0, MAX_HTML_LENGTH) + "\n... [truncated]"
            } else {
                result
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning HTML", e)
            html.take(MAX_HTML_LENGTH)
        }
    }
}
