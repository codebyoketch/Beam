package com.beam.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ── ParsedPage ────────────────────────────────────────────────────────────────
// The full AI-analyzed result for a website URL

data class ParsedPage(
    val siteName: String,
    val sourceUrl: String,
    val rows: List<ContentRow>
)

// ── ContentRow ────────────────────────────────────────────────────────────────
// A horizontal row of content cards (e.g. "Trending", "Action Movies")

data class ContentRow(
    val title: String,
    val items: List<ContentItem>
)

// ── ContentItem ───────────────────────────────────────────────────────────────
// A single video card shown in the browse grid

data class ContentItem(
    val title: String,
    val thumbnailUrl: String,
    val detailUrl: String,
    val description: String = ""
)

// ── Site ──────────────────────────────────────────────────────────────────────
// A saved site stored in Room DB

@Entity(tableName = "sites")
data class Site(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val iconUrl: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

// ── StreamResult ──────────────────────────────────────────────────────────────
// Result of stream URL extraction

data class StreamResult(
    val url: String,
    val type: StreamType
)

enum class StreamType {
    HLS,    // .m3u8
    MP4,    // .mp4
    DASH,   // .mpd
    IFRAME, // embedded iframe to another host
    UNKNOWN
}
