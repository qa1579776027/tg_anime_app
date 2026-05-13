package com.cameron.tganime.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * api.bgm.tv/calendar returns an array of 7 weekday buckets.
 *
 *     [ { weekday: {en, cn, ja, id}, items: [ {id, url, type, name, name_cn,
 *         summary, air_date, air_weekday, rating, rank, images, collection } ] } ]
 *
 * `images.*` URLs come back as http:// (lain.bgm.tv has no TLS) — the manifest
 * already permits cleartext, no rewrite needed.
 */
@Serializable
data class BgmCalendarDay(
    val weekday: BgmWeekday,
    val items: List<BgmSubject> = emptyList(),
)

@Serializable
data class BgmWeekday(
    val en: String = "",
    val cn: String = "",
    val ja: String = "",
    val id: Int = 0,
)

@Serializable
data class BgmSubject(
    val id: Long = 0,
    val url: String = "",
    val type: Int = 0,
    val name: String = "",
    @SerialName("name_cn") val nameCn: String = "",
    val summary: String = "",
    @SerialName("air_date") val airDate: String = "",
    @SerialName("air_weekday") val airWeekday: Int = 0,
    val images: BgmImages = BgmImages(),
    val rating: BgmRating? = null,
    val rank: Int = 0,
)

@Serializable
data class BgmImages(
    val large: String = "",
    val common: String = "",
    val medium: String = "",
    val small: String = "",
    val grid: String = "",
)

@Serializable
data class BgmRating(
    val total: Int = 0,
    val score: Double = 0.0,
)

/**
 * Response from the legacy `/search/subject/{kw}` endpoint. Successful search
 * returns `{ results: N, list: [...] }`. Empty / failed searches return
 * `{ code: 404, ... }` without `list` — kotlinx with `coerceInputValues` will
 * fall back to empty list.
 */
@Serializable
data class BgmSearchResponse(
    val results: Int = 0,
    val list: List<BgmSubject> = emptyList(),
)
