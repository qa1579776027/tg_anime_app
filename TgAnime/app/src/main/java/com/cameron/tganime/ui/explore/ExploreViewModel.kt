package com.cameron.tganime.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cameron.tganime.TgAnimeApp
import com.cameron.tganime.data.network.BgmSubject
import com.cameron.tganime.data.prefs.SettingsStore
import com.cameron.tganime.data.prefs.WatchEntry
import com.cameron.tganime.data.repo.DiscoverRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ExploreState {
    data object Loading : ExploreState
    data class Loaded(
        val hot: List<BgmSubject>,
        val recommended: List<BgmSubject>,
    ) : ExploreState
    data class Failed(val msg: String) : ExploreState
}

/**
 * 探索页数据。一次性拉取 bgm.tv 本季周历,然后按评分 / 排名计算两个分段:
 *
 *  - 最高热度: 按评分 desc + 评分人数加权,取前 10
 *  - 推荐:     评分 >= 6 的剩余条目, 按评分 desc
 */
class ExploreViewModel(
    private val repo: DiscoverRepository = TgAnimeApp.get().discoverRepo,
    private val settings: SettingsStore = TgAnimeApp.get().settings,
) : ViewModel() {

    private val _state = MutableStateFlow<ExploreState>(ExploreState.Loading)
    val state: StateFlow<ExploreState> = _state.asStateFlow()

    val watchList: StateFlow<List<WatchEntry>> = settings.watchListFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { refresh() }

    fun refresh() {
        _state.value = ExploreState.Loading
        viewModelScope.launch {
            try {
                val all = repo.loadCalendar().flatMap { it.items }.distinctBy { it.id }
                val ranked = all.sortedWith(
                    compareByDescending<BgmSubject> { (it.rating?.score ?: 0.0) }
                        .thenByDescending { it.rating?.total ?: 0 }
                )
                val hot = ranked.take(10)
                val hotIds = hot.map { it.id }.toSet()
                val recommended = ranked.filter { it.id !in hotIds }
                _state.value = ExploreState.Loaded(hot = hot, recommended = recommended)
            } catch (t: Throwable) {
                _state.value = ExploreState.Failed(t.message ?: t::class.java.simpleName)
            }
        }
    }

    fun toggleWatching(subject: BgmSubject) {
        val entry = WatchEntry(
            id = subject.id,
            name = subject.name,
            nameCn = subject.nameCn,
            image = subject.images.medium.ifBlank {
                subject.images.common.ifBlank { subject.images.large }
            },
            addedAt = System.currentTimeMillis(),
        )
        viewModelScope.launch { settings.toggleWatching(entry) }
    }
}
