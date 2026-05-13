package com.cameron.tganime.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cameron.tganime.TgAnimeApp
import com.cameron.tganime.data.network.BgmCalendarDay
import com.cameron.tganime.data.repo.DiscoverRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DiscoverState {
    data object Loading : DiscoverState
    data class Loaded(val days: List<BgmCalendarDay>) : DiscoverState
    data class Failed(val msg: String) : DiscoverState
}

class DiscoverViewModel(
    private val repo: DiscoverRepository = TgAnimeApp.get().discoverRepo,
) : ViewModel() {

    private val _state = MutableStateFlow<DiscoverState>(DiscoverState.Loading)
    val state: StateFlow<DiscoverState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = DiscoverState.Loading
        viewModelScope.launch {
            try {
                _state.value = DiscoverState.Loaded(repo.loadCalendar())
            } catch (t: Throwable) {
                _state.value = DiscoverState.Failed(t.message ?: t::class.java.simpleName)
            }
        }
    }
}
