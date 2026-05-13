package com.cameron.tganime.util

/** Parse `https://t.me/<channel>/<msg_id>` into (channel, msgId). */
object TmeLinkParser {
    private val RE = Regex("^https?://t\\.me/(?<ch>[^/]+)/(?<id>\\d+)")

    fun parse(link: String?): Pair<String, Long>? {
        if (link.isNullOrBlank()) return null
        val m = RE.find(link) ?: return null
        val ch = m.groups["ch"]?.value ?: return null
        val id = m.groups["id"]?.value?.toLongOrNull() ?: return null
        return ch to id
    }
}

/** Format bytes as a short human-readable size (1.5GB / 678MB). */
fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var u = 0
    while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
    return "%.1f%s".format(v, units[u])
}
