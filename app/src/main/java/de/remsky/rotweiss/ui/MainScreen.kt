package de.remsky.rotweiss.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private fun Tab.icon(): ImageVector = when (this) {
    Tab.FC -> Icons.Default.SportsSoccer
    Tab.LIVE -> Icons.Default.LiveTv
    Tab.MOVIES -> Icons.Default.Movie
    Tab.SERIES -> Icons.Default.VideoLibrary
    Tab.FAVORITES -> Icons.Default.Favorite
}

@Composable
private fun TabContent(vm: AppViewModel) {
    when (vm.tab) {
        Tab.FC -> FcScreen(vm)
        Tab.LIVE -> LiveScreen(vm)
        Tab.MOVIES -> MoviesScreen(vm)
        Tab.SERIES -> SeriesScreen(vm)
        Tab.FAVORITES -> FavoritesScreen(vm)
    }
}

@Composable
fun MainScreen(vm: AppViewModel) {
    // Zurück: erst Unterseite schließen, dann zum FC-Tab, dann App verlassen
    BackHandler(enabled = vm.routes.isNotEmpty() || vm.tab != Tab.FC) {
        if (vm.routes.isNotEmpty()) vm.back() else vm.tab = Tab.FC
    }

    val route = vm.routes.lastOrNull()
    if (route != null) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            when (route) {
                is Route.ChannelEpg -> ChannelEpgScreen(vm, route.channel)
                is Route.MovieDetail -> MovieDetailScreen(vm, route.movie)
                is Route.SeriesDetail -> SeriesDetailScreen(vm, route.series)
                is Route.LockSettings -> LockSettingsScreen(vm, route.type)
                Route.Settings -> SettingsScreen(vm)
                Route.FcChannels -> FcChannelsScreen(vm)
            }
        }
        return
    }

    // Fernseher: Seitenleiste links, per Pfeiltasten bedienbar
    if (TvMode.enabled) {
        val startFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { startFocus.requestFocus() } }
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.weight(1f))
                Tab.entries.forEach { t ->
                    NavigationRailItem(
                        selected = vm.tab == t,
                        onClick = { vm.tab = t },
                        icon = { Icon(t.icon(), null) },
                        label = { Text(t.title) },
                        modifier = if (t == vm.tab) Modifier.focusRequester(startFocus) else Modifier,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { TabContent(vm) }
        }
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = vm.tab == t,
                        onClick = { vm.tab = t },
                        icon = { Icon(t.icon(), null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) { TabContent(vm) }
    }
}
