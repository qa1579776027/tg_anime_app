package com.cameron.tganime.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cameron.tganime.R
import com.cameron.tganime.data.network.TvHit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onPlay: (url: String, title: String) -> Unit,
    onBack: (() -> Unit)? = null,
    vm: SearchViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val proxyBase by vm.proxyBase.collectAsStateWithLifecycle()
    val playingId by vm.playingHitId.collectAsStateWithLifecycle()
    val playError by vm.playError.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(playError) {
        val msg = playError ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        vm.clearPlayError()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("搜索", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            },
            actions = {
                val canRefresh = state is SearchState.Loaded || query.isNotBlank()
                IconButton(onClick = vm::refresh, enabled = canRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新（绕过缓存）")
                }
            },
        )

        SearchInputRow(
            query = query,
            onQuery = vm::setQuery,
            onSubmit = vm::submit,
            onClear = { vm.setQuery("") },
        )

        if (proxyBase.isBlank()) {
            ProxyMissingBanner()
            Spacer(Modifier.height(8.dp))
        }

        Box(modifier = Modifier.weight(1f)) {
            when (val s = state) {
                SearchState.Idle -> Centered(stringResource(R.string.search_hint))
                SearchState.Loading -> Centered("搜索中…", showSpinner = true)
                is SearchState.Failed -> Centered("出错了：${s.msg}")
                is SearchState.Loaded -> ResultList(
                    state = s,
                    playingId = playingId,
                    onHitClick = { hit ->
                        vm.play(hit) { url, title -> onPlay(url, title) }
                    },
                    onLoadMore = vm::loadMore,
                )
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputRow(
    query: String,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Outlined.Clear, contentDescription = "清除")
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProxyMissingBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
    ) {
        Text(
            "尚未配置后端地址。请到「设置」填入 tg_anime 的地址，例如 http://192.168.31.20:8080",
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Centered(text: String, showSpinner: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showSpinner) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}

@Composable
private fun ResultList(
    state: SearchState.Loaded,
    playingId: Long?,
    onHitClick: (TvHit) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (state.hits.isEmpty()) {
        Centered(stringResource(R.string.search_no_results))
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item("header") {
            Text(
                text = "已加载 ${state.hits.size} / ${state.totalSoFar} 条" +
                    if (state.drainDone) "（已抓取完毕）" else "（仍在后台抓取…）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        items(state.hits, key = { it.hit_id }) { hit ->
            HitCard(
                hit = hit,
                resolving = playingId == hit.hit_id,
                onClick = { onHitClick(hit) },
            )
        }
        item("footer") {
            LoadMoreFooter(state = state, onLoadMore = onLoadMore)
        }
    }
}

@Composable
private fun HitCard(hit: TvHit, resolving: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !resolving, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.title.ifBlank { "(无标题)" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(text = driveLabel(hit.drive))
                hit.quality.take(2).forEach { q -> Badge(text = q) }
            }
            if (hit.posted_at.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hit.posted_at,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        if (resolving) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
        } else {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = "播放",
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Badge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun driveLabel(drive: String): String = when (drive.lowercase()) {
    "quark" -> "夸克"
    "" -> "未知"
    else -> drive
}

@Composable
private fun LoadMoreFooter(state: SearchState.Loaded, onLoadMore: () -> Unit) {
    val enabled = state.hasMore && !state.loadingMore
    val label = when {
        state.loadingMore -> "加载中…"
        !state.hasMore -> "已经没有更多了"
        else -> "加载更多"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (enabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable(enabled = enabled, onClick = onLoadMore)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.loadingMore) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    state.loadMoreError?.let { err ->
        Text(
            text = "加载下一页失败：$err",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 6.dp),
        )
    }
}
