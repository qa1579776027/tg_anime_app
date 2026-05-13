package com.cameron.tganime.ui.cache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cameron.tganime.TgAnimeApp
import com.cameron.tganime.data.network.OpenListEntry
import com.cameron.tganime.data.prefs.SettingsStore
import com.cameron.tganime.data.repo.OpenListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CacheState {
    data object Idle : CacheState
    data object Loading : CacheState
    data class Loaded(val path: String, val entries: List<OpenListEntry>) : CacheState
    data class Failed(val msg: String) : CacheState
}

class CacheViewModel(
    private val repo: OpenListRepository = TgAnimeApp.get().openListRepo,
    private val settings: SettingsStore = TgAnimeApp.get().settings,
) : ViewModel() {

    private val _state = MutableStateFlow<CacheState>(CacheState.Idle)
    val state: StateFlow<CacheState> = _state.asStateFlow()

    private val _path = MutableStateFlow("/")
    val path: StateFlow<String> = _path.asStateFlow()

    val proxyBase: StateFlow<String> = settings.proxyBaseFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun load(targetPath: String = _path.value, refresh: Boolean = false) {
        val base = proxyBase.value
        if (base.isBlank()) {
            _state.value = CacheState.Idle
            return
        }
        _path.value = targetPath
        _state.value = CacheState.Loading
        viewModelScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) {
                    repo.list(base, targetPath, refresh = refresh)
                }
                _state.value = CacheState.Loaded(targetPath, entries)
            } catch (t: Throwable) {
                _state.value = CacheState.Failed(t.message ?: t::class.java.simpleName)
            }
        }
    }

    /** Resolve a file path to a streamable URL. Returns null if not resolved. */
    suspend fun resolveUrl(filePath: String): String? {
        val base = proxyBase.value
        if (base.isBlank()) return null
        return runCatching {
            withContext(Dispatchers.IO) { repo.resolveUrl(base, filePath) }
        }.getOrNull()
    }

    fun parentOf(path: String): String {
        if (path == "/" || path.isBlank()) return "/"
        val trimmed = path.trimEnd('/')
        val idx = trimmed.lastIndexOf('/')
        if (idx <= 0) return "/"
        return trimmed.substring(0, idx)
    }
}
