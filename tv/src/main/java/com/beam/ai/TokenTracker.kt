package com.beam.ai

import android.content.Context
import android.util.Log
import androidx.room.*
import java.text.SimpleDateFormat
import java.util.*

// ── Entity ────────────────────────────────────────────────────────────────────

@Entity(tableName = "token_usage")
data class TokenUsage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,
    val task: String,
    val tokensUsed: Int,
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val timestamp: Long = System.currentTimeMillis()
)

// ── DAO ───────────────────────────────────────────────────────────────────────

@Dao
interface TokenUsageDao {

    @Insert
    suspend fun insert(usage: TokenUsage)

    @Query("SELECT SUM(tokensUsed) FROM token_usage WHERE provider = :provider AND date = :date")
    suspend fun getTodayUsage(provider: String, date: String): Int?

    @Query("SELECT SUM(tokensUsed) FROM token_usage WHERE provider = :provider AND date = :date AND task = :task")
    suspend fun getTaskUsage(provider: String, task: String, date: String): Int?

    @Query("SELECT * FROM token_usage WHERE date = :date ORDER BY timestamp DESC")
    suspend fun getTodayAll(date: String): List<TokenUsage>

    @Query("DELETE FROM token_usage WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}

// ── Database ──────────────────────────────────────────────────────────────────

@Database(entities = [TokenUsage::class], version = 1, exportSchema = false)
abstract class TokenDatabase : RoomDatabase() {
    abstract fun tokenUsageDao(): TokenUsageDao

    companion object {
        @Volatile private var INSTANCE: TokenDatabase? = null

        fun getInstance(context: Context): TokenDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    TokenDatabase::class.java,
                    "beam_tokens"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

// ── Tracker ───────────────────────────────────────────────────────────────────

/**
 * TokenTracker records AI token usage and provides usage summaries.
 * Automatically estimates tokens since most free APIs don't return exact counts.
 */
class TokenTracker(context: Context) {

    companion object {
        private const val TAG = "TokenTracker"

        // Approximate tokens per character (rough estimate)
        private const val CHARS_PER_TOKEN = 4

        // Daily limits per provider (free tier)
        val DAILY_LIMITS = mapOf(
            "gemini" to 1_000_000,  // Gemini Flash free tier
            "groq" to 100_000,      // Groq free tier
            "ollama" to Int.MAX_VALUE // Local — unlimited
        )

        // Warning threshold — switch providers at 80%
        const val WARNING_THRESHOLD = 0.80f
    }

    private val dao = TokenDatabase.getInstance(context).tokenUsageDao()
    private val today get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /**
     * Records token usage after an AI call.
     * Estimates tokens from prompt + response length.
     */
    suspend fun record(provider: String, task: String, prompt: String, response: String) {
        val estimated = (prompt.length + response.length) / CHARS_PER_TOKEN
        dao.insert(TokenUsage(provider = provider, task = task, tokensUsed = estimated))
        Log.d(TAG, "Recorded ~$estimated tokens for $provider ($task)")
    }

    /**
     * Returns today's total token usage for a provider.
     */
    suspend fun getTodayUsage(provider: String): Int {
        return dao.getTodayUsage(provider, today) ?: 0
    }

    /**
     * Returns remaining tokens for a provider today.
     */
    suspend fun getRemainingTokens(provider: String): Int {
        val limit = DAILY_LIMITS[provider] ?: Int.MAX_VALUE
        val used = getTodayUsage(provider)
        return (limit - used).coerceAtLeast(0)
    }

    /**
     * Returns true if a provider is near its daily limit.
     */
    suspend fun isNearLimit(provider: String): Boolean {
        val limit = DAILY_LIMITS[provider] ?: return false
        val used = getTodayUsage(provider)
        return used.toFloat() / limit >= WARNING_THRESHOLD
    }

    /**
     * Returns a summary of today's usage for all providers.
     */
    suspend fun getTodaySummary(): Map<String, Pair<Int, Int>> {
        return DAILY_LIMITS.keys.associateWith { provider ->
            val used = getTodayUsage(provider)
            val limit = DAILY_LIMITS[provider] ?: Int.MAX_VALUE
            Pair(used, limit)
        }
    }

    /**
     * Cleans up usage records older than 7 days.
     */
    suspend fun cleanup() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val cutoff = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
        dao.deleteOlderThan(cutoff)
    }
}
