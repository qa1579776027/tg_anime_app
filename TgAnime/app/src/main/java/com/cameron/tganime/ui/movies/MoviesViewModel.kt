package com.cameron.tganime.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cameron.tganime.TgAnimeApp
import com.cameron.tganime.data.network.TmdbItem
import com.cameron.tganime.data.network.TvHit
import com.cameron.tganime.data.prefs.SettingsStore
import com.cameron.tganime.data.repo.MoviesRepository
import com.cameron.tganime.data.repo.TvSearchRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val tvRepo: TvSearchRepository = TgAnimeApp.get().tvSearchRepo,
    private val settings: SettingsStore = TgAnimeApp.get().settings,
) : ViewModel() {

    private val _state = MutableStateFlow<MoviesState>(MoviesState.Loading)
    val state: StateFlow<MoviesState> = _state.asStateFlow()

    // -- search --
    val searchQuery = MutableStateFlow("")
    val searching = MutableStateFlow(false)
    val searchHits = MutableStateFlow<List<TvHit>>(emptyList())
    val searchLoading = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)

    init { refresh() }

    fun refresh() {
        _state.value = MoviesState.Loading
        viewModelScope.launch {
            try {
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

    fun startSearch() { searching.value = true }

    fun stopSearch() {
        searching.value = false
        searchQuery.value = ""
        searchHits.value = emptyList()
        searchError.value = null
    }

    val playingHitId = MutableStateFlow<Long?>(null)
    val playError = MutableStateFlow<String?>(null)

    fun submitSearch() {
        val kw = searchQuery.value.trim()
        if (kw.isEmpty()) return
        searchLoading.value = true
        searchError.value = null
        viewModelScope.launch {
            try {
                val base = settings.proxyBaseFlow.first().trim()
                if (base.isBlank()) {
                    searchError.value = "尚未配置后端地址"
                    return@launch
                }
                val resp = tvRepo.search(base, kw = kw, offset = 0)
                searchHits.value = resp.hits
            } catch (t: Throwable) {
                searchError.value = t.message ?: t::class.java.simpleName
            } finally {
                searchLoading.value = false
            }
        }
    }

    fun play(hit: TvHit, onResolved: (url: String, title: String) -> Unit) {
        if (playingHitId.value != null) return
        playingHitId.value = hit.hit_id
        playError.value = null
        viewModelScope.launch {
            try {
                val base = settings.proxyBaseFlow.first().trim()
                val resp = tvRepo.play(base, hit.hit_id)
                if (resp.url.isBlank()) {
                    playError.value = "后端返回空 URL"
                } else {
                    onResolved(resp.url, resp.title.ifBlank { hit.title })
                }
            } catch (t: Throwable) {
                playError.value = t.message ?: t::class.java.simpleName
            } finally {
                playingHitId.value = null
            }
        }
    }
}
