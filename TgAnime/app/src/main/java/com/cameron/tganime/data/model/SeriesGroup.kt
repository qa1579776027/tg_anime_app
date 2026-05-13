package com.cameron.tganime.data.model

/**
 * Search result grouped by series (one anime / show), aggregating every episode
 * across all encoder sources. Used by the new poster-grid search UI.
 */
data class SeriesGroup(
    /** Normalized lookup key — lowercase, punctuation stripped. */
    val key: String,
    /** Display title (original casing, may be Japanese / Chinese / English). */
    val title: String,
    /** Optional bgm.tv subject id when matched to an entry. */
    val bgmId: Long?,
    /** Optional Chinese title from bgm.tv. */
    val nameCn: String,
    /** Poster URL from bgm.tv. Empty if no match was found. */
    val posterUrl: String,
    /** Episodes that belong to this series. */
    val episodes: List<EpisodeGroup>,
    /** Hits whose episode number could not be parsed. */
    val unparsedSources: List<EpisodeSource>,
    /** Total source count (episodes + unparsed). */
    val totalSources: Int,
)
