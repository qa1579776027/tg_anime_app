package com.cameron.tganime.data.network

import kotlinx.serialization.Serializable

/**
 * Models for the yinshi (media-backend) `/api/tmdb/list` endpoint.
 *
 * Backend normalizes TMDB's raw schema (poster_path, vote_average, etc.) into
 * the shape below so the client doesn't have to deal with TMDB image-path
 * concatenation or the title/name dual key.
 */
@Serializable
data class TmdbListResponse(
    val results: List<TmdbItem> = emptyList(),
    val page: Int = 1,
    val total_pages: Int = 1,
    val total_results: Int = 0,
)

@Serializable
data class TmdbItem(
    val id: Long = 0,
    /** "movie" | "tv" */
    val kind: String = "movie",
    val title: String = "",
    val original_title: String = "",
    /** Full TMDB image URL (w500) or null if TMDB has no poster. */
    val poster: String? = null,
    /** Full TMDB image URL (w1280) or null if TMDB has no backdrop. */
    val backdrop: String? = null,
    val overview: String = "",
    val rating: Double? = null,
    /** 4-digit year string or "" if unknown. */
    val year: String = "",
)
