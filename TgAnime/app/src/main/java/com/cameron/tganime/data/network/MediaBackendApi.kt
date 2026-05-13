package com.cameron.tganime.data.network

import retrofit2.http.GET
import retrofit2.http.Query

// tg_anime backend ("yiti") /api/tmdb/list route. Used by the 电影 tab.
// No auth — yiti's TMDB routes are read-only and live on private LAN.
interface MediaBackendApi {
    // endpoint: popular | top_rated | now_playing | upcoming | trending
    // kind:     movie | tv | all   (all is only valid for trending)
    // page:     1-based TMDB page
    // window:   day | week         (only used by trending)
    @GET("api/tmdb/list")
    suspend fun list(
        @Query("endpoint") endpoint: String,
        @Query("kind") kind: String = "movie",
        @Query("page") page: Int = 1,
        @Query("window") window: String = "week",
        @Query("lang") lang: String = "zh-CN",
        @Query("region") region: String = "",
    ): TmdbListResponse
}
