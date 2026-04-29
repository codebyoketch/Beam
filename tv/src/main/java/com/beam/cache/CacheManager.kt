package com.beam.cache

import android.content.Context
import android.util.Log
import com.beam.model.ContentItem
import com.beam.model.ContentRow
import com.beam.model.ParsedPage
import org.json.JSONArray
import org.json.JSONObject

/**
 * CacheManager wraps the Room DB and handles serialization of ParsedPage
 * to/from JSON for storage.
 *
 * Usage:
 *   val cache = CacheManager(context)
 *   val cached = cache.get(url)         // returns ParsedPage or null
 *   cache.save(url, parsedPage)         // saves result
 *   cache.invalidate(url)               // force refresh
 */
class CacheManager(context: Context) {

    companion object {
        private const val TAG = "CacheManager"
    }

    private val db = BeamDatabase.getInstance(context)
    private val dao = db.pageCacheDao()

    /**
     * Returns a cached ParsedPage if it exists and hasn't expired.
     * Returns null if not cached or expired.
     */
    suspend fun get(url: String): ParsedPage? {
        return try {
            val cached = dao.getByUrl(url) ?: run {
                Log.d(TAG, "Cache miss: $url")
                return null
            }

            if (cached.isExpired()) {
                Log.d(TAG, "Cache expired for: $url")
                dao.deleteByUrl(url)
                return null
            }

            Log.d(TAG, "Cache hit: $url")
            deserialize(cached)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cache for $url", e)
            null
        }
    }

    /**
     * Saves a ParsedPage to the cache.
     * Silently skips if the page has no rows (nothing worth caching).
     */
    suspend fun save(url: String, page: ParsedPage) {
        if (page.rows.isEmpty()) {
            Log.d(TAG, "Skipping cache — no rows for: $url")
            return
        }

        try {
            val cached = CachedPage(
                url = url,
                siteName = page.siteName,
                rowsJson = serializeRows(page.rows)
            )
            dao.insert(cached)
            Log.d(TAG, "Cached ${page.rows.size} rows for: $url")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cache for $url", e)
        }
    }

    /**
     * Removes a specific URL from cache — forces fresh fetch next time.
     */
    suspend fun invalidate(url: String) {
        dao.deleteByUrl(url)
        Log.d(TAG, "Invalidated cache for: $url")
    }

    /**
     * Removes all expired entries — call this periodically.
     */
    suspend fun cleanup() {
        dao.deleteExpired()
        Log.d(TAG, "Cache cleanup done. Remaining: ${dao.count()} entries")
    }

    /**
     * Clears the entire cache.
     */
    suspend fun clearAll() {
        dao.clearAll()
        Log.d(TAG, "Cache cleared")
    }

    // ── Serialization ──────────────────────────────────────────────────────────

    private fun serializeRows(rows: List<ContentRow>): String {
        val jsonArray = JSONArray()
        rows.forEach { row ->
            val rowJson = JSONObject().apply {
                put("title", row.title)
                val itemsArray = JSONArray()
                row.items.forEach { item ->
                    itemsArray.put(JSONObject().apply {
                        put("title", item.title)
                        put("thumbnailUrl", item.thumbnailUrl)
                        put("detailUrl", item.detailUrl)
                        put("description", item.description)
                    })
                }
                put("items", itemsArray)
            }
            jsonArray.put(rowJson)
        }
        return jsonArray.toString()
    }

    private fun deserialize(cached: CachedPage): ParsedPage {
        val rowsArray = JSONArray(cached.rowsJson)
        val rows = mutableListOf<ContentRow>()

        for (i in 0 until rowsArray.length()) {
            val rowJson = rowsArray.getJSONObject(i)
            val itemsArray = rowJson.getJSONArray("items")
            val items = mutableListOf<ContentItem>()

            for (j in 0 until itemsArray.length()) {
                val itemJson = itemsArray.getJSONObject(j)
                items.add(ContentItem(
                    title = itemJson.optString("title"),
                    thumbnailUrl = itemJson.optString("thumbnailUrl"),
                    detailUrl = itemJson.optString("detailUrl"),
                    description = itemJson.optString("description")
                ))
            }

            rows.add(ContentRow(title = rowJson.optString("title"), items = items))
        }

        return ParsedPage(
            siteName = cached.siteName,
            sourceUrl = cached.url,
            rows = rows
        )
    }
}
