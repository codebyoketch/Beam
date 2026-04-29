package com.beam.ai

import android.content.Context
import android.util.Log
import com.beam.cache.CacheManager
import com.beam.model.ContentItem
import com.beam.model.ContentRow
import com.beam.model.ParsedPage
import com.beam.scraper.HtmlFetcher
import org.json.JSONObject

/**
 * PageAnalyzer is the core of Beam.
 * It fetches a URL, sends the HTML to the AI, and returns
 * a structured ParsedPage ready for the TV UI.
 *
 * Results are cached in Room DB for 24 hours to avoid
 * repeated AI calls for the same page.
 */
class PageAnalyzer(
    private val provider: AIProvider,
    private val htmlFetcher: HtmlFetcher,
    private val context: Context? = null  // optional — needed for cache
) {

    companion object {
        private const val TAG = "PageAnalyzer"
    }

    private val cache: CacheManager? = context?.let { CacheManager(it) }

    /**
     * Analyzes a URL and returns structured page content.
     * Checks cache first — only calls AI if no valid cache exists.
     *
     * @param url The website URL to analyze
     * @param forceRefresh Skip cache and always fetch fresh
     * @param onProgress Optional callback for loading state updates
     */
    suspend fun analyze(
        url: String,
        forceRefresh: Boolean = false,
        onProgress: ((String) -> Unit)? = null
    ): Result<ParsedPage> {
        return try {

            // Check cache first
            if (!forceRefresh && cache != null) {
                onProgress?.invoke("Checking cache...")
                val cached = cache.get(url)
                if (cached != null) {
                    Log.d(TAG, "Returning cached result for $url")
                    onProgress?.invoke("Loaded from cache!")
                    return Result.success(cached)
                }
            }

            // Step 1: Fetch the HTML
            onProgress?.invoke("Fetching page...")
            val html = htmlFetcher.fetch(url)
                ?: return Result.failure(Exception("Could not load page at $url"))

            // Step 2: Build and send the AI prompt
            onProgress?.invoke("Analyzing content with AI...")
            val prompt = PromptBuilder.buildPageAnalysisPrompt(url, html)
            val aiResponse = provider.complete(prompt)

            // Step 3: Parse the AI's JSON response
            onProgress?.invoke("Building TV view...")
            val parsedPage = parseAiResponse(aiResponse, url)

            // Step 4: Save to cache
            cache?.save(url, parsedPage)

            Result.success(parsedPage)

        } catch (e: AIProviderException) {
            Log.e(TAG, "AI error analyzing $url", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error analyzing $url", e)
            Result.failure(e)
        }
    }

    /**
     * Parses the raw JSON string from the AI into a ParsedPage model.
     * Handles common AI output issues like markdown fences.
     */
    private fun parseAiResponse(rawResponse: String, sourceUrl: String): ParsedPage {
        val cleaned = rawResponse
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val json = JSONObject(cleaned)
            val siteName = json.optString("siteName", extractDomain(sourceUrl))
            val rowsJson = json.optJSONArray("rows")

            val rows = mutableListOf<ContentRow>()

            if (rowsJson != null) {
                for (i in 0 until rowsJson.length()) {
                    val rowJson = rowsJson.getJSONObject(i)
                    val title = rowJson.optString("title", "Content")
                    val itemsJson = rowJson.optJSONArray("items") ?: continue

                    val items = mutableListOf<ContentItem>()
                    for (j in 0 until itemsJson.length()) {
                        val itemJson = itemsJson.getJSONObject(j)
                        val item = ContentItem(
                            title = itemJson.optString("title", "Untitled"),
                            thumbnailUrl = itemJson.optString("thumbnailUrl", ""),
                            detailUrl = itemJson.optString("detailUrl", ""),
                            description = itemJson.optString("description", "")
                        )
                        if (item.detailUrl.isNotBlank()) {
                            items.add(item)
                        }
                    }

                    if (items.isNotEmpty()) {
                        rows.add(ContentRow(title = title, items = items))
                    }
                }
            }

            ParsedPage(
                siteName = siteName,
                sourceUrl = sourceUrl,
                rows = rows
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AI response: $cleaned", e)
            ParsedPage(
                siteName = extractDomain(sourceUrl),
                sourceUrl = sourceUrl,
                rows = emptyList()
            )
        }
    }

    private fun extractDomain(url: String): String {
        return try {
            java.net.URL(url).host.removePrefix("www.")
        } catch (e: Exception) {
            url
        }
    }
}
