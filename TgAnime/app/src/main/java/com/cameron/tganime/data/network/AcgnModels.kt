package com.cameron.tganime.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response envelope from search.acgn.es/api/.
 * Confirmed via probe: { code: 0, data: [...] } on success.
 */
@Serializable
data class AcgnResponse(
    val code: Int = 0,
    val data: List<AcgnHit> = emptyList(),
    val message: String? = null,
)

@Serializable
data class AcgnHit(
    val id: Long = 0,
    @SerialName("channel_id") val channelId: Long = 0,
    @SerialName("channel_name") val channelName: String = "",
    val size: Long = 0,
    val text: String = "",
    @SerialName("file_suffix") val fileSuffix: String = "",
    @SerialName("msg_id") val msgId: Long = 0,
    @SerialName("supports_streaming") val supportsStreaming: Boolean = false,
    /** Telegram public URL: https://t.me/<channel>/<msg_id>. We parse channel + msg_id from this. */
    val link: String = "",
    val date: Long = 0,
)
