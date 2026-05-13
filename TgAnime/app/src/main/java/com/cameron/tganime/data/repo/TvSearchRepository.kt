package com.cameron.tganime.data.repo

import com.cameron.tganime.data.network.TvPlayRequest
import com.cameron.tganime.data.network.TvPlayResponse
import com.cameron.tganime.data.network.TvSearchResponse
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Talks to the user-configured tg_anime backend's TV routes:
 * `GET /tv/search?kw=&offset=&page_size=&refresh=` and `POST /tv/play`.
 *
 * The backend keeps a per-keyword session cached for ~5 minutes and pages
 * through `offset`; the frontend just appends hits to its existing list.
 */
class TvSearchRepository(
    private val client: OkHttpClient,
    private val json: Json,
) {

    suspend fun search(
        proxyBase: String,
        kw: String,
        offset: Int = 0,
        pageSize: Int = 6,
        refresh: Boolean = false,
    ): TvSearchResponse {
        val base = proxyBase.trim().trimEnd('/')
        if (base.isEmpty()) throw IllegalStateException("backend URL not configured")
        val url = "$base/tv/search".toHttpUrlOrNull()
            ?: throw IllegalStateException("bad backend URL: $base")
        val req = Request.Builder()
            .url(
                url.newBuilder()
                    .addQueryParameter("kw", kw)
                    .addQueryParameter("offset", offset.toString())
                    .addQueryParameter("page_size", pageSize.toString())
                    .addQueryParameter("refresh", refresh.toString())
                    .build()
            )
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("tv/search ${resp.code}: ${body.take(200)}")
            }
            return json.decodeFromString(TvSearchResponse.serializer(), body)
        }
    }

    suspend fun play(proxyBase: String, hitId: Long): TvPlayResponse {
        val base = proxyBase.trim().trimEnd('/')
        if (base.isEmpty()) throw IllegalStateException("backend URL not configured")
        val url = "$base/tv/play".toHttpUrlOrNull()
            ?: throw IllegalStateException("bad backend URL: $base")
        val body = json.encodeToString(
            TvPlayRequest.serializer(),
            TvPlayRequest(hit_id = hitId),
        )
        val req = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("tv/play ${resp.code}: ${text.take(200)}")
            }
            return json.decodeFromString(TvPlayResponse.serializer(), text)
        }
    }
}
