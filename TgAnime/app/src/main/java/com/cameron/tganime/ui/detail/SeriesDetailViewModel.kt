package com.cameron.tganime.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cameron.tganime.TgAnimeApp
import com.cameron.tganime.data.model.SeriesGroup
import com.cameron.tganime.data.network.BgmSubject
import com.cameron.tganime.data.prefs.SettingsStore
import com.cameron.tganime.data.prefs.WatchEntry
import com.cameron.tganime.data.repo.DiscoverRepository
import com.cameron.tganime.data.repo.SearchRepository
import com.cameron.tganime.ui.nav.PendingSeriesLookup
import com.cameron.tganime.ui.nav.SeriesCache
import com.cameron.tganime.ui.nav.SeriesLookup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Top-level tabs inside the detail page header (详情 / 评论). */
sealed interface DetailTab {
    data object Info : DetailTab
    data object Comments : DetailTab
}

class SeriesDetailViewModel(
    private val settings: SettingsStore = TgAnimeApp.get().settings,
    private val searchRepo: SearchRepository = TgAnimeApp.get().searchRepo,
    private val discoverRepo: DiscoverRepository = TgAnimeApp.get().discoverRepo,
) : ViewModel() {

    private val _group = MutableStateFlow<SeriesGroup?>(null)
    val group: StateFlow<SeriesGroup?> = _group.asStateFlow()

    /**
     * Display reference for the header (title / poster) — populated from
     * [PendingSeriesLookup] before [SearchRepository] returns episodes. Stays
     * usable even when no acgn.es result is found.
     */
    private val _lookup = MutableStateFlow<SeriesLookup?>(null)
    val lookup: StateFlow<SeriesLookup?> = _lookup.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedEpisode = MutableStateFlow<Int?>(null)
    val selectedEpisode: StateFlow<Int?> = _selectedEpisode.asStateFlow()

    /** Index of the active source inside [selectedEpisode]'s sources. */
    private val _selectedSourceIdx = MutableStateFlow(0)
    val selectedSourceIdx: StateFlow<Int> = _selectedSourceIdx.asStateFlow()

    private val _tab = MutableStateFlow<DetailTab>(DetailTab.Info)
    val tab: StateFlow<DetailTab> = _tab.asStateFlow()

    private val _related = MutableStateFlow<List<BgmSubject>>(emptyList())
    val related: StateFlow<List<BgmSubject>> = _related.asStateFlow()

    val proxyBase: StateFlow<String> = settings.proxyBaseFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** True when the bound series matches an entry in the watch list. */
    val isWatching: StateFlow<Boolean> =
        combine(_group, _lookup, settings.watchListFlow) { g, l, list ->
            val id = g?.bgmId ?: l?.bgmId
            id != null && list.any { it.id == id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var bound: String? = null

    fun bind(seriesKey: String) {
        if (bound == seriesKey) return
        bound = seriesKey
        _selectedSourceIdx.value = 0
        _tab.value = DetailTab.Info
        _error.value = null

        val cached = SeriesCache.get(seriesKey)
        if (cached != null) {
            _group.value = cached
            _selectedEpisode.value = cached.episodes.firstOrNull()?.episode
            _lookup.value = SeriesLookup(
                key = cached.key,
                title = cached.title,
                nameCn = cached.nameCn,
                posterUrl = cached.posterUrl,
                bgmId = cached.bgmId,
            )
        } else {
            val lookup = PendingSeriesLookup.get(seriesKey)
            if (lookup != null) {
                _lookup.value = lookup
                loadFromTitle(lookup)
            } else {
                _error.value = "找不到该番剧。"
            }
        }
        loadRelated()
    }

    private fun loadFromTitle(lookup: SeriesLookup) {
        _loading.value = true
        viewModelScope.launch {
            try {
                val result = searchRepo.search(lookup.title, limit = 48)
                // The user clicked a *specific* anime (lookup); acgn.es's first
                // series match might be a different season / related show, so
                // we override its display fields (title/nameCn/poster) with the
                // user's clicked data and only adopt its episodes + sources.
                val match = pickBestMatch(result.series, lookup)
                val merged = if (match != null) {
                    match.copy(
                        key = lookup.key,
                        title = lookup.title.ifBlank { match.title },
                        nameCn = lookup.nameCn.ifBlank { match.nameCn },
                        posterUrl = lookup.posterUrl.ifBlank { match.posterUrl },
                        bgmId = lookup.bgmId ?: match.bgmId,
                    )
                } else {
                    // Stub group so the header (title + poster) still renders
                    // even when acgn returns nothing.
                    SeriesGroup(
                        key = lookup.key,
                        title = lookup.title,
                        bgmId = lookup.bgmId,
                        nameCn = lookup.nameCn,
                        posterUrl = lookup.posterUrl,
                        episodes = emptyList(),
                        unparsedSources = emptyList(),
                        totalSources = 0,
                    )
                }
                SeriesCache.put(merged)
                _group.value = merged
                _selectedEpisode.value = merged.episodes.firstOrNull()?.episode
            } catch (t: Throwable) {
                _error.value = t.message ?: t::class.java.simpleName
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Pick the series whose title most closely matches what the user clicked.
     * Prefers exact substring match (either direction); falls back to first.
     */
    private fun pickBestMatch(
        candidates: List<SeriesGroup>,
        lookup: SeriesLookup,
    ): SeriesGroup? {
        if (candidates.isEmpty()) return null
        val targets = listOf(lookup.nameCn, lookup.title)
            .filter { it.isNotBlank() }
            .map { it.lowercase().trim() }
        if (targets.isEmpty()) return candidates.first()
        // Exact bgm id match wins.
        if (lookup.bgmId != null) {
            candidates.firstOrNull { it.bgmId == lookup.bgmId }?.let { return it }
        }
        // Then substring match in either direction.
        val substring = candidates.firstOrNull { c ->
            val pool = listOf(c.title, c.nameCn).filter { it.isNotBlank() }.map { it.lowercase() }
            pool.any { p -> targets.any { t -> p.contains(t) || t.contains(p) } }
        }
        return substring ?: candidates.first()
    }

    private fun loadRelated() {
        viewModelScope.launch {
            try {
                val all = discoverRepo.loadCalendar()
                    .flatMap { it.items }
                    .distinctBy { it.id }
                val currentBgmId = _lookup.value?.bgmId
                _related.value = all
                    .filter { it.id != currentBgmId }
                    .sortedByDescending { it.rating?.score ?: 0.0 }
                    .take(8)
            } catch (_: Throwable) {
                // related is non-critical; ignore failures.
            }
        }
    }

    fun selectEpisode(episode: Int) {
        _selectedEpisode.value = episode
        _selectedSourceIdx.value = 0
    }

    fun selectSource(idx: Int) {
        _selectedSourceIdx.value = idx
    }

    fun setTab(t: DetailTab) {
        _tab.value = t
    }

    fun toggleWatching() {
        val g = _group.value
        val l = _lookup.value
        val id = g?.bgmId ?: l?.bgmId ?: return
        val title = g?.title?.ifBlank { l?.title }?.ifBlank { "" } ?: l?.title ?: return
        val nameCn = g?.nameCn?.ifBlank { l?.nameCn } ?: l?.nameCn ?: ""
        val poster = g?.posterUrl?.ifBlank { l?.posterUrl } ?: l?.posterUrl ?: ""
        val entry = WatchEntry(
            id = id,
            name = title,
            nameCn = nameCn,
            image = poster,
            addedAt = System.currentTimeMillis(),
        )
        viewModelScope.launch { settings.toggleWatching(entry) }
    }
}
