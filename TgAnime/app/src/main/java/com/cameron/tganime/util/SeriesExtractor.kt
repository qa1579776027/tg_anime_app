package com.cameron.tganime.util

/**
 * Extracts a clean series title from an acgn.es channel caption so the search
 * page can group hits by series and look up posters on bgm.tv.
 *
 * Strategy (in order):
 *   1. Strip known media file extensions.
 *   2. Strip a leading `[Group]` bracket prefix (e.g. "[ANi]", "[LoliHouse]").
 *   3. Strip a leading "Group |" pipe prefix (e.g. "ANi | …", "NEP.Anime | …").
 *   4. Cut at the first dash-episode marker (" - 05", " - S01E12").
 *   5. Fall back to cutting at the first Chinese marker ("第 N 集").
 *   6. Strip trailing tag groups like "[1080P Baha WEB-DL AAC]".
 *   7. Trim residual whitespace + punctuation.
 *
 * The returned `key` is a normalized lower-case identifier suitable for
 * grouping ("re：从零開始的異世界生活 第四季" ≈ "re从零开始的异世界生活第四季").
 */
object SeriesExtractor {

    private val EXT_TAIL = Regex("\\.(?:mkv|mp4|ts|m2ts|avi|mov|webm|flv|m3u8)$", RegexOption.IGNORE_CASE)
    private val LEADING_BRACKET = Regex("^\\[[^]]+]\\s*")
    private val LEADING_PIPE = Regex("^[^|\\[]{1,40}\\|\\s*")
    private val DASH_EP_CUT = Regex(
        "\\s*-\\s*(?:S\\d{1,2}E)?\\d{1,3}(?:v\\d+)?(?=\\s|\\[|\\(|$)",
        RegexOption.IGNORE_CASE,
    )
    private val CN_EP_CUT = Regex("\\s*第\\s*\\d{1,3}\\s*[集话話].*$")
    private val TRAILING_BRACKETS = Regex("\\s*[\\[(].*$")
    private val TRAILING_TAGS = Regex(
        "\\s*[|·•]\\s*(?:\\d{1,3}(?:v\\d+)?|1080p|720p|2160p|4K|WEB-?DL|BDRip|HEVC|AVC|AAC|FLAC|H\\.?26[45]|x26[45]|CHT|CHS|BIG5|GB|简中|繁中|内嵌|内封|双语).*$",
        RegexOption.IGNORE_CASE,
    )
    private val MULTI_SPACE = Regex("\\s+")

    /** Display name (preserves case + non-ASCII chars). */
    fun title(raw: String): String {
        if (raw.isBlank()) return ""
        var s = raw.replace('\u3000', ' ').replace('\n', ' ')
        s = EXT_TAIL.replace(s, "")
        s = LEADING_BRACKET.replace(s, "")

        // Strip leading "Group |" only if the head looks like an encoder
        // (no brackets, no long digit runs — same heuristic as EpisodeParser).
        val pipeMatch = LEADING_PIPE.find(s)
        if (pipeMatch != null) {
            val head = pipeMatch.value
            if (!head.contains('[') && !Regex("\\d{2,}").containsMatchIn(head)) {
                s = s.substring(pipeMatch.range.last + 1)
            }
        }

        // Cut at episode marker.
        DASH_EP_CUT.find(s)?.let { s = s.substring(0, it.range.first) }
        CN_EP_CUT.find(s)?.let { s = s.substring(0, it.range.first) }

        // Strip trailing pipe-segment tags ("| 1080p | …", "| 26v2 | …").
        TRAILING_TAGS.find(s)?.let { s = s.substring(0, it.range.first) }

        // Strip trailing bracket groups ("(Baha 1080p)", "[CHT]").
        TRAILING_BRACKETS.find(s)?.let { s = s.substring(0, it.range.first) }

        return MULTI_SPACE.replace(s, " ").trim().trim('-', '–', '—', '|', '·', '•').trim()
    }

    /** Normalized grouping key — lower-cased, punctuation/spacing stripped. */
    fun key(title: String): String {
        if (title.isBlank()) return ""
        val lower = title.lowercase()
        val out = StringBuilder()
        for (ch in lower) {
            if (ch.isLetterOrDigit()) out.append(ch)
        }
        return out.toString()
    }
}
