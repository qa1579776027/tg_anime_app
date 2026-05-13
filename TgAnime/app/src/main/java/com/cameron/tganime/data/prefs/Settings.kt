package com.cameron.tganime.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("tganime.settings")

/** Slim snapshot of a bgm.tv subject persisted into the local watch-list. */
@Serializable
data class WatchEntry(
    val id: Long,
    val name: String,
    val nameCn: String,
    val image: String,
    /** epoch millis. Sorted desc so newest-added is first. */
    val addedAt: Long,
)

class SettingsStore(private val ctx: Context) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val watchListSer = ListSerializer(WatchEntry.serializer())

    val proxyBaseFlow: Flow<String> =
        ctx.dataStore.data.map { it[PROXY_BASE_KEY].orEmpty() }

    suspend fun setProxyBase(value: String) {
        ctx.dataStore.edit { it[PROXY_BASE_KEY] = value.trim().trimEnd('/') }
    }

    /**
     * 电影 (TMDB) backend — separate from the tg_anime playback proxy.
     * Default points at the public yinshi deployment; user can override
     * in Settings if they're self-hosting.
     */
    val mediaBackendBaseFlow: Flow<String> =
        ctx.dataStore.data.map {
            it[MEDIA_BACKEND_BASE_KEY]?.takeIf { v -> v.isNotBlank() }
                ?: DEFAULT_MEDIA_BACKEND_BASE
        }

    suspend fun setMediaBackendBase(value: String) {
        ctx.dataStore.edit { it[MEDIA_BACKEND_BASE_KEY] = value.trim().trimEnd('/') }
    }

    val mediaBackendTokenFlow: Flow<String> =
        ctx.dataStore.data.map {
            it[MEDIA_BACKEND_TOKEN_KEY]?.takeIf { v -> v.isNotBlank() }
                ?: DEFAULT_MEDIA_BACKEND_TOKEN
        }

    suspend fun setMediaBackendToken(value: String) {
        ctx.dataStore.edit { it[MEDIA_BACKEND_TOKEN_KEY] = value.trim() }
    }

    val watchListFlow: Flow<List<WatchEntry>> =
        ctx.dataStore.data.map { prefs ->
            val raw = prefs[WATCHLIST_JSON_KEY].orEmpty()
            if (raw.isBlank()) emptyList()
            else runCatching { json.decodeFromString(watchListSer, raw) }.getOrDefault(emptyList())
        }

    suspend fun isWatching(id: Long): Boolean =
        readWatchList().any { it.id == id }

    suspend fun addWatching(entry: WatchEntry) {
        ctx.dataStore.edit { prefs ->
            val cur = decode(prefs[WATCHLIST_JSON_KEY])
                .filter { it.id != entry.id } + entry
            prefs[WATCHLIST_JSON_KEY] = json.encodeToString(watchListSer, cur)
        }
    }

    suspend fun removeWatching(id: Long) {
        ctx.dataStore.edit { prefs ->
            val cur = decode(prefs[WATCHLIST_JSON_KEY]).filter { it.id != id }
            prefs[WATCHLIST_JSON_KEY] = json.encodeToString(watchListSer, cur)
        }
    }

    /** Returns the new "watching" state (true = now in list). */
    suspend fun toggleWatching(entry: WatchEntry): Boolean {
        var nowWatching = false
        ctx.dataStore.edit { prefs ->
            val cur = decode(prefs[WATCHLIST_JSON_KEY]).toMutableList()
            val idx = cur.indexOfFirst { it.id == entry.id }
            if (idx >= 0) {
                cur.removeAt(idx)
                nowWatching = false
            } else {
                cur += entry
                nowWatching = true
            }
            prefs[WATCHLIST_JSON_KEY] = json.encodeToString(watchListSer, cur)
        }
        return nowWatching
    }

    private suspend fun readWatchList(): List<WatchEntry> {
        val prefs = ctx.dataStore.data.first()
        return decode(prefs[WATCHLIST_JSON_KEY])
    }

    private fun decode(raw: String?): List<WatchEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(watchListSer, raw) }.getOrDefault(emptyList())
    }

    companion object {
        private val PROXY_BASE_KEY: Preferences.Key<String> =
            stringPreferencesKey("proxy_base")
        private val WATCHLIST_JSON_KEY: Preferences.Key<String> =
            stringPreferencesKey("watchlist_json_v1")
        private val MEDIA_BACKEND_BASE_KEY: Preferences.Key<String> =
            stringPreferencesKey("media_backend_base")
        private val MEDIA_BACKEND_TOKEN_KEY: Preferences.Key<String> =
            stringPreferencesKey("media_backend_token")

        /** Public yinshi deployment, per its README. */
        const val DEFAULT_MEDIA_BACKEND_BASE: String =
            "https://media-backend-nrz6.onrender.com"

        /**
         * Shared Bearer token. Repo is private; rotation is via Render
         * Environment, which silently invalidates this fallback. Users on
         * a self-hosted deployment can override in Settings.
         */
        const val DEFAULT_MEDIA_BACKEND_TOKEN: String =
            "nTHIL9Lysb/1pA3JHDxq8vNMMNOTiOozE0/Ty5JcAPc="
    }
}

/** Build the TG proxy URL for a (channel, msg_id). Trailing slash is tolerated. */
fun buildByLinkUrl(proxyBase: String, channel: String, msgId: Long): String =
    "${proxyBase.trimEnd('/')}/by_link/$channel/$msgId"
