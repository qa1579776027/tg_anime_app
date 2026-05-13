package com.cameron.tganime.data.network

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

class NetworkModule(
    val baseHttpClient: OkHttpClient = defaultHttpClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }
    val jsonFormat: Json get() = json

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
     * Build a fresh [MediaBackendApi] pointing at [baseUrl] with [token] as the
     * Bearer credential. We don't cache this because both base URL and token
     * are user-configurable in Settings and may change at runtime.
     *
     * [baseUrl] must end with `/` (Retrofit requirement). Trailing slash is
     * added if missing.
     */
    fun mediaBackendApi(baseUrl: String, token: String): MediaBackendApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(
                baseHttpClient.newBuilder()
                    .addInterceptor(bearerInterceptor(token))
                    .build()
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MediaBackendApi::class.java)
    }

    private fun bearerInterceptor(token: String) = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .build()
        chain.proceed(req)
    }
}

private fun defaultHttpClient(): OkHttpClient {
    val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
    return OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()
}
