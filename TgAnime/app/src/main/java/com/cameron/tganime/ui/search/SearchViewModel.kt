package com.cameron.tganime.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cameron.tganime.TgAnimeApp
import com.cameron.tganime.data.network.TvHit
import com.cameron.tganime.data.prefs.SettingsStore
import com.cameron.tganime.data.repo.TvSearchRepository
import com.cameron.tganime.ui.nav.SearchBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Cursor-based search state matching the tg_anime `/tv/search` backend. */
sealed interface SearchState {
    data object Idle : SearchState
    /** Showing nothing yet; first page is loading. */
    data object Loading : SearchState
    /** Failed before any hits were loaded. */
    data class Failed(val msg: String) : SearchState
    /**
     * Hits accumulated across one or more pages. [loadingMore] is true while
     * the next /tv/search call (with offset > 0) is in flight; [hasMore] is
     * the backend's signal that more pages remain.
     */
    data class Loaded(
        val keyword: String,
        val hits: List<TvHit>,
        val totalSoFar: Int,
        val hasMore: Boolean,
        val drainDone: Boolean,
        val loadingMore: Boolean = false,
        val loadMoreError: String? = null,
    ) : SearchState
}

class SearchViewModel(
    private val repo: TvSearchRepository = TgAnimeApp.get().tvSearchRepo,
    private val settings: SettingsStore = TgAnimeApp.get().settings,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    /** Set when the user taps a hit and we're resolving its playback URL. */
    private val _playing = MutableStateFlow<Long?>(null)
    val playingHitId: StateFlow<Long?> = _playing.asStateFlow()

    private val _playError = MutableStateFlow<String?>(null)
    val playError: StateFlow<String?> = _playError.asStateFlow()

    val proxyBase: StateFlow<String> = settings.proxyBaseFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init {
        // "Open Search from Explore" hand-off.
        viewModelScope.launch {
            SearchBus.events.collect { query -> runWith(query) }
        }
    }

    fun setQuery(value: String) { _query.value = value }

    fun submit() {
        val word = _query.value.trim()
        if (word.isEmpty()) return
        val base = proxyBase.value
        if (base.isBlank()) {
            _state.value = SearchState.Failed("尚未配置后端地址")
            return
        }
        _state.value = SearchState.Loading
        viewModelScope.launch {
            try {
                val resp = repo.search(base, kw = word, offset = 0)
                _state.value = SearchState.Loaded(
                    keyword = resp.keyword.ifBlank { word },
                    hits = resp.hits,
                    totalSoFar = resp.total_so_far,
                    hasMore = resp.has_more,
                    drainDone = resp.drain_done,
                )
            } catch (t: Throwable) {
                _state.value = SearchState.Failed(t.message ?: t::class.java.simpleName)
            }
        }
    }

    /** Append the next page (offset = current hit count). Idempotent while in-flight. */
    fun loadMore() {
        val loaded = _state.value as? SearchState.Loaded ?: return
        if (loaded.loadingMore || !loaded.hasMore) return
        val base = proxyBase.value
        if (base.isBlank()) return
        _state.value = loaded.copy(loadingMore = true, loadMoreError = null)
        viewModelScope.launch {
            try {
                val resp = repo.search(
                    base,
                    kw = loaded.keyword,
                    offset = loaded.hits.size,
                )
                _state.value = loaded.copy(
                    hits = loaded.hits + resp.hits,
                    totalSoFar = resp.total_so_far,
                    hasMore = resp.has_more,
                    drainDone = resp.drain_done,
                    loadingMore = false,
                )
            } catch (t: Throwable) {
                _state.value = loaded.copy(
                    loadingMore = false,
                    loadMoreError = t.message ?: t::class.java.simpleName,
                )
            }
        }
    }

    /** Force a fresh search bypassing the backend's 5-minute cache. */
    fun refresh() {
        val word = _query.value.trim().ifEmpty {
            (state.value as? SearchState.Loaded)?.keyword.orEmpty()
        }
        if (word.isEmpty()) return
        val base = proxyBase.value
        if (base.isBlank()) {
            _state.value = SearchState.Failed("尚未配置后端地址")
            return
        }
        _state.value = SearchState.Loading
        _query.value = word
        viewModelScope.launch {
            try {
                val resp = repo.search(base, kw = word, offset = 0, refresh = true)
                _state.value = SearchState.Loaded(
                    keyword = resp.keyword.ifBlank { word },
                    hits = resp.hits,
                    totalSoFar = resp.total_so_far,
                    hasMore = resp.has_more,
                    drainDone = resp.drain_done,
                )
            } catch (t: Throwable) {
                _state.value = SearchState.Failed(t.message ?: t::class.java.simpleName)
            }
        }
    }

    /**
     * Resolve a hit's playable URL via `POST /tv/play` and invoke [onResolved]
     * with `(url, title)`. The backend may take a few seconds the first time
     * (it transfers the share into OpenList).
     */
    fun play(hit: TvHit, onResolved: (url: String, title: String) -> Unit) {
        if (_playing.value != null) return
        val base = proxyBase.value
        if (base.isBlank()) {
            _playError.value = "尚未配置后端地址"
            return
        }
        _playing.value = hit.hit_id
        _playError.value = null
        viewModelScope.launch {
            try {
                val resp = repo.play(base, hit.hit_id)
                if (resp.url.isBlank()) {
                    _playError.value = "后端返回空 URL"
                } else {
                    val titleForPlayer = resp.title.ifBlank { hit.title }
                    onResolved(resp.url, titleForPlayer)
                }
            } catch (t: Throwable) {
                _playError.value = t.message ?: t::class.java.simpleName
            } finally {
                _playing.value = null
            }
        }
    }

    fun clearPlayError() { _playError.value = null }

    /** Convenience: also feed a query (e.g. from Explore) and immediately run it. */
    fun runWith(word: String) {
        _query.value = word
        submit()
    }
}
