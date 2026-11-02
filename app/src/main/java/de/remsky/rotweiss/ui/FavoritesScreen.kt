package de.remsky.rotweiss.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.remsky.rotweiss.data.WatchEntry

@Composable
fun FavoritesScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val favs = vm.favorites

    // Filme/Serien nur laden, wenn dafür Favoriten existieren
    LaunchedEffect(favs) {
        if (favs.any { it.startsWith("movie:") }) vm.ensureMovies()
        if (favs.any { it.startsWith("series:") }) vm.ensureSeries()
    }

    val favChannels = vm.channels.filter { "live:${it.streamId}" in favs && !vm.isLocked("live", it.categoryId) }
    val favMovies = vm.movies.filter { "movie:${it.streamId}" in favs && !vm.isLocked("movie", it.categoryId) }
    val favSeries = vm.seriesList.filter { "series:${it.seriesId}" in favs && !vm.isLocked("series", it.categoryId) }
    val history = vm.history

    Column(Modifier.fillMaxSize()) {
        TitleBar("Meine Inhalte") {
            IconButton({ vm.open(Route.Settings) }) { Icon(Icons.Default.Settings, "Einstellungen") }
        }

        if (history.isEmpty() && favs.isEmpty()) {
            CenterText("Hier erscheinen angefangene Filme und Folgen sowie deine Favoriten.\n\nTippe bei Sendern, Filmen oder Serien auf den Stern ☆.")
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (history.isNotEmpty()) {
                Section("Weiterschauen")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(history, key = { it.key }) { w ->
                        ResumeCard(
                            w,
                            onClick = { vm.playHistory(w); ctx.openPlayer() },
                            onRemove = { vm.removeHistory(w.key) },
                        )
                    }
                }
            }

            if (favChannels.isNotEmpty()) {
                Section("Lieblingssender")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(favChannels, key = { it.streamId }) { ch ->
                        LaunchedEffect(ch.streamId) { vm.requestNowNext(ch.streamId) }
                        val now = vm.nowNext[ch.streamId]?.firstOrNull()
                        Column(
                            Modifier.width(130.dp).clip(RoundedCornerShape(10.dp))
                                .clickable { vm.playChannel(favChannels, ch); ctx.openPlayer() }
                                .background(MaterialTheme.colorScheme.surface).padding(8.dp),
                        ) {
                            ChannelLogo(ch, 114, 64)
                            Spacer(Modifier.height(6.dp))
                            Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                now?.title ?: " ", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp, color = Color.Gray,
                            )
                        }
                    }
                }
            }

            if (favMovies.isNotEmpty()) {
                Section("Lieblingsfilme")
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(favMovies, key = { it.streamId }) { m ->
                        Box(Modifier.width(115.dp)) {
                            PosterCard(m.name, m.poster, m.rating, true) { vm.open(Route.MovieDetail(m)) }
                        }
                    }
                }
            } else if (favs.any { it.startsWith("movie:") } && vm.moviesLoading) {
                Section("Lieblingsfilme")
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            }

            if (favSeries.isNotEmpty()) {
                Section("Lieblingsserien")
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(favSeries, key = { it.seriesId }) { s ->
                        Box(Modifier.width(115.dp)) {
                            PosterCard(s.name, s.cover, s.rating, true) { vm.open(Route.SeriesDetail(s)) }
                        }
                    }
                }
            } else if (favs.any { it.startsWith("series:") } && vm.seriesLoading) {
                Section("Lieblingsserien")
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun ResumeCard(w: WatchEntry, onClick: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.width(200.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (w.image != null) AsyncImage(w.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.size(40.dp).background(Color(0x99000000), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White)
            }
            IconButton(onRemove, Modifier.align(Alignment.TopEnd).size(32.dp)) {
                Icon(
                    Icons.Default.Close, "Entfernen", tint = Color.White,
                    modifier = Modifier.size(18.dp).background(Color(0x99000000), CircleShape).padding(2.dp),
                )
            }
            LinearProgressIndicator(
                progress = { w.progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(w.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (w.subtitle.isNotBlank()) {
            Text(w.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Color.Gray)
        }
    }
}
