package com.cameron.tganime.ui.nav

import com.cameron.tganime.data.network.BgmSubject
import com.cameron.tganime.data.prefs.WatchEntry
import java.util.concurrent.ConcurrentHashMap

/**
 * Identifier + display data needed to open a series detail page when we don't
 * yet have a fully-loaded [com.cameron.tganime.data.model.SeriesGroup]. The
 * detail [com.cameron.tganime.ui.detail.SeriesDetailViewModel] uses the
 * [title] to run an acgn.es search and resolve episode sources.
 */
data class SeriesLookup(
    val key: String,
    val title: String,
    val nameCn: String = "",
    val posterUrl: String = "",
    val bgmId: Long? = null,
)

/**
 * Hand-off cache for navigating from Explore / Calendar / WatchList → detail.
 * The detail route only carries a stable string key; the rest of the lookup
 * data (title to search, poster, bgm id) is stashed here in-memory.
 *
 * Entries persist for the process lifetime. Keys are stable per source item
 * (e.g. "bgm-12345"), so revisits during the same session re-use the same key.
 */
object PendingSeriesLookup {
    private val store = ConcurrentHashMap<String, SeriesLookup>()

    fun put(lookup: SeriesLookup) {
        store[lookup.key] = lookup
    }

    fun get(key: String): SeriesLookup? = store[key]
}

fun lookupForBgmSubject(s: BgmSubject): SeriesLookup =
    SeriesLookup(
        key = "bgm-${s.id}",
        title = s.nameCn.ifBlank { s.name },
        nameCn = s.nameCn,
        posterUrl = s.images.large.ifBlank { s.images.common.ifBlank { s.images.medium } },
        bgmId = s.id,
    )

fun lookupForWatchEntry(e: WatchEntry): SeriesLookup =
    SeriesLookup(
        key = "watch-${e.id}",
        title = e.nameCn.ifBlank { e.name },
        nameCn = e.nameCn,
        posterUrl = e.image,
        bgmId = e.id,
    )
