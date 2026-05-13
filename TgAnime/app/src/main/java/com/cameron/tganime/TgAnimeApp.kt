package com.cameron.tganime

import android.app.Application
import coil.Coil
import coil.ImageLoader
import com.cameron.tganime.data.network.NetworkModule
import com.cameron.tganime.data.prefs.SettingsStore
import com.cameron.tganime.data.repo.DiscoverRepository
import com.cameron.tganime.data.repo.MoviesRepository
import com.cameron.tganime.data.repo.OpenListRepository
import com.cameron.tganime.data.repo.SearchRepository
import com.cameron.tganime.data.repo.TvSearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Force the (lazy) NetworkModule to materialise now so we have a stable
        // OkHttp client + ProxyBaseHolder reference for both Coil and the
        // proxyBase collector below.
        val net = network

        // Configure Coil's app-wide ImageLoader to use the same OkHttp client
        // as the rest of the app. This is what lets ProxyRewriteInterceptor
        // also rewrite image.tmdb.org / lain.bgm.tv image requests through
        // ${proxyBase}/api/img.
        Coil.setImageLoader {
            ImageLoader.Builder(this)
                .okHttpClient(net.baseHttpClient)
                .build()
        }

        // Keep ProxyRewriteInterceptor's view of proxyBase in sync with the
        // DataStore-backed setting.
        settings.proxyBaseFlow
            .distinctUntilChanged()
            .onEach { net.proxyBaseHolder.set(it) }
            .launchIn(appScope)
    }

    companion object {
        @Volatile private var instance: TgAnimeApp? = null
        fun get(): TgAnimeApp = instance ?: error("TgAnimeApp not yet created")
    }
}
