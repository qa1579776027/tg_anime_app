@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cameron.tganime.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cameron.tganime.data.model.EpisodeGroup
import com.cameron.tganime.data.model.EpisodeSource
import com.cameron.tganime.data.model.SeriesGroup
import com.cameron.tganime.data.network.BgmSubject
import com.cameron.tganime.data.prefs.buildByLinkUrl
import com.cameron.tganime.ui.nav.SeriesLookup
import com.cameron.tganime.util.formatSize

/**
 * Series detail page. Reached by tapping a poster on Explore / WatchList /
 * Calendar / Search results — the chosen item is stashed in
 * [com.cameron.tganime.ui.nav.PendingSeriesLookup] (or
 * [com.cameron.tganime.ui.nav.SeriesCache] for search results) and looked up
 * by key here.
 *
 *   ┌────────────────────────┐
 *   │ poster (16:9)    [⛶]   │
 *   ├────────────────────────┤
 *   │ 详情 │ 评论   [发送弹幕]│
 *   ├────────────────────────┤
 *   │ Series Title    ♡      │
 *   │ E01 · 7 sources [↗][↓] │
 *   │ 数据源: ANi    [⇄ 更换]│
 *   │ 剧集列表 [01][02]…     │
 *   │ 相关推荐               │
 *   └────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesKey: String,
    onBack: () -> Unit,
    onPlay: (url: String, title: String) -> Unit,
    onOpenSubject: ((BgmSubject) -> Unit)? = null,
    vm: SeriesDetailViewModel = viewModel(),
) {
    val group by vm.group.collectAsStateWithLifecycle()
    val lookup by vm.lookup.collectAsStateWithLifecycle()
    val proxyBase by vm.proxyBase.collectAsStateWithLifecycle()
    val watching by vm.isWatching.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val selectedEp by vm.selectedEpisode.collectAsStateWithLifecycle()
    val selectedSourceIdx by vm.selectedSourceIdx.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val related by vm.related.collectAsStateWithLifecycle()

    LaunchedEffect(seriesKey) { vm.bind(seriesKey) }

    BackHandler { onBack() }

    val titleText = group?.nameCn?.ifBlank { group?.title }?.ifBlank { lookup?.nameCn }?.ifBlank { lookup?.title }
        ?: lookup?.nameCn?.ifBlank { lookup?.title }
        ?: ""
    val posterUrl = group?.posterUrl?.ifBlank { lookup?.posterUrl } ?: lookup?.posterUrl ?: ""

    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        PosterHeader(
            posterUrl = posterUrl,
            onBack = onBack,
        )

        TabsAndDanmakuRow(
            selected = tab,
            onSelect = vm::setTab,
        )

        when (tab) {
            DetailTab.Info -> InfoTab(
                title = titleText,
                group = group,
                lookup = lookup,
                loading = loading,
                error = error,
                watching = watching,
                proxyBase = proxyBase,
                selectedEpisode = selectedEp,
                selectedSourceIdx = selectedSourceIdx,
                related = related,
                onSelectEpisode = vm::selectEpisode,
                onSelectSource = vm::selectSource,
                onToggleWatching = vm::toggleWatching,
                onPlay = onPlay,
                onOpenSubject = onOpenSubject,
            )
            DetailTab.Comments -> CommentsTab()
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun PosterHeader(
    posterUrl: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
    ) {
        if (posterUrl.isNotBlank()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Back button overlay (top-left)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(36.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
            )
        }
        // Fullscreen icon overlay (bottom-right) — static, mirrors design mock.
        Icon(
            imageVector = Icons.Outlined.Fullscreen,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(28.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Tabs + 发送弹幕 button
// ---------------------------------------------------------------------------

@Composable
private fun TabsAndDanmakuRow(
    selected: DetailTab,
    onSelect: (DetailTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabPill(
            label = "详情",
            selected = selected is DetailTab.Info,
            onClick = { onSelect(DetailTab.Info) },
        )
        Spacer(Modifier.width(8.dp))
        TabPill(
            label = "评论",
            selected = selected is DetailTab.Comments,
            onClick = { onSelect(DetailTab.Comments) },
        )
        Spacer(Modifier.weight(1f))
        DanmakuSendButton()
    }
}

@Composable
private fun TabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(if (selected) 20.dp else 0.dp)
                .background(accent, RoundedCornerShape(1.dp)),
        )
    }
}

/**
 * Static "send danmaku" pill — UI only; tapping it is a no-op to honour the
 * "弹幕不用动" spec.
 */
@Composable
private fun DanmakuSendButton() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "发送弹幕",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Send,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// 详情 tab body
// ---------------------------------------------------------------------------

@Composable
private fun InfoTab(
    title: String,
    group: SeriesGroup?,
    lookup: SeriesLookup?,
    loading: Boolean,
    error: String?,
    watching: Boolean,
    proxyBase: String,
    selectedEpisode: Int?,
    selectedSourceIdx: Int,
    related: List<BgmSubject>,
    onSelectEpisode: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onToggleWatching: () -> Unit,
    onPlay: (url: String, title: String) -> Unit,
    onOpenSubject: ((BgmSubject) -> Unit)?,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // ── title + favorite ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title.ifBlank { "—" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleWatching) {
                Icon(
                    imageVector = if (watching) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (watching) "移除追番" else "加入追番",
                    tint = if (watching) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        val episodes = group?.episodes.orEmpty()
        val ep = episodes.firstOrNull { it.episode == selectedEpisode }
        val source = ep?.sources?.getOrNull(selectedSourceIdx) ?: ep?.sources?.firstOrNull()

        // ── episode label + share/download icons ──
        if (ep != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildEpisodeLine(ep),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { /* static — no share impl */ }) {
                    Icon(
                        imageVector = Icons.Outlined.IosShare,
                        contentDescription = "分享",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { /* static — no offline impl yet */ }) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "下载",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── data source card with always-visible 更换 button ──
        Spacer(Modifier.height(12.dp))
        DataSourceCard(
            sources = ep?.sources.orEmpty(),
            selectedIdx = selectedSourceIdx,
            onSelect = onSelectSource,
            onPlay = { src ->
                if (proxyBase.isBlank()) return@DataSourceCard
                val url = buildByLinkUrl(proxyBase, src.channel, src.msgId)
                onPlay(url, "${title} - E${ep?.episode ?: "?"}")
            },
        )

        if (proxyBase.isBlank() && (group?.totalSources ?: 0) > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "尚未配置后端地址,无法播放。请到「设置」填入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // ── episode list ──
        Spacer(Modifier.height(20.dp))
        EpisodeListHeader(episodes = episodes)
        Spacer(Modifier.height(10.dp))
        when {
            loading && episodes.isEmpty() -> CenteredSmall("加载剧集中…", spinner = true)
            error != null && episodes.isEmpty() -> CenteredSmall("加载失败: $error")
            episodes.isEmpty() -> CenteredSmall(if (group == null) "找不到该番剧。" else "暂无可用剧集。")
            else -> EpisodeStrip(
                episodes = episodes,
                selected = selectedEpisode,
                onPick = { onSelectEpisode(it.episode) },
            )
        }

        // ── unparsed sources fallback ──
        val unparsed = group?.unparsedSources.orEmpty()
        if (unparsed.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "未识别集数 · ${unparsed.size}",
                style = MaterialTheme.typography.titleSmall,
                color = Color.Gray,
            )
            Spacer(Modifier.height(6.dp))
            unparsed.take(6).forEach { src ->
                UnparsedRow(
                    src = src,
                    proxyBase = proxyBase,
                    seriesTitle = title,
                    onPlay = onPlay,
                )
            }
        }

        // ── related recommendations ──
        if (related.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "相关推荐",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            related.take(6).forEach { subject ->
                RelatedRow(
                    subject = subject,
                    onClick = { onOpenSubject?.invoke(subject) },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private fun buildEpisodeLine(ep: EpisodeGroup): String {
    val ord = ep.episode.toString().padStart(2, '0')
    val src = ep.sources.firstOrNull()
    val tag = src?.let {
        val q = it.quality.firstOrNull().orEmpty()
        val grp = it.group ?: it.channelName
        sequenceOf(grp, q).filter { s -> s.isNotBlank() }.joinToString(" · ")
    }.orEmpty()
    return if (tag.isNotBlank()) "$ord  $tag" else ord
}

// ---------------------------------------------------------------------------
// 数据源 card — always-visible "更换" affordance
// ---------------------------------------------------------------------------

@Composable
private fun DataSourceCard(
    sources: List<EpisodeSource>,
    selectedIdx: Int,
    onSelect: (Int) -> Unit,
    onPlay: (EpisodeSource) -> Unit,
) {
    val current = sources.getOrNull(selectedIdx) ?: sources.firstOrNull()
    val label = current?.let { it.group ?: it.channelName } ?: "—"
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = current != null) { onPlay(current!!) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.SwapHoriz,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "数据源",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .clickable(enabled = sources.isNotEmpty()) { menuOpen = true }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(4.dp))
                Text("更换", style = MaterialTheme.typography.bodyMedium)
                if (sources.size > 1) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "(${sources.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                if (sources.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("暂无可用数据源") },
                        onClick = { menuOpen = false },
                        enabled = false,
                    )
                } else {
                    sources.forEachIndexed { idx, src ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = (src.group ?: src.channelName) +
                                            "  ·  " + formatSize(src.size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (idx == selectedIdx) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                    if (src.quality.isNotEmpty()) {
                                        Text(
                                            text = src.quality.joinToString(" "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                menuOpen = false
                                onSelect(idx)
                            },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 剧集列表
// ---------------------------------------------------------------------------

@Composable
private fun EpisodeListHeader(episodes: List<EpisodeGroup>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "剧集列表",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (episodes.isNotEmpty()) {
            Text(
                text = "已完结 · 全 ${episodes.size} 话",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
        }
    }
}

@Composable
private fun EpisodeStrip(
    episodes: List<EpisodeGroup>,
    selected: Int?,
    onPick: (EpisodeGroup) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(episodes, key = { it.episode }) { ep ->
            EpisodeChip(
                ep = ep,
                isSelected = ep.episode == selected,
                onClick = { onPick(ep) },
            )
        }
    }
}

@Composable
private fun EpisodeChip(
    ep: EpisodeGroup,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .width(110.dp)
            .height(78.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = ep.episode.toString().padStart(2, '0'),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
        Text(
            text = "${ep.sources.size} 源",
            style = MaterialTheme.typography.bodySmall,
            color = fg.copy(alpha = 0.8f),
        )
    }
}

// ---------------------------------------------------------------------------
// Unparsed sources fallback
// ---------------------------------------------------------------------------

@Composable
private fun UnparsedRow(
    src: EpisodeSource,
    proxyBase: String,
    seriesTitle: String,
    onPlay: (url: String, title: String) -> Unit,
) {
    val enabled = proxyBase.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled) {
                val url = buildByLinkUrl(proxyBase, src.channel, src.msgId)
                onPlay(url, seriesTitle)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = src.rawText.take(120),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
            Text(
                text = (src.group ?: src.channelName) + " · " + formatSize(src.size),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

// ---------------------------------------------------------------------------
// 评论 tab — empty placeholder
// ---------------------------------------------------------------------------

@Composable
private fun CommentsTab() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "暂无评论",
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ---------------------------------------------------------------------------
// 相关推荐 row
// ---------------------------------------------------------------------------

@Composable
private fun RelatedRow(
    subject: BgmSubject,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = subject.images.medium.ifBlank { subject.images.common },
            contentDescription = subject.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subject.nameCn.ifBlank { subject.name },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            if (subject.airDate.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatAirDate(subject.airDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
            if ((subject.rating?.score ?: 0.0) > 0.0) {
                Spacer(Modifier.height(2.dp))
                val total = subject.rating?.total ?: 0
                Text(
                    text = "${total}+ 收藏 · ${"%.1f".format(subject.rating!!.score)} 分",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}

private fun formatAirDate(date: String): String {
    val parts = date.split("-")
    if (parts.size < 2) return date
    val year = parts[0]
    val month = parts[1].toIntOrNull() ?: return date
    return "$year 年 $month 月"
}

// ---------------------------------------------------------------------------
// Shared small UI bits
// ---------------------------------------------------------------------------

@Composable
private fun CenteredSmall(text: String, spinner: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (spinner) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
    }
}


