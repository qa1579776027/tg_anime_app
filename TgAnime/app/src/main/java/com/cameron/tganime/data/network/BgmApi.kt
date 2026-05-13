package com.cameron.tganime.data.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Bangumi.tv public API.
 *
 *  - `/calendar`                — current season's broadcast (one bucket per weekday)
 *  - `/search/subject/{kw}`     — legacy keyword search; returns BgmSearchResponse
 *
 * No auth required for either endpoint. The legacy search route is used to
 * resolve series → poster mapping for the App's search page.
 */
interface BgmApi {
    @GET("calendar")
    suspend fun calendar(): List<BgmCalendarDay>

    @GET("search/subject/{keyword}")
    suspend fun searchSubject(
        @Path(value = "keyword", encoded = false) keyword: String,
        @Query("type") type: Int = 2, // 2 = anime
        @Query("responseGroup") responseGroup: String = "small",
        @Query("max_results") maxResults: Int = 3,
    ): BgmSearchResponse
}
