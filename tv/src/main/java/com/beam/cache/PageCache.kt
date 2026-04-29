package com.beam.cache

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── Entity ────────────────────────────────────────────────────────────────────

@Entity(tableName = "page_cache")
data class CachedPage(
    @PrimaryKey
    val url: String,
    val siteName: String,
    val rowsJson: String,          // Serialized JSON of List<ContentRow>
    val cachedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + CACHE_DURATION_MS
) {
    companion object {
        const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
}

// ── DAO ───────────────────────────────────────────────────────────────────────

@Dao
interface PageCacheDao {

    @Query("SELECT * FROM page_cache WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): CachedPage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: CachedPage)

    @Query("DELETE FROM page_cache WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM page_cache WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM page_cache")
    suspend fun count(): Int

    @Query("DELETE FROM page_cache")
    suspend fun clearAll()
}

// ── Database ──────────────────────────────────────────────────────────────────

@Database(entities = [CachedPage::class], version = 1, exportSchema = false)
abstract class BeamDatabase : RoomDatabase() {
    abstract fun pageCacheDao(): PageCacheDao

    companion object {
        @Volatile
        private var INSTANCE: BeamDatabase? = null

        fun getInstance(context: android.content.Context): BeamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BeamDatabase::class.java,
                    "beam_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
