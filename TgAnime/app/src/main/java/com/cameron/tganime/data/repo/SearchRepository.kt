package com.cameron.tganime.data.repo

import com.cameron.tganime.data.model.EpisodeGroup
import com.cameron.tganime.data.model.EpisodeSource
import com.cameron.tganime.data.model.SeriesGroup
import com.cameron.tganime.data.network.AcgnApi
import com.cameron.tganime.data.network.BgmApi
import com.cameron.tganime.data.network.BgmSubject
import com.cameron.tganime.util.EpisodeParser
import com.cameron.tganime.util.SeriesExtractor
import com.cameron.tganime.util.TmeLinkParser
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * acgn.es page size caps at 24. We page internally when more is requested,
 * matching tg_anime/demo_acgn.py behaviour.
 */
private const val ACGN_PAGE_SIZE = 24

data class SearchResult(
    /** Flat (legacy) episode-bucket view, kept for compatibility. */
    val episodes: List<EpisodeGroup>,
    val unparsed: List<EpisodeSource>,
    val totalSources: Int,
    /** Series-grouped view used by the poster-grid UI. */
    val series: List<SeriesGroup>,
)

class SearchRepository(
    private val api: AcgnApi,
    private val bgmApi: BgmApi? = null,
) {

    /** Cache series-title → bgm.tv lookup result so we don't refetch per search. */
    private val bgmCache = mutableMapOf<String, BgmSubject?>()

    suspend fun search(word: String, limit: Int = 48): SearchResult {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) {
            return SearchResult(emptyList(), emptyList(), 0, emptyList())
        }

        val all = collectSources(trimmed, limit)

        // -------- legacy view: bucket by episode number across the whole result
        val byEpisode = sortedMapOf<Int, MutableList<EpisodeSource>>()
        val unparsed = mutableListOf<EpisodeSource>()
        for (src in all) {
            val ep = EpisodeParser.parse(src.rawText).episode
            if (ep == null) unparsed += src
            else byEpisode.getOrPut(ep) { mutableListOf() } += src
        }
        val flatEpisodes = byEpisode.map { (ep, srcs) -> EpisodeGroup(ep, srcs) }

        // -------- series-grouped view: one bucket per detected series title
        val series = groupBySeries(all)
        val seriesWithPosters = attachPosters(series)

        return SearchResult(
            episodes = flatEpisodes,
            unparsed = unparsed,
            totalSources = all.size,
            series = seriesWithPosters,
        )
    }

    private suspend fun collectSources(word: String, limit: Int): List<EpisodeSource> {
        val all = mutableListOf<EpisodeSource>()
        val seen = mutableSetOf<Pair<String, Long>>()
        var page = 0
        var remaining = limit.coerceAtLeast(1)

        while (remaining > 0) {
            val pageSize = minOf(remaining, ACGN_PAGE_SIZE)
            val resp = api.search(word = word, page = page, limit = pageSize)
            if (resp.code != 0) break
            val hits = resp.data
            if (hits.isEmpty()) break

            for (h in hits) {
                val (channel, msgId) = TmeLinkParser.parse(h.link) ?: continue
                if (!seen.add(channel to msgId)) continue
                val parsed = EpisodeParser.parse(h.text)
                all += EpisodeSource(
                    channel = channel,
                    msgId = msgId,
                    channelName = h.channelName,
                    size = h.size,
                    suffix = h.fileSuffix.ifBlank { "?" },
                    rawText = h.text,
                    quality = parsed.quality,
                    group = parsed.group,
                )
            }

            remaining -= hits.size
            page++
            if (hits.size < pageSize) break
        }
        return all
    }

    private fun groupBySeries(sources: List<EpisodeSource>): List<SeriesGroup> {
        if (sources.isEmpty()) return emptyList()
        data class Bucket(
            val title: String,
            val sources: MutableList<EpisodeSource> = mutableListOf(),
        )
        val buckets = linkedMapOf<String, Bucket>()
        for (src in sources) {
            val title = SeriesExtractor.title(src.rawText)
                .ifBlank { src.rawText.take(40) }
            val key = SeriesExtractor.key(title).ifBlank { title.lowercase() }
            val b = buckets.getOrPut(key) { Bucket(title) }
            b.sources += src
        }

        return buckets.map { (key, b) ->
            val byEp = sortedMapOf<Int, MutableList<EpisodeSource>>()
            val unparsed = mutableListOf<EpisodeSource>()
            for (src in b.sources) {
                val ep = EpisodeParser.parse(src.rawText).episode
                if (ep == null) unparsed += src
                else byEp.getOrPut(ep) { mutableListOf() } += src
            }
            SeriesGroup(
                key = key,
                title = b.title,
                bgmId = null,
                nameCn = "",
                posterUrl = "",
                episodes = byEp.map { (ep, srcs) -> EpisodeGroup(ep, srcs) },
                unparsedSources = unparsed,
                totalSources = b.sources.size,
            )
        }.sortedByDescending { it.totalSources }
    }

    private suspend fun attachPosters(groups: List<SeriesGroup>): List<SeriesGroup> {
        val api = bgmApi ?: return groups
        if (groups.isEmpty()) return groups

        return coroutineScope {
            groups.map { group ->
                async {
                    val cached = bgmCache[group.key]
                    val matched: BgmSubject? = if (cached != null || bgmCache.containsKey(group.key)) {
                        cached
                    } else {
                        val hit = lookupBgm(group.title)
                        bgmCache[group.key] = hit
                        hit
                    }
                    if (matched == null) group
                    else group.copy(
                        bgmId = matched.id,
                        nameCn = matched.nameCn,
                        posterUrl = matched.images.medium.ifBlank {
                            matched.images.common.ifBlank { matched.images.large }
                        },
                    )
                }
            }.map { it.await() }
        }
    }

    private suspend fun lookupBgm(title: String): BgmSubject? {
        val api = bgmApi ?: return null
        val candidates = candidateQueries(title)
        for (q in candidates) {
            if (q.isBlank()) continue
            val resp = runCatching { api.searchSubject(keyword = q) }.getOrNull() ?: continue
            val hit = resp.list.firstOrNull { it.images.medium.isNotBlank() || it.images.common.isNotBlank() }
                ?: resp.list.firstOrNull()
            if (hit != null) return hit
        }
        return null
    }

    /**
     * bgm.tv's search is sensitive to "season N" / "OVA" tails. We try the full
     * title first, then progressively strip Chinese / Japanese season suffixes.
     */
    private fun candidateQueries(title: String): List<String> {
        val out = linkedSetOf<String>()
        out += title
        val seasonRe = Regex("\\s*(?:第[一二三四五六七八九十0-9]+季|Season\\s*\\d+|S\\d+|II|III|IV|V|VI|VII|VIII|IX|X)\\s*$", RegexOption.IGNORE_CASE)
        val stripped = seasonRe.replace(title, "").trim()
        if (stripped.isNotBlank() && stripped != title) out += stripped
        // First "word" / phrase before a colon — handles "Re：从零开始..." → "Re"
        val colonIdx = title.indexOfFirst { it == '：' || it == ':' }
        if (colonIdx > 1) {
            val head = title.substring(0, colonIdx).trim()
            if (head.length >= 2) out += head
        }
        return out.toList()
    }
}
