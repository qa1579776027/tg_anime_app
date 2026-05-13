package com.cameron.tganime.data.model

/** Single playable source within an episode (one acgn.es hit). */
data class EpisodeSource(
    val channel: String,
    val msgId: Long,
    val channelName: String,
    val size: Long,
    val suffix: String,
    val rawText: String,
    val quality: List<String>,
    val group: String?,
)

/** All known sources for one parsed episode number; empty list of episodes means unparsed. */
data class EpisodeGroup(
    val episode: Int,
    val sources: List<EpisodeSource>,
)
