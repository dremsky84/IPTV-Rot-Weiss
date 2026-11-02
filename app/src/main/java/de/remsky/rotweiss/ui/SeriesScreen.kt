@file:OptIn(ExperimentalMaterial3Api::class)

package de.remsky.rotweiss.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import de.remsky.rotweiss.data.Episode
import de.remsky.rotweiss.data.Series
import de.remsky.rotweiss.data.SeriesInfo
import androidx.compose.foundation.lazy.grid.items as gridItems

// ---------------------------------------------------------------- Serienliste

@Composable
fun SeriesScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.ensureSeries() }

    Column(Modifier.fillMaxSize()) {
        TitleBar("Serien", if (vm.seriesList.isNotEmpty()) "${vm.seriesList.size} Serien" else null) {
            SortButton(vm.seriesSort) { vm.seriesSort = it }
        }
        SearchField(vm.seriesQuery, { vm.seriesQuery = it }, "Serie suchen")
        CategoryChips(vm.visibleCategories("series", vm.seriesCategories), vm.seriesCategory) { vm.seriesCategory = it }

        when {
            vm.seriesLoading && vm.seriesList.isEmpty() -> CenterLoading("Serien werden geladen …")
            else -> {
                val list = vm.visibleSeries
                if (list.isEmpty()) CenterText("Keine Serien gefunden")
                else LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    gridItems(list, key = { it.seriesId }) { s ->
                        PosterCard(s.name, s.cover, s.rating, vm.isFavorite("series:${s.seriesId}")) {
                            vm.open(Route.SeriesDetail(s))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Seriendetails mit Staffeln und Folgen

@Composable
fun SeriesDetailScreen(vm: AppViewModel, s: Series) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var season by remember { mutableStateOf<Int?>(null) }
    var showPlot by remember { mutableStateOf(false) }

    LaunchedEffect(s.seriesId) {
        info = try { vm.seriesInfo(s.seriesId) } catch (_: Exception) { null }
        loaded = true
    }

    val key = "series:${s.seriesId}"
    val fav = vm.isFavorite(key)
    val i = info
    val seasons = i?.episodes?.keys?.sorted().orEmpty()

    // Zuletzt gesehene Folge dieser Serie → Staffel vorauswählen und "Weiter" anbieten
    val lastWatched = vm.history.firstOrNull { it.seriesId == s.seriesId }
    val lastEpisode: Episode? = i?.episodes?.values?.flatten()?.firstOrNull { "episode:${it.id}" == lastWatched?.key }
    LaunchedEffect(seasons, lastEpisode) {
        if (season == null && seasons.isNotEmpty()) season = lastEpisode?.season ?: seasons.first()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(i?.backdrop ?: s.cover, onBack = vm::back)

        Row(Modifier.padding(horizontal = 16.dp)) {
            Box(
                Modifier.width(110.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (s.cover != null) AsyncImage(s.cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                val facts = listOfNotNull(
                    (i?.year?.takeIf { it.isNotBlank() } ?: s.year.takeIf { it.isNotBlank() }),
                    if (seasons.isNotEmpty()) "${seasons.size} Staffel${if (seasons.size == 1) "" else "n"}" else null,
                    s.rating.takeIf { it.isNotBlank() }?.let { "★ $it" },
                )
                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = Color.Gray, fontSize = 13.sp)
                i?.genre?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp)); Text(it, color = Color.Gray, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val first = lastEpisode ?: seasons.firstOrNull()?.let { n -> i?.episodes?.get(n) }?.firstOrNull()
            Button(
                onClick = { if (i != null && first != null) { vm.playEpisode(s, i, first); ctx.openPlayer() } },
                enabled = first != null,
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (lastEpisode != null) "Weiter: S${lastEpisode.season} E${lastEpisode.number}" else "Abspielen",
                    maxLines = 1,
                )
            }
            OutlinedIconButton({ vm.toggleFavorite(key) }, Modifier.size(48.dp)) {
                Icon(if (fav) Icons.Default.Star else Icons.Default.StarBorder, "Favorit", tint = if (fav) Gold else Color.White)
            }
        }

        val plot = i?.plot?.takeIf { it.isNotBlank() } ?: s.plot
        if (plot.isNotBlank()) {
            Text(
                plot, lineHeight = 21.sp,
                maxLines = if (showPlot) 50 else 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(16.dp).clickable { showPlot = !showPlot },
            )
        }

        when {
            !loaded -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            i == null || seasons.isEmpty() -> Text("Keine Folgen gefunden", color = Color.Gray, modifier = Modifier.padding(16.dp))
            else -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(seasons, key = { it }) { n ->
                        FilterChip(season == n, { season = n }, { Text("Staffel $n") })
                    }
                }
                val eps = i.episodes[season ?: seasons.first()].orEmpty()
                eps.forEach { e ->
                    val w = vm.history.firstOrNull { it.key == "episode:${e.id}" }
                    EpisodeRow(e, w?.progress) { vm.playEpisode(s, i, e); ctx.openPlayer() }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EpisodeRow(e: Episode, progress: Float?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (e.image != null) AsyncImage(e.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Icon(Icons.Default.PlayArrow, null, tint = Color.White)
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${e.number}. ${e.title.ifBlank { "Folge ${e.number}" }}",
                maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
            )
            if (e.duration.isNotBlank()) Text(e.duration, fontSize = 12.sp, color = Color.Gray)
            if (e.plot.isNotBlank()) {
                Text(e.plot, fontSize = 12.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
