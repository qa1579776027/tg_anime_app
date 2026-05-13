package com.cameron.tganime.data.network

import kotlinx.serialization.Serializable

/**
 * A single search hit from `GET /tv/search` on the tg_anime backend.
 *
 * Each hit represents one share-link (movie / pack) the bot indexed. Pass
 * [hitId] to `POST /tv/play` to obtain a streaming URL.
 */
@Serializable
data class TvHit(
    val hit_id: Long,
    val drive: String = "",
    val share_url: String = "",
    val passcode: String = "",
    val title: String = "",
    val quality: List<String> = emptyList(),
    val bot_source: String = "",
    val posted_at: String = "",
)

/**
 * Response shape of `GET /tv/search?kw=&offset=&page_size=`.
 *
 * Pagination protocol (cursor-based):
 *  - First call: omit `offset`, get hits[0..page_size-1].
 *  - Load-more: pass `offset = totalLoadedSoFar`, get next page.
 *  - Stop when [has_more] is false.
 *
 * The backend keeps a per-keyword session cached for 5 minutes; `refresh=true`
 * forces a fresh bot search.
 */
@Serializable
data class TvSearchResponse(
    val keyword: String = "",
    val offset: Int = 0,
    val count: Int = 0,
    val hits: List<TvHit> = emptyList(),
    val has_more: Boolean = false,
    val total_so_far: Int = 0,
    val drain_done: Boolean = false,
)

/** Request body for `POST /tv/play`. */
@Serializable
data class TvPlayRequest(val hit_id: Long)

/** Response shape of `POST /tv/play`. */
@Serializable
data class TvPlayResponse(
    val url: String = "",
    val title: String = "",
    val cached: Boolean = false,
    val folder: String = "",
    val transfer_sec: Double? = null,
)
