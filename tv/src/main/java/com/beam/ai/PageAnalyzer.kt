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
 * PageAnalyzer orchestrates page fetching, AI analysis, and caching.
 * Uses AIRouter to automatically select the best provider for the task.
 */
class PageAnalyzer(
    private val htmlFetcher: HtmlFetcher,
    private val context: Context
) {
    companion object {
        private const val TAG = "PageAnalyzer"
    }

    private val cache = CacheManager(context)
    private val tokenTracker = TokenTracker(context)
    private val router = AIRouter(context, tokenTracker)

    suspend fun analyze(
        url: String,
        forceRefresh: Boolean = false,
        onProgress: ((String) -> Unit)? = null
    ): Result<ParsedPage> {
        return try {
            // Normalize URL — ensure it has http/https scheme
            val normalizedUrl = when {
                url.startsWith("http://") || url.startsWith("https://") -> url
                url.startsWith("//") -> "https:$url"
                else -> "https://$url"
            }.trim().removeSuffix("/")

            // Check cache first
            if (!forceRefresh) {
                onProgress?.invoke("Checking cache...")
                val cached = cache.get(normalizedUrl)
                if (cached != null) {
                    Log.d(TAG, "Cache hit for $normalizedUrl")
                    onProgress?.invoke("Loaded from cache!")
                    return Result.success(cached)
                }
            }

            // Get best provider for page analysis
            val provider = router.getProvider(AITask.PAGE_ANALYSIS)
                ?: return Result.failure(Exception("No AI provider configured. Please add an API key in Settings."))

            Log.d(TAG, "Using provider: ${provider.name} for page analysis")

            // Fetch HTML
            onProgress?.invoke("Fetching page...")
            val html = htmlFetcher.fetch(normalizedUrl)
                ?: return Result.failure(Exception("Could not load page at $normalizedUrl"))

            // Build prompt and call AI
            onProgress?.invoke("Analyzing with ${provider.name}...")
            val prompt = PromptBuilder.buildPageAnalysisPrompt(normalizedUrl, html)
            val response = provider.complete(prompt)

            // Track token usage
            router.recordUsage(provider, AITask.PAGE_ANALYSIS, prompt, response)

            // Parse response
            onProgress?.invoke("Building TV view...")
            val parsedPage = parseAiResponse(response, normalizedUrl)

            // Save to cache
            cache.save(normalizedUrl, parsedPage)

            Result.success(parsedPage)

        } catch (e: AIProviderException) {
            Log.e(TAG, "AI error analyzing $url", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing $url", e)
            Result.failure(e)
        }
    }

    private fun parseAiResponse(rawResponse: String, sourceUrl: String): ParsedPage {
        val cleaned = rawResponse
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        return try {
            val json = JSONObject(cleaned)
            val siteName = json.optString("siteName", extractDomain(sourceUrl))
            val rowsJson = json.optJSONArray("rows")
            val rows = mutableListOf<ContentRow>()

            if (rowsJson != null) {
                for (i in 0 until rowsJson.length()) {
                    val rowJson = rowsJson.getJSONObject(i)
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
                        if (item.detailUrl.isNotBlank()) items.add(item)
                    }
                    if (items.isNotEmpty()) {
                        rows.add(ContentRow(title = rowJson.optString("title", "Content"), items = items))
                    }
                }
            }

            ParsedPage(siteName = siteName, sourceUrl = sourceUrl, rows = rows)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AI response", e)
            ParsedPage(siteName = extractDomain(sourceUrl), sourceUrl = sourceUrl, rows = emptyList())
        }
    }

    private fun extractDomain(url: String) = try {
        java.net.URL(url).host.removePrefix("www.")
    } catch (e: Exception) { url }
}