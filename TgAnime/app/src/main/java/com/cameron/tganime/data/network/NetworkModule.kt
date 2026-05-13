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
