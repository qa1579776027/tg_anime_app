package com.cameron.tganime.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Responses from the tg_anime backend's /openlist routes (see
 * tg_anime/tv_router.py). They proxy the OpenList / AList v3 HTTP API.
 *
 * Endpoints:
 *
 *     GET /openlist/list?path=/quark&refresh=false
 *     GET /openlist/url?path=/quark/foo/bar.mp4
 */
@Serializable
data class OpenListResp(
    val path: String = "",
    val entries: List<OpenListEntry> = emptyList(),
)

@Serializable
data class OpenListEntry(
    val name: String = "",
    val size: Long = 0,
    @SerialName("is_dir") val isDir: Boolean = false,
    val modified: String = "",
)

@Serializable
data class OpenListUrlResp(
    val path: String = "",
    val url: String = "",
)
