package com.cameron.tganime.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cameron.tganime.TgAnimeApp
import com.cameron.tganime.data.prefs.SettingsStore
import com.cameron.tganime.data.prefs.WatchEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WatchListViewModel(
    private val settings: SettingsStore = TgAnimeApp.get().settings,
) : ViewModel() {

    val watchList: StateFlow<List<WatchEntry>> = settings.watchListFlow
        .map { it.sortedByDescending(WatchEntry::addedAt) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(id: Long) {
        viewModelScope.launch { settings.removeWatching(id) }
    }
}
