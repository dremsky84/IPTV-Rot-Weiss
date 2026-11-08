@file:OptIn(ExperimentalMaterial3Api::class)

package de.remsky.rotweiss.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
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
import de.remsky.rotweiss.data.Channel
import de.remsky.rotweiss.data.EpgEntry

// ---------------------------------------------------------------- Senderliste

@Composable
fun LiveScreen(vm: AppViewModel) {
    if (useWideLive()) { LiveWideScreen(vm); return }
    val ctx = LocalContext.current
    var favOnly by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TitleBar(
            "Live-TV",
            vm.account?.let { "Gültig bis ${it.expires} · ${vm.channels.size} Sender" },
        ) {
            IconButton({ favOnly = !favOnly }) {
                Icon(
                    if (favOnly) Icons.Default.Star else Icons.Default.StarBorder,
                    "Nur Favoriten", tint = if (favOnly) Gold else LocalContentColor.current,
                )
            }
        }
        SearchField(vm.liveQuery, { vm.liveQuery = it }, "Sender suchen")
        CategoryChips(vm.visibleCategories("live", vm.liveCategories), vm.liveCategory) { vm.liveCategory = it }

        val list = vm.visibleChannels.let { l -> if (favOnly) l.filter { vm.isFavorite("live:${it.streamId}") } else l }
        if (list.isEmpty()) {
            CenterText(if (favOnly) "Noch keine Lieblingssender.\nTippe auf den Stern neben einem Sender." else "Keine Sender gefunden")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(list, key = { it.streamId }) { ch ->
                    ChannelRow(
                        vm, ch,
                        onPlay = { vm.playChannel(list, ch); ctx.openPlayer() },
                        onGuide = { vm.open(Route.ChannelEpg(ch)) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Archiv (Sender mit Catch-up)

@Composable
fun ArchiveScreen(vm: AppViewModel) {
    if (useWideLive()) { ArchiveWideScreen(vm); return }
    val list = vm.archiveChannels
    Column(Modifier.fillMaxSize()) {
        TitleBar("Archiv", "${list.size} Sender mit Archiv · Sender wählen, dann Sendung") {}
        SearchField(vm.archiveQuery, { vm.archiveQuery = it }, "Sender suchen")
        if (list.isEmpty()) {
            CenterText(
                if (vm.channels.none { it.hasArchive }) "Dein Anbieter bietet für keinen Sender ein Archiv an."
                else "Keine Sender gefunden",
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(list, key = { it.streamId }) { ch ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.open(Route.ChannelEpg(ch)) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChannelLogo(ch)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            if (ch.archiveDays > 0) {
                                Text("${ch.archiveDays} Tage zurück", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Icon(Icons.Default.History, null, tint = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(vm: AppViewModel, ch: Channel, onPlay: () -> Unit, onGuide: () -> Unit) {
    LaunchedEffect(ch.streamId) { vm.requestNowNext(ch.streamId) }
    val epg = vm.nowNext[ch.streamId].orEmpty()
    val now = epg.firstOrNull()
    val next = epg.getOrNull(1)
    val fav = vm.isFavorite("live:${ch.streamId}")

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(ch)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false),
                )
                if (ch.hasArchive) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.History, "Catch-up", Modifier.size(14.dp), tint = Color.Gray)
                }
            }
            if (now != null) {
                Text(
                    "${clock(now.start)}  ${now.title}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp, color = Color(0xFFB8C4D6),
                )
                LinearProgressIndicator(
                    progress = { epgProgress(now) },
                    modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 3.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                if (next != null) {
                    Text(
                        "${clock(next.start)}  ${next.title}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp, color = Color.Gray,
                    )
                }
            }
        }
        IconButton(onGuide) { Icon(Icons.Default.DateRange, "Programm", tint = Color.Gray) }
        IconButton({ vm.toggleFavorite("live:${ch.streamId}") }) {
            Icon(
                if (fav) Icons.Default.Star else Icons.Default.StarBorder, "Favorit",
                tint = if (fav) Gold else Color.Gray,
            )
        }
    }
}

@Composable
fun ChannelLogo(ch: Channel, width: Int = 56, height: Int = 40) {
    Box(
        Modifier.size(width.dp, height.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (ch.logo != null) {
            AsyncImage(ch.logo, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
        } else {
            Text(ch.name.take(2).uppercase(), fontWeight = FontWeight.Bold, color = Color.Gray)
        }
    }
}

// ---------------------------------------------------------------- Programm eines Senders (EPG + Catch-up)

@Composable
fun ChannelEpgScreen(vm: AppViewModel, ch: Channel) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<EpgEntry>?>(null) }
    var selected by remember { mutableStateOf<EpgEntry?>(null) }
    val state = rememberLazyListState()

    LaunchedEffect(ch.streamId) {
        list = try { vm.fullEpg(ch.streamId) } catch (_: Exception) { emptyList() }
        val now = System.currentTimeMillis() / 1000
        val idx = list.orEmpty().indexOfFirst { it.end > now }
        if (idx > 0) state.scrollToItem((idx - 1).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(ch, 44, 32)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        Text(
                            if (ch.hasArchive) "Programm · Catch-up ${ch.archiveDays} Tage" else "Programm",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                        )
                    }
                }
            },
            navigationIcon = { IconButton(vm::back) { Icon(Icons.Default.ArrowBack, "Zurück") } },
            actions = {
                IconButton({ vm.playChannel(listOf(ch), ch); ctx.openPlayer() }) {
                    Icon(Icons.Default.PlayArrow, "Live ansehen")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        val entries = list
        when {
            entries == null -> CenterLoading("Programm wird geladen …")
            entries.isEmpty() -> CenterText("Für diesen Sender gibt es kein Programm (EPG).")
            else -> {
                val now = System.currentTimeMillis() / 1000
                LazyColumn(state = state, contentPadding = PaddingValues(bottom = 16.dp)) {
                    var lastDay = ""
                    entries.forEachIndexed { i, e ->
                        val d = day(e.start)
                        if (d != lastDay) {
                            lastDay = d
                            item(key = "day-$i") {
                                Text(
                                    d, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                                )
                            }
                        }
                        item(key = "e-$i") {
                            val running = e.start <= now && e.end > now
                            val past = e.end <= now
                            val canWatch = running || (past && (e.hasArchive || ch.hasArchive))
                            EpgRow(e, running, past, canWatch) {
                                when {
                                    running -> { vm.playChannel(listOf(ch), ch); ctx.openPlayer() }
                                    canWatch -> { vm.playCatchup(ch, e); ctx.openPlayer() }
                                    else -> selected = e
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { e ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(e.title) },
            text = {
                Column {
                    Text("${day(e.start)} ${timeRange(e)}", color = Color.Gray)
                    if (e.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(e.description)
                    }
                }
            },
            confirmButton = { TextButton({ selected = null }) { Text("Schließen") } },
        )
    }
}

@Composable
private fun EpgRow(e: EpgEntry, running: Boolean, past: Boolean, canWatch: Boolean, onClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .background(if (running) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            clock(e.start), fontWeight = if (running) FontWeight.Bold else FontWeight.Normal,
            color = if (past && !running) Color.Gray else Color.White, modifier = Modifier.width(52.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                e.title, fontWeight = if (running) FontWeight.Bold else FontWeight.Normal,
                color = if (past && !running) Color(0xFF9AA4B2) else Color.White,
            )
            if (running) {
                LinearProgressIndicator(
                    progress = { epgProgress(e) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(3.dp),
                    trackColor = MaterialTheme.colorScheme.background,
                )
            }
            if (e.description.isNotBlank()) {
                Text(
                    e.description, fontSize = 12.sp, color = Color.Gray,
                    maxLines = if (expanded) 20 else 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
        }
        if (canWatch) {
            Icon(
                if (running) Icons.Default.PlayArrow else Icons.Default.History,
                if (running) "Live" else "Catch-up",
                Modifier.padding(start = 8.dp).size(20.dp),
                tint = if (running) MaterialTheme.colorScheme.primary else Gold,
            )
        }
    }
}
