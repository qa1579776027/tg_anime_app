package com.cameron.tganime.ui.nav

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cameron.tganime.data.network.BgmSubject
import com.cameron.tganime.data.prefs.WatchEntry
import com.cameron.tganime.ui.cache.CacheScreen
import com.cameron.tganime.ui.calendar.CalendarScreen
import com.cameron.tganime.ui.detail.SeriesDetailScreen
import com.cameron.tganime.ui.explore.ExploreScreen
import com.cameron.tganime.ui.movies.MoviesScreen
import com.cameron.tganime.ui.player.PlayerScreen
import com.cameron.tganime.ui.search.SearchScreen
import com.cameron.tganime.ui.settings.SettingsScreen
import com.cameron.tganime.ui.watchlist.WatchListScreen

private const val ROUTE_PLAYER = "player/{url}/{title}"
private const val ROUTE_CALENDAR = "calendar"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_SERIES = "series/{key}"

fun playerRoute(url: String, title: String): String =
    "player/${Uri.encode(url)}/${Uri.encode(title)}"

fun seriesRoute(key: String): String = "series/${Uri.encode(key)}"

/**
 * Open the detail page for a bgm.tv subject (Explore / Calendar). The
 * [PendingSeriesLookup] hand-off carries the title + poster across the
 * navigation; the detail screen runs an acgn.es search to find episodes.
 */
private fun openSubject(nav: NavController, subject: BgmSubject) {
    val lookup = lookupForBgmSubject(subject)
    PendingSeriesLookup.put(lookup)
    nav.navigate(seriesRoute(lookup.key))
}

/** Open the detail page for a watch-list entry. */
private fun openWatchEntry(nav: NavController, entry: WatchEntry) {
    val lookup = lookupForWatchEntry(entry)
    PendingSeriesLookup.put(lookup)
    nav.navigate(seriesRoute(lookup.key))
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val showBottomBar = route in Tab.entries.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(Tab.Default.route) { saveState = true }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Tab.Default.route,
            modifier = Modifier.padding(inner),
        ) {
            composable(Tab.Explore.route) {
                ExploreScreen(
                    onOpenSearch = { nav.navigate(ROUTE_SEARCH) },
                    onOpenSettings = { nav.navigate(ROUTE_SETTINGS) },
                    onOpenCalendar = { nav.navigate(ROUTE_CALENDAR) },
                    onOpenSubject = { subject -> openSubject(nav, subject) },
                    onOpenWatchEntry = { entry -> openWatchEntry(nav, entry) },
                )
            }
            composable(Tab.Movies.route) {
                MoviesScreen(
                    onPlay = { url, title -> nav.navigate(playerRoute(url, title)) },
                )
            }
            composable(Tab.WatchList.route) {
                WatchListScreen(
                    onOpenEntry = { entry -> openWatchEntry(nav, entry) },
                )
            }
            composable(Tab.Cache.route) {
                CacheScreen(
                    onPlay = { url, title -> nav.navigate(playerRoute(url, title)) },
                    onOpenSettings = { nav.navigate(ROUTE_SETTINGS) },
                )
            }

            composable(ROUTE_CALENDAR) {
                CalendarScreen(
                    onBack = { nav.popBackStack() },
                    onOpenSubject = { subject -> openSubject(nav, subject) },
                )
            }
            composable(ROUTE_SEARCH) {
                SearchScreen(
                    onBack = { nav.popBackStack() },
                    onPlay = { url, title -> nav.navigate(playerRoute(url, title)) },
                )
            }
            composable(
                route = ROUTE_SERIES,
                arguments = listOf(navArgument("key") { type = NavType.StringType }),
            ) { entry ->
                val key = entry.arguments?.getString("key").orEmpty()
                SeriesDetailScreen(
                    seriesKey = key,
                    onBack = { nav.popBackStack() },
                    onPlay = { url, title -> nav.navigate(playerRoute(url, title)) },
                    onOpenSubject = { subject -> openSubject(nav, subject) },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }

            composable(
                route = ROUTE_PLAYER,
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                ),
            ) { entry ->
                val url = entry.arguments?.getString("url").orEmpty()
                val title = entry.arguments?.getString("title").orEmpty()
                PlayerScreen(
                    url = url,
                    title = title,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
