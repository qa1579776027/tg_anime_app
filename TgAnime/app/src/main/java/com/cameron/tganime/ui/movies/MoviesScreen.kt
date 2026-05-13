package com.cameron.tganime.ui.movies

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cameron.tganime.data.network.TmdbItem
import kotlinx.coroutines.delay

private const val MOVIES_TITLE = "电影"
private const val ERROR_RETRY = "重试"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(vm: MoviesViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<TmdbItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(MOVIES_TITLE, fontWeight = FontWeight.SemiBold) },
        )

        when (val s = state) {
            MoviesState.Loading -> CenteredSpinner()
            is MoviesState.Failed -> CenteredError(s.msg, onRetry = vm::refresh)
            is MoviesState.Loaded -> MoviesBody(
                hero = s.hero,
                rows = s.rows,
                onClick = { selected = it },
            )
        }
    }

    selected?.let { item ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = sheetState,
        ) {
            MovieDetailSheet(item)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoviesBody(
    hero: List<TmdbItem>,
    rows: List<MovieRow>,
    onClick: (TmdbItem) -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(bottom = 24.dp),
    ) {
        if (hero.isNotEmpty()) {
            HeroPager(items = hero, onClick = onClick)
            Spacer(Modifier.height(8.dp))
        }
        rows.forEach { row ->
            if (row.items.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                RowSection(row = row, onClick = onClick)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroPager(items: List<TmdbItem>, onClick: (TmdbItem) -> Unit) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(pageCount = { items.size })

    // Netflix-style auto-advance every 6s. Pauses while the user is dragging
    // (rememberPagerState exposes isScrollInProgress as Compose state, so the
    // LaunchedEffect re-keys and stops the timer).
    LaunchedEffect(pager.isScrollInProgress, items.size) {
        if (!pager.isScrollInProgress && items.size > 1) {
            delay(6_000)
            val next = (pager.currentPage + 1) % items.size
            pager.animateScrollToPage(next)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth(),
        ) { idx ->
            HeroCard(item = items[idx], onClick = { onClick(items[idx]) })
        }
        PagerDots(
            count = items.size,
            current = pager.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        )
    }
}

@Composable
private fun HeroCard(item: TmdbItem, onClick: () -> Unit) {
    val img = item.backdrop ?: item.poster
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        if (img != null) {
            AsyncImage(
                model = img,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        BottomGradientScrim()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                item.title.ifBlank { item.original_title },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            val meta = buildList {
                if (item.year.isNotBlank()) add(item.year)
                if (item.kind == "tv") add("剧集") else add("电影")
                item.rating?.takeIf { it > 0.0 }?.let { add("★ %.1f".format(it)) }
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    meta,
                    color = Color(0xFFE0E0E0),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun RowSection(row: MovieRow, onClick: (TmdbItem) -> Unit) {
    Text(
        row.title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(row.items, key = { it.id }) { item ->
            PosterCard(item = item, onClick = { onClick(item) })
        }
    }
}

@Composable
private fun PosterCard(item: TmdbItem, onClick: () -> Unit) {
    // 2:3 portrait poster (TMDB standard). 120dp wide → ~180dp tall, lets the
    // user see ~3 posters per screen on a typical phone.
    Column(
        modifier = Modifier.width(120.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
        ) {
            if (item.poster != null) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            item.rating?.takeIf { it > 0.0 }?.let { score ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "★ %.1f".format(score),
                        color = Color(0xFFFFD479),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title.ifBlank { item.original_title },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
        )
    }
}

@Composable
private fun MovieDetailSheet(item: TmdbItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
    ) {
        val img = item.backdrop ?: item.poster
        if (img != null) {
            AsyncImage(
                model = img,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            item.title.ifBlank { item.original_title },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        val meta = buildList {
            if (item.year.isNotBlank()) add(item.year)
            if (item.kind == "tv") add("剧集") else add("电影")
            item.rating?.takeIf { it > 0.0 }?.let { add("TMDB ★ %.1f".format(it)) }
            if (item.original_title.isNotBlank() && item.original_title != item.title) {
                add(item.original_title)
            }
        }.joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(Modifier.height(10.dp))
        }
        if (item.overview.isNotBlank()) {
            Text(item.overview, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("暂无简介", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}

@Composable
fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    if (count <= 1) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { i ->
            val active = i == current
            Box(
                modifier = Modifier
                    .size(if (active) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) Color.White
                        else Color.White.copy(alpha = 0.4f),
                    ),
            )
        }
    }
}

@Composable
fun BoxScope.BottomGradientScrim() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.75f),
                    ),
                    startY = 200f,
                )
            )
    )
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredError(msg: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "加载失败: $msg",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text(ERROR_RETRY) }
    }
}
