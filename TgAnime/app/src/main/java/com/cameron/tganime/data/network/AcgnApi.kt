package com.cameron.tganime.data.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * search.acgn.es — public Telegram channel index. Page size caps at 24
 * (anything bigger 400s). We paginate inside the repository if needed.
 */
interface AcgnApi {
    @GET("api/")
    suspend fun search(
        @Query("word") word: String,
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 24,
        @Query("cid") cid: Int = 0,
        @Query("sort") sort: String = "time",
        @Query("file_suffix") fileSuffix: String = "",
    ): AcgnResponse
}
