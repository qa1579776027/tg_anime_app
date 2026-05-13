package com.cameron.tganime.util

/**
 * Episode + quality extraction from acgn.es channel captions.
 *
 * Mirrors the Python tg_anime/parser.py pattern priority so client + backend
 * agree on what counts as a recognized episode:
 *
 *   A.   "[Group] Title - 03 [WebRip 1080p HEVC-10bit AAC][CHT].mkv"
 *   A'.  "ANi | Re：從零開始的異世界生活 第四季 - 05 [1080P Baha WEB-DL AAC AVC CHT]"
 *        (also caption snippets like "...第四季 - 05 [...]" without a leading bracket)
 *   C.   anywhere "Title.S01E12.1080p.WEB-DL.mkv"
 *   B.   Chinese "第 04 集 / 第 12 话 / 第十二话"
 *   Pipe fallback: "<Group> | <Title> | 26v2 | <quality> | <lang>"
 */
data class ParsedEpisode(
    val episode: Int?,
    val quality: List<String>,
    val group: String?,
)

object EpisodeParser {

    // Pattern A — leading "[group] series - (SnnEnn|nn)(v\d)?" anchored at start.
    private val GROUP_BRACKET = Regex(
        "^\\[(?<group>[^]]+)]\\s*" +
            "(?<series>.+?)\\s*-\\s*" +
            "(?:S(?<season>\\d{1,2})E)?(?<ep>\\d{1,3})(?:v\\d+)?" +
            "(?=\\s|\\[|\\(|$)",
        RegexOption.IGNORE_CASE,
    )

    // Pattern A' — "series - (SnnEnn|nn)(v\d)?" without a leading bracket
    // (covers "ANi | Re:... 第四季 - 05 [1080P...]" once the leading bracket
    // pattern fails).
    private val DASH_EPISODE = Regex(
        "^(?<series>.+?)\\s*-\\s*" +
            "(?:S(?<season>\\d{1,2})E)?(?<ep>\\d{1,3})(?:v\\d+)?" +
            "(?=\\s|\\[|\\(|$)",
        RegexOption.IGNORE_CASE,
    )

    // Pattern C — SxxExx anywhere.
    private val SXXEXX = Regex("S(\\d{1,2})E(\\d{1,3})", RegexOption.IGNORE_CASE)

    // Pattern B — Chinese episode marker "第 N 集 / 第 N 话 / 第 N 話".
    private val CN_EPISODE = Regex("第\\s*(\\d{1,3})\\s*[集话話]")

    // Pipe segment that's purely an episode number (with optional v2 etc.).
    private val PIPE_EPISODE = Regex("^(\\d{1,3})(?:v\\d+)?$")

    // Last-resort: trailing 1–3 digit run immediately followed by a hash
    // signature (NEP.Anime "Title #tag   26 D2DCAAA8" captions).
    private val TRAILING_EP = Regex(
        "(?<![\\d.])(?<ep>\\d{1,3})(?=\\s+[A-Fa-f0-9]{6,}\\b|\\s*$)"
    )

    private val KNOWN_EXTS = listOf(
        ".mkv", ".mp4", ".ts", ".m2ts", ".avi", ".mov", ".webm", ".flv",
    )

    private val QUALITY_KEYWORDS = listOf(
        "2160p", "1080p", "720p", "480p",
        "4K", "UHD", "HDR10", "HDR", "DV",
        "REMUX", "BDRip", "WebRip", "WEB-DL", "BluRay", "BD",
        "HEVC", "H.265", "H265", "x265", "H.264", "x264", "AV1", "AVC",
        "AAC", "FLAC", "Opus", "Atmos", "DDP", "DDP5.1", "DTS",
        "10bit",
        "CHS", "CHT", "JPSC", "BIG5", "GB",
        "简中", "繁中", "内嵌", "内封", "双语",
    )

    fun extractQuality(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val upper = text.uppercase()
        val out = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (kw in QUALITY_KEYWORDS) {
            if (upper.contains(kw.uppercase()) && kw !in seen) {
                out += kw
                seen += kw
            }
        }
        return out
    }

    private fun stripExt(name: String): String {
        val low = name.lowercase()
        for (ext in KNOWN_EXTS) {
            if (low.endsWith(ext)) return name.dropLast(ext.length)
        }
        return name
    }

    private fun normalize(name: String): String {
        val collapsed = name.replace('\u3000', ' ').replace('\n', ' ')
        return Regex("\\s+").replace(collapsed, " ").trim()
    }

    /** Lift a "GroupName | rest…" prefix out of a series candidate. */
    private fun pickPipeGroup(series: String): String? {
        val idx = series.indexOf('|')
        if (idx !in 1..40) return null
        val head = series.substring(0, idx).trim()
        if (head.isBlank()) return null
        // Reject pipe heads that look like part of the title (contain a year /
        // long digit run / opening bracket), e.g. "Title 2024 | …".
        if (head.contains('[') || Regex("\\d{2,}").containsMatchIn(head)) return null
        return head
    }

    fun parse(text: String): ParsedEpisode {
        if (text.isBlank()) return ParsedEpisode(null, emptyList(), null)
        val raw = normalize(stripExt(text))
        val quality = extractQuality(raw)

        // A — "[group] series - 03"
        GROUP_BRACKET.find(raw)?.let { m ->
            val ep = m.groups["ep"]?.value?.toIntOrNull()
            val grp = m.groups["group"]?.value?.trim()
            if (ep != null) return ParsedEpisode(ep, quality, grp)
        }

        // A' — "series - 03" (no leading group bracket). Recover an encoder
        // prefix ("ANi | ...") from the series candidate when present so the
        // search-result card still shows a sensible label.
        DASH_EPISODE.find(raw)?.let { m ->
            val ep = m.groups["ep"]?.value?.toIntOrNull()
            if (ep != null) {
                val series = m.groups["series"]?.value?.trim().orEmpty()
                return ParsedEpisode(ep, quality, pickPipeGroup(series))
            }
        }

        // C — SxxExx anywhere.
        SXXEXX.find(raw)?.let { m ->
            val ep = m.groupValues[2].toIntOrNull()
            if (ep != null) return ParsedEpisode(ep, quality, null)
        }

        // B — Chinese "第 N 集 / 第 N 话".
        CN_EPISODE.find(raw)?.let { m ->
            val ep = m.groupValues[1].toIntOrNull()
            if (ep != null) return ParsedEpisode(ep, quality, null)
        }

        // Pipe fallback: "Group | Title | 26v2 | quality | lang".
        val parts = raw.split("|").map { it.trim() }
        if (parts.size >= 3) {
            for (i in 1 until parts.size) {
                PIPE_EPISODE.find(parts[i])?.let {
                    val ep = it.groupValues[1].toIntOrNull()
                    if (ep != null) {
                        return ParsedEpisode(ep, quality, parts[0].ifBlank { null })
                    }
                }
            }
        }

        // Trailing "<digits> <hex-hash>" (NEP.Anime style with no dash).
        TRAILING_EP.find(raw)?.groups?.get("ep")?.value?.toIntOrNull()?.let {
            return ParsedEpisode(it, quality, null)
        }

        return ParsedEpisode(null, quality, null)
    }
}
