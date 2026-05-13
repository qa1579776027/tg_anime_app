package com.cameron.tganime.ui.explore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cameron.tganime.R
import com.cameron.tganime.data.network.BgmSubject
import com.cameron.tganime.data.prefs.WatchEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSubject: (BgmSubject) -> Unit,
    onOpenWatchEntry: (WatchEntry) -> Unit,
    vm: ExploreViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val watching by vm.watchList.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.explore_title),
                    fontWeight = FontWeight.SemiBold,
                )
            },
            actions = {
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.explore_search),
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = stringResource(R.string.explore_profile),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(),
        )

        when (val s = state) {
            ExploreState.Loading -> CenteredSpinner()
            is ExploreState.Failed -> CenteredError(s.msg, onRetry = vm::refresh)
            is ExploreState.Loaded -> ExploreBody(
                hot = s.hot,
                recommended = s.recommended,
                watching = watching,
                onOpenSubject = onOpenSubject,
                onOpenWatchEntry = onOpenWatchEntry,
                onOpenCalendar = onOpenCalendar,
                onToggleWatching = vm::toggleWatching,
            )
        }
    }
}

@Composable
private fun ExploreBody(
    hot: List<BgmSubject>,
    recommended: List<BgmSubject>,
    watching: List<WatchEntry>,
    onOpenSubject: (BgmSubject) -> Unit,
    onOpenWatchEntry: (WatchEntry) -> Unit,
    onOpenCalendar: () -> Unit,
    onToggleWatching: (BgmSubject) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.explore_hot_section),
            trailingButton = {
                TextButton(onClick = onOpenCalendar) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.explore_calendar_button))
                }
            },
        )
        HotCarousel(items = hot, onClick = onOpenSubject)

        Spacer(Modifier.height(24.dp))
        SectionHeader(title = stringResource(R.string.explore_continue_section))
        ContinueWatchingRow(
            entries = watching,
            onClick = onOpenWatchEntry,
        )

        Spacer(Modifier.height(24.dp))
        SectionHeader(title = stringResource(R.string.explore_recommend_section))
        RecommendedGrid(
            items = recommended,
            onClick = onOpenSubject,
            onLongPress = onToggleWatching,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    trailingButton: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailingButton?.invoke()
    }
}

@Composable
private fun HotCarousel(items: List<BgmSubject>, onClick: (BgmSubject) -> Unit) {
    if (items.isEmpty()) return
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Show ~1.4 cards per screen so the next item peeks in.
        val cardWidth = maxWidth * 0.72f
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { subject ->
                HotCard(
                    subject = subject,
                    width = cardWidth,
                    onClick = { onClick(subject) },
                )
            }
        }
    }
}

@Composable
private fun HotCard(
    subject: BgmSubject,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = subject.images.large.ifBlank {
                subject.images.medium.ifBlank { subject.images.common }
            },
            contentDescription = subject.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f),
                        ),
                        startY = 200f,
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                text = subject.nameCn.ifBlank { subject.name },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            if ((subject.rating?.score ?: 0.0) > 0.0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "★ ${"%.1f".format(subject.rating!!.score)}",
                    color = Color(0xFFFFD479),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    entries: List<WatchEntry>,
    onClick: (WatchEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.explore_continue_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
            )
        }
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { it.id }) { e ->
            Column(
                modifier = Modifier
                    .width(110.dp)
                    .clickable { onClick(e) },
            ) {
                AsyncImage(
                    model = e.image,
                    contentDescription = e.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = e.nameCn.ifBlank { e.name },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun RecommendedGrid(
    items: List<BgmSubject>,
    onClick: (BgmSubject) -> Unit,
    onLongPress: (BgmSubject) -> Unit,
) {
    if (items.isEmpty()) return
    // Two-column rows: matches the reference UI's "推荐" grid without nesting
    // a LazyVerticalGrid inside the verticalScroll (that combination is
    // forbidden by Compose).
    val rows = items.chunked(3)
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { subject ->
                    GridPoster(
                        subject = subject,
                        modifier = Modifier.weight(1f),
                        onClick = { onClick(subject) },
                        onLongPress = { onLongPress(subject) },
                    )
                }
                // Fill empty cells so the row keeps even widths.
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridPoster(
    subject: BgmSubject,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        AsyncImage(
            model = subject.images.medium.ifBlank { subject.images.common },
            contentDescription = subject.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subject.nameCn.ifBlank { subject.name },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
        )
        if ((subject.rating?.score ?: 0.0) > 0.0) {
            Text(
                text = "★ ${"%.1f".format(subject.rating!!.score)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.discover_loading), color = Color.Gray)
        }
    }
}

@Composable
private fun CenteredError(msg: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("加载失败: $msg", color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}
