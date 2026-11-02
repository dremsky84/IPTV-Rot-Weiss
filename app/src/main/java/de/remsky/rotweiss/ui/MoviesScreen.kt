@file:OptIn(ExperimentalMaterial3Api::class)

package de.remsky.rotweiss.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.remsky.rotweiss.data.Movie
import de.remsky.rotweiss.data.MovieInfo
import androidx.compose.foundation.lazy.grid.items as gridItems

// ---------------------------------------------------------------- Filmliste

@Composable
fun MoviesScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.ensureMovies() }

    Column(Modifier.fillMaxSize()) {
        TitleBar("Filme", if (vm.movies.isNotEmpty()) "${vm.movies.size} Filme" else null) {
            SortButton(vm.movieSort) { vm.movieSort = it }
        }
        SearchField(vm.movieQuery, { vm.movieQuery = it }, "Film suchen")
        CategoryChips(vm.visibleCategories("movie", vm.movieCategories), vm.movieCategory) { vm.movieCategory = it }

        when {
            vm.moviesLoading && vm.movies.isEmpty() -> CenterLoading("Filme werden geladen …")
            else -> {
                val list = vm.visibleMovies
                if (list.isEmpty()) CenterText("Keine Filme gefunden")
                else LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    gridItems(list, key = { it.streamId }) { m ->
                        PosterCard(m.name, m.poster, m.rating, vm.isFavorite("movie:${m.streamId}")) {
                            vm.open(Route.MovieDetail(m))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Filmdetails

@Composable
fun MovieDetailScreen(vm: AppViewModel, m: Movie) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<MovieInfo?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(m.streamId) {
        info = try { vm.movieInfo(m.streamId) } catch (_: Exception) { null }
        loaded = true
    }
    val key = "movie:${m.streamId}"
    val resume = vm.history.firstOrNull { it.key == key }
    val fav = vm.isFavorite(key)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(info?.backdrop ?: m.poster, onBack = vm::back)

        Row(Modifier.padding(horizontal = 16.dp)) {
            Box(
                Modifier.width(110.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (m.poster != null) AsyncImage(m.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                val facts = listOfNotNull(
                    info?.year?.takeIf { it.isNotBlank() },
                    info?.duration?.takeIf { it.isNotBlank() },
                    (info?.rating?.takeIf { it.isNotBlank() } ?: m.rating.takeIf { it.isNotBlank() })?.let { "★ $it" },
                )
                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = Color.Gray, fontSize = 13.sp)
                info?.genre?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp)); Text(it, color = Color.Gray, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.playMovie(m); ctx.openPlayer() },
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text(if (resume != null) "Fortsetzen" else "Abspielen")
            }
            if (resume != null) {
                OutlinedButton(
                    onClick = { vm.removeHistory(key); vm.playMovie(m); ctx.openPlayer() },
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(Icons.Default.Replay, null); Spacer(Modifier.width(4.dp)); Text("Von vorn")
                }
            }
            OutlinedIconButton({ vm.toggleFavorite(key) }, Modifier.size(48.dp)) {
                Icon(if (fav) Icons.Default.Star else Icons.Default.StarBorder, "Favorit", tint = if (fav) Gold else Color.White)
            }
        }
        if (resume != null) {
            LinearProgressIndicator(
                progress = { resume.progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(3.dp),
            )
        }

        Column(Modifier.padding(16.dp)) {
            if (!loaded) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                val i = info
                if (i != null && i.plot.isNotBlank()) Text(i.plot, lineHeight = 21.sp)
                if (i != null && i.director.isNotBlank()) Fact("Regie", i.director)
                if (i != null && i.cast.isNotBlank()) Fact("Besetzung", i.cast)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun Header(image: String?, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(200.dp)) {
        if (image != null) {
            AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, MaterialTheme.colorScheme.background))
            )
        )
        IconButton(onBack, Modifier.padding(4.dp).align(Alignment.TopStart)) {
            Icon(Icons.Default.ArrowBack, "Zurück", tint = Color.White)
        }
    }
}

@Composable
fun Fact(label: String, value: String) {
    Spacer(Modifier.height(10.dp))
    Text(label, color = Color.Gray, fontSize = 12.sp)
    Text(value, fontSize = 14.sp)
}
