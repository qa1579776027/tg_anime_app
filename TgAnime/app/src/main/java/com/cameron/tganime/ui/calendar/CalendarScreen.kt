package com.cameron.tganime.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cameron.tganime.R
import com.cameron.tganime.data.network.BgmCalendarDay
import com.cameron.tganime.data.network.BgmSubject
import com.cameron.tganime.ui.discover.DiscoverState
import com.cameron.tganime.ui.discover.DiscoverViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onOpenSubject: (BgmSubject) -> Unit,
    vm: DiscoverViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.calendar_title),
                    fontWeight = FontWeight.SemiBold,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
        )

        when (val s = state) {
            DiscoverState.Loading -> Centered("加载中…", spinner = true)
            is DiscoverState.Failed -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("加载失败: ${s.msg}", color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = vm::refresh) { Text("重试") }
            }
            is DiscoverState.Loaded -> CalendarPager(days = s.days, onOpenSubject = onOpenSubject)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarPager(
    days: List<BgmCalendarDay>,
    onOpenSubject: (BgmSubject) -> Unit,
) {
    val todayBgmId = todayBgmWeekdayId()
    val initialIdx = days.indexOfFirst { it.weekday.id == todayBgmId }
        .takeIf { it >= 0 } ?: 0
    var selected by remember { mutableIntStateOf(initialIdx) }

    PrimaryScrollableTabRow(
        selectedTabIndex = selected,
        edgePadding = 8.dp,
    ) {
        days.forEachIndexed { index, day ->
            Tab(
                selected = selected == index,
                onClick = { selected = index },
                text = {
                    Text(
                        text = day.weekday.cn.ifBlank { day.weekday.en },
                        fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
            )
        }
    }

    val items = days.getOrNull(selected)?.items.orEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id }) { subject ->
            PosterCard(subject = subject, onClick = { onOpenSubject(subject) })
        }
    }
}

@Composable
private fun PosterCard(subject: BgmSubject, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = subject.images.medium.ifBlank { subject.images.common },
            contentDescription = subject.name,
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
private fun Centered(text: String, spinner: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (spinner) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
            }
            Text(text, color = Color.Gray)
        }
    }
}

/**
 * Bangumi convention: weekday IDs are 1=Mon, 2=Tue, …, 7=Sun.
 * java.util.Calendar's MONDAY=2, …, SUNDAY=1 — translate.
 */
private fun todayBgmWeekdayId(): Int {
    val cal = Calendar.getInstance()
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }
}
