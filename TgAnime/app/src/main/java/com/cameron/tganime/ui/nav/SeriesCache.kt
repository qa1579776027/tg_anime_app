package com.cameron.tganime.ui.nav

import com.cameron.tganime.data.model.SeriesGroup
import java.util.concurrent.ConcurrentHashMap

/**
 * Hand-off cache for navigating from Search → SeriesDetail. The detail route
 * only carries the series key in its arguments; the heavy SeriesGroup object
 * (sources + posters) is stashed here in-memory.
 *
 * Lifetime is process-scope. Entries are evicted manually when the detail
 * screen is popped to avoid unbounded growth.
 */
object SeriesCache {
    private val store = ConcurrentHashMap<String, SeriesGroup>()

    fun put(group: SeriesGroup) {
        store[group.key] = group
    }

    fun get(key: String): SeriesGroup? = store[key]

    fun clear() { store.clear() }
}
