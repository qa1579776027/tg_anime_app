package com.cameron.tganime.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cameron.tganime.TgAnimeApp
import com.cameron.tganime.data.network.TmdbItem
import com.cameron.tganime.data.repo.MoviesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A single horizontally-scrolling Netflix-style row. */
data class MovieRow(
    val key: String,
    val title: String,
    val items: List<TmdbItem>,
)

sealed interface MoviesState {
    data object Loading : MoviesState
    data class Loaded(
        /** Top items used as the auto-scrolling hero banner. */
        val hero: List<TmdbItem>,
        val rows: List<MovieRow>,
    ) : MoviesState
    data class Failed(val msg: String) : MoviesState
}

/**
 * Loads the 电影 tab's six Netflix-style rows in parallel.
 *
 * Hero data is `trending/movie/day`'s top 6 (newest "what's hot today"),
 * the rest is the standard TMDB Discover taxonomy.
 */
class MoviesViewModel(
    private val repo: MoviesRepository = TgAnimeApp.get().moviesRepo,
) : ViewModel() {

    private val _state = MutableStateFlow<MoviesState>(MoviesState.Loading)
    val state: StateFlow<MoviesState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = MoviesState.Loading
        viewModelScope.launch {
            try {
                // 6 backend calls in parallel; each is a passthrough to TMDB
                // so they fan out independently. Backend uses a 24-thread pool
                // and 30-day TMDB cache, so contention isn't a concern.
                val trendingDay = async { repo.trending(kind = "movie", window = "day") }
                val popular = async { repo.popular(kind = "movie") }
                val nowPlaying = async { repo.nowPlaying() }
                val topRated = async { repo.topRated(kind = "movie") }
                val upcoming = async { repo.upcoming() }
                val popularTv = async { repo.popular(kind = "tv") }
                val all = awaitAll(
                    trendingDay, popular, nowPlaying, topRated, upcoming, popularTv,
                )
                val hero = all[0].results.take(6)
                _state.value = MoviesState.Loaded(
                    hero = hero,
                    rows = listOf(
                        MovieRow("trending_day", "今日热门", all[0].results),
                        MovieRow("popular_movie", "热门电影", all[1].results),
                        MovieRow("now_playing", "正在热映", all[2].results),
                        MovieRow("top_rated", "高分电影", all[3].results),
                        MovieRow("upcoming", "即将上映", all[4].results),
                        MovieRow("popular_tv", "热门剧集", all[5].results),
                    ),
                )
            } catch (t: Throwable) {
                _state.value = MoviesState.Failed(t.message ?: t::class.java.simpleName)
            }
        }
    }
}
