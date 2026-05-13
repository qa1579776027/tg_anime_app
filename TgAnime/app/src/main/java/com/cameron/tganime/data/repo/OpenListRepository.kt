package com.cameron.tganime.data.repo

import com.cameron.tganime.data.network.OpenListEntry
import com.cameron.tganime.data.network.OpenListResp
import com.cameron.tganime.data.network.OpenListUrlResp
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

/**
 * Talks to the user-configured tg_anime backend (`proxyBase` from settings).
 * The /openlist routes are exposed by tg_anime/tv_router.py and proxy OpenList
 * / AList. No auth on our side — the backend holds the AList token.
 */
class OpenListRepository(
    private val client: OkHttpClient,
    private val json: Json,
) {

    suspend fun list(proxyBase: String, path: String, refresh: Boolean = false): List<OpenListEntry> {
        val base = proxyBase.trim().trimEnd('/')
        if (base.isEmpty()) throw IllegalStateException("backend URL not configured")
        val url = "$base/openlist/list".toHttpUrlOrNull()
            ?: throw IllegalStateException("bad backend URL: $base")
        val req = Request.Builder()
            .url(
                url.newBuilder()
                    .addQueryParameter("path", path.ifBlank { "/" })
                    .addQueryParameter("refresh", refresh.toString())
                    .build()
            )
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("openlist list ${resp.code}: ${body.take(200)}")
            }
            return json.decodeFromString(OpenListResp.serializer(), body).entries
        }
    }

    suspend fun resolveUrl(proxyBase: String, path: String): String {
        val base = proxyBase.trim().trimEnd('/')
        if (base.isEmpty()) throw IllegalStateException("backend URL not configured")
        val url = "$base/openlist/url".toHttpUrlOrNull()
            ?: throw IllegalStateException("bad backend URL: $base")
        val req = Request.Builder()
            .url(url.newBuilder().addQueryParameter("path", path).build())
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("openlist url ${resp.code}: ${body.take(200)}")
            }
            return json.decodeFromString(OpenListUrlResp.serializer(), body).url
        }
    }
}
