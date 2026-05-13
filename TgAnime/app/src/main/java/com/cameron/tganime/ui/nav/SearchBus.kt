package com.cameron.tganime.ui.nav

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One-shot hand-off bus between tabs. The Discover screen pushes a query here
 * when the user taps a bgm.tv poster; the Search screen's ViewModel collects
 * from this and immediately runs the search.
 *
 * Using a SharedFlow (replay = 0) means re-subscription doesn't re-trigger an
 * old query.
 */
object SearchBus {
    private val _events = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun push(query: String) {
        if (query.isBlank()) return
        _events.tryEmit(query)
    }
}
