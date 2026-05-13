package com.cameron.tganime.data.network

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

/**
 * Hosts the OkHttp ProxyRewrite interceptor knows about.
 *
 * api.bgm.tv     → ${proxyBase}/api/bgm/<path>
 * image.tmdb.org → ${proxyBase}/api/img?u=<original>
 * lain.bgm.tv    → ${proxyBase}/api/img?u=<original>
 * bangumi-image.bangumi.tv → same
 *
 * When proxyBase is blank, the interceptor is a no-op and requests go to the
 * origin directly. This keeps Explore working before the user configures the
 * tg_anime backend.
 */
private const val HOST_BGM_API = "api.bgm.tv"
private val IMAGE_HOSTS = setOf("image.tmdb.org", "lain.bgm.tv", "bangumi-image.bangumi.tv")

/**
 * Container for the user-configured tg_anime backend ("yiti") base URL.
 * Updated from a coroutine in [com.cameron.tganime.TgAnimeApp] that collects
 * [com.cameron.tganime.data.prefs.SettingsStore.proxyBaseFlow]. Read on every
 * outbound request by [ProxyRewriteInterceptor].
 *
 * AtomicReference so reads/writes are safe across threads without locking.
 */
class ProxyBaseHolder(initial: String = "") {
    private val ref = AtomicReference(normalize(initial))
    fun set(value: String) { ref.set(normalize(value)) }
    fun get(): String = ref.get()
    private fun normalize(v: String): String = v.trim().trimEnd('/')
}

/**
 * Rewrite outbound requests so that bgm.tv and TMDB/bangumi image hosts are
 * served via the tg_anime backend ("yiti"). See [HOST_BGM_API] / [IMAGE_HOSTS]
 * for the host list.
 *
 * Implementation note: we rewrite the request URL and let OkHttp do everything
 * else (DNS, TLS, keep-alive). The downstream service is expected to expose
 * `/api/bgm/<path>` and `/api/img?u=<url>`.
 */
class ProxyRewriteInterceptor(private val holder: ProxyBaseHolder) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val req = chain.request()
        val rewritten = rewrite(req.url) ?: return chain.proceed(req)
        return chain.proceed(req.newBuilder().url(rewritten).build())
    }

    private fun rewrite(url: HttpUrl): HttpUrl? {
        val base = holder.get()
        if (base.isEmpty()) return null
        val baseUrl = base.toHttpUrlOrNull() ?: return null
        val host = url.host

        if (host.equals(HOST_BGM_API, ignoreCase = true)) {
            // /calendar               -> /api/bgm/calendar
            // /v0/subjects/302303     -> /api/bgm/v0/subjects/302303
            val builder = baseUrl.newBuilder()
                .addPathSegment("api")
                .addPathSegment("bgm")
            for (seg in url.pathSegments) {
                if (seg.isNotEmpty()) builder.addPathSegment(seg)
            }
            for (name in url.queryParameterNames) {
                for (value in url.queryParameterValues(name)) {
                    if (value != null) builder.addQueryParameter(name, value)
                }
            }
            return builder.build()
        }

        if (host.lowercase() in IMAGE_HOSTS) {
            return baseUrl.newBuilder()
                .addPathSegment("api")
                .addPathSegment("img")
                .addQueryParameter("u", url.toString())
                .build()
        }

        return null
    }
}

class NetworkModule(
    val proxyBaseHolder: ProxyBaseHolder = ProxyBaseHolder(),
    val baseHttpClient: OkHttpClient = defaultHttpClient(proxyBaseHolder),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }
    val jsonFormat: Json get() = json

    /**
     * acgn.es is *not* proxied through yiti — there's no `/api/acgn` route on
     * the backend and it's already on a reasonably fast CDN. If we ever want to
     * proxy it too, add a host case to [ProxyRewriteInterceptor].
     */
    val acgnApi: AcgnApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://search.acgn.es/")
            .client(
                baseHttpClient.newBuilder()
                    .addInterceptor(refererInterceptor("https://search.acgn.es/"))
                    .build()
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AcgnApi::class.java)
    }

    /**
     * bgm.tv client. Retrofit's [baseUrl] is `https://api.bgm.tv/` but
     * [ProxyRewriteInterceptor] silently rewrites every request to go through
     * `${proxyBase}/api/bgm/...` when [proxyBaseHolder] is non-empty. So this
     * single client handles both "no proxy configured" (direct) and "proxy
     * configured" (single-host) without callers caring.
     */
    val bgmApi: BgmApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.bgm.tv/")
            .client(
                baseHttpClient.newBuilder()
                    .addInterceptor(refererInterceptor("https://bgm.tv/"))
                    .build()
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BgmApi::class.java)
    }

    private fun refererInterceptor(referer: String) = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("Referer", referer)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        chain.proceed(req)
    }

    /**
     * Build a Retrofit client for the tg_anime backend ("yiti") at [proxyBase].
     * Used by the Movies screen to hit `/api/tmdb/list`. No auth — yiti doesn't
     * gate the read endpoints (it's expected to live on a private LAN /
     * Tailscale, with an external reverse proxy adding auth if exposed).
     */
    fun mediaBackendApi(proxyBase: String): MediaBackendApi {
        val trimmed = proxyBase.trim().trimEnd('/')
        require(trimmed.isNotEmpty()) { "proxyBase is empty; configure it in Settings first" }
        return Retrofit.Builder()
            .baseUrl("$trimmed/")
            .client(baseHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MediaBackendApi::class.java)
    }
}

private fun defaultHttpClient(proxyBaseHolder: ProxyBaseHolder): OkHttpClient {
    val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
    return OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        // Rewrite first so logging / Referer / etc. all see the final URL.
        .addInterceptor(ProxyRewriteInterceptor(proxyBaseHolder))
        .addInterceptor(logging)
        .build()
}
