package com.cameron.tganime.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.ui.graphics.vector.ImageVector

/** Bottom-nav tabs: 探索 / 电影 / 追番 / 缓存. */
enum class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    Explore("explore", com.cameron.tganime.R.string.tab_explore, Icons.Outlined.Explore),
    Movies("movies", com.cameron.tganime.R.string.tab_movies, Icons.Outlined.Movie),
    WatchList("watchlist", com.cameron.tganime.R.string.tab_watchlist, Icons.Outlined.FavoriteBorder),
    Cache("cache", com.cameron.tganime.R.string.tab_cache, Icons.Outlined.FolderOpen),
    ;

    companion object {
        val Default = Explore
        fun fromRoute(route: String?): Tab? = entries.firstOrNull { it.route == route }
    }
}
