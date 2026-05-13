package com.cameron.tganime.ui.cache

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cameron.tganime.R
import com.cameron.tganime.data.network.OpenListEntry
import com.cameron.tganime.util.formatSize
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheScreen(
    onPlay: (url: String, title: String) -> Unit,
    onOpenSettings: () -> Unit,
    vm: CacheViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val path by vm.path.collectAsStateWithLifecycle()
    val proxyBase by vm.proxyBase.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(proxyBase) {
        if (proxyBase.isNotBlank() && state == CacheState.Idle) {
            vm.load("/")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.cache_title),
                    fontWeight = FontWeight.SemiBold,
                )
            },
            navigationIcon = {
                if (path != "/" && proxyBase.isNotBlank()) {
                    IconButton(onClick = { vm.load(vm.parentOf(path)) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "上一级")
                    }
                }
            },
            actions = {
                if (proxyBase.isNotBlank()) {
                    IconButton(onClick = { vm.load(path, refresh = true) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                }
            },
        )

        if (proxyBase.isBlank()) {
            ProxyMissingState(onOpenSettings = onOpenSettings)
            return@Column
        }

        // Path breadcrumb
        Text(
            text = path,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )

        when (val s = state) {
            CacheState.Idle -> Centered(stringResource(R.string.cache_loading), spinner = true)
            CacheState.Loading -> Centered(stringResource(R.string.cache_loading), spinner = true)
            is CacheState.Failed -> ErrorState(s.msg, onRetry = { vm.load(path, refresh = true) })
            is CacheState.Loaded -> {
                if (s.entries.isEmpty()) Centered(stringResource(R.string.cache_empty_dir))
                else EntryList(
                    entries = s.entries,
                    onClickDir = { entry ->
                        val next = joinPath(s.path, entry.name)
                        vm.load(next)
                    },
                    onClickFile = { entry ->
                        val full = joinPath(s.path, entry.name)
                        scope.launch {
                            val url = vm.resolveUrl(full)
                            if (!url.isNullOrBlank()) onPlay(url, entry.name)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun EntryList(
    entries: List<OpenListEntry>,
    onClickDir: (OpenListEntry) -> Unit,
    onClickFile: (OpenListEntry) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.name }) { e ->
            EntryRow(
                entry = e,
                onClick = { if (e.isDir) onClickDir(e) else onClickFile(e) },
            )
        }
    }
}

@Composable
private fun EntryRow(entry: OpenListEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when {
                entry.isDir -> Icons.Outlined.Folder
                entry.name.matches(MEDIA_REGEX) -> Icons.Outlined.PlayArrow
                else -> Icons.Outlined.Description
            },
            contentDescription = null,
            tint = if (entry.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            if (!entry.isDir) {
                Text(
                    text = formatSize(entry.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            } else if (entry.modified.isNotBlank()) {
                Text(
                    text = entry.modified.take(19).replace('T', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}

@Composable
private fun ProxyMissingState(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.cache_proxy_missing),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onOpenSettings) { Text("去设置") }
    }
}

@Composable
private fun ErrorState(msg: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("加载失败: $msg", color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun Centered(text: String, spinner: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (spinner) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
            }
            Text(text, color = Color.Gray)
        }
    }
}

private val MEDIA_REGEX = Regex(""".+\.(mp4|mkv|webm|mov|m4v|ts|avi|flv|wmv|m3u8)$""", RegexOption.IGNORE_CASE)

private fun joinPath(base: String, name: String): String {
    val b = base.trimEnd('/').ifEmpty { "" }
    return "$b/$name"
}
