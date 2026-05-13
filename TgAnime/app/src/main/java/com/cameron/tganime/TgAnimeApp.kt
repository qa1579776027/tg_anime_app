package com.cameron.tganime

import android.app.Application
import com.cameron.tganime.data.network.NetworkModule
import com.cameron.tganime.data.prefs.SettingsStore
import com.cameron.tganime.data.repo.DiscoverRepository
import com.cameron.tganime.data.repo.MoviesRepository
import com.cameron.tganime.data.repo.OpenListRepository
import com.cameron.tganime.data.repo.SearchRepository
import com.cameron.tganime.data.repo.TvSearchRepository

/**
 * Tiny manual DI container. We don't pull Hilt in — Application + lazy singletons
 * are more than enough for this app and let the user build without KSP/KAPT.
 */
class TgAnimeApp : Application() {
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val network: NetworkModule by lazy { NetworkModule() }
    val searchRepo: SearchRepository by lazy { SearchRepository(network.acgnApi, network.bgmApi) }
    val discoverRepo: DiscoverRepository by lazy { DiscoverRepository(network.bgmApi) }
    val openListRepo: OpenListRepository by lazy {
        OpenListRepository(network.baseHttpClient, network.jsonFormat)
    }
    val tvSearchRepo: TvSearchRepository by lazy {
        TvSearchRepository(network.baseHttpClient, network.jsonFormat)
    }
    val moviesRepo: MoviesRepository by lazy { MoviesRepository(network, settings) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile private var instance: TgAnimeApp? = null
        fun get(): TgAnimeApp = instance ?: error("TgAnimeApp not yet created")
    }
}
