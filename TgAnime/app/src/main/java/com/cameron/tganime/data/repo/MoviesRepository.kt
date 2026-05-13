package com.cameron.tganime.data.repo

import com.cameron.tganime.data.network.MediaBackendApi
import com.cameron.tganime.data.network.NetworkModule
import com.cameron.tganime.data.network.TmdbListResponse
import com.cameron.tganime.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * TMDB list data via the yinshi `media-backend` `/api/tmdb/list` route.
 *
 * The base URL and Bearer token both come from [SettingsStore] so users on a
 * self-hosted yinshi can override them. We rebuild the Retrofit client per
 * call because either may have changed since last call; the underlying
 * OkHttp client + connection pool is shared, so cost is negligible.
 */
class MoviesRepository(
    private val network: NetworkModule,
    private val settings: SettingsStore,
) {
    private suspend fun api(): MediaBackendApi {
        val base = settings.mediaBackendBaseFlow.first()
        val token = settings.mediaBackendTokenFlow.first()
        return network.mediaBackendApi(base, token)
    }

    suspend fun popular(kind: String = "movie", page: Int = 1): TmdbListResponse =
        api().list(endpoint = "popular", kind = kind, page = page)

    suspend fun topRated(kind: String = "movie", page: Int = 1): TmdbListResponse =
        api().list(endpoint = "top_rated", kind = kind, page = page)

    suspend fun trending(
        kind: String = "movie",
        window: String = "week",
        page: Int = 1,
    ): TmdbListResponse =
        api().list(endpoint = "trending", kind = kind, page = page, window = window)

    suspend fun nowPlaying(page: Int = 1): TmdbListResponse =
        api().list(endpoint = "now_playing", kind = "movie", page = page)

    suspend fun upcoming(page: Int = 1): TmdbListResponse =
        api().list(endpoint = "upcoming", kind = "movie", page = page)
}
