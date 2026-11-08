@file:OptIn(ExperimentalMaterial3Api::class)

package de.remsky.rotweiss.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import de.remsky.rotweiss.data.Channel
import kotlinx.coroutines.delay

private val PreviewBorder = Color(0xFFFF9800)

/** Drei-Spalten-Ansicht (Kategorien · Sender · Vorschau) am Fernseher und auf großen Tablets. */
@Composable
fun useWideLive(): Boolean {
    val c = LocalConfiguration.current
    return TvMode.enabled || (c.screenWidthDp >= 900 && c.screenHeightDp >= 600)
}

@Composable
fun LiveWideScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val tv = TvMode.enabled
    var favOnly by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Channel?>(null) }
    var suspended by remember { mutableStateOf(false) }

    // Vorschau anhalten, sobald die App nicht im Vordergrund ist (spart die zweite Verbindung)
    val owner = ctx as? LifecycleOwner
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_STOP) suspended = true
            if (ev == Lifecycle.Event.ON_START) suspended = false
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }

    val cats = vm.visibleCategories("live", vm.liveCategories)
    val counts = remember(vm.channels) { vm.channels.groupingBy { it.categoryId }.eachCount() }
    val list = vm.visibleChannels.let { l -> if (favOnly) l.filter { vm.isFavorite("live:${it.streamId}") } else l }

    fun play(ch: Channel) {
        suspended = true            // Vorschau-Stream sofort beenden, bevor der Vollbild-Player startet
        vm.playChannel(list, ch)
        ctx.openPlayer()
    }

    Row(Modifier.fillMaxSize()) {
        // ---------------------------------------------------- Kategorien
        Column(Modifier.weight(0.26f).fillMaxHeight()) {
            Spacer(Modifier.height(8.dp))
            SearchField(vm.liveQuery, { vm.liveQuery = it }, "Sender suchen")
            Spacer(Modifier.height(6.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    CatRow("Alle", cats.sumOf { counts[it.id] ?: 0 }, vm.liveCategory == null && !favOnly) {
                        vm.liveCategory = null; favOnly = false
                    }
                }
                item {
                    CatRow("★ Favoriten", vm.channels.count { vm.isFavorite("live:${it.streamId}") }, favOnly) {
                        vm.liveCategory = null; favOnly = true
                    }
                }
                items(cats, key = { it.id }) { cat ->
                    CatRow(cat.name, counts[cat.id] ?: 0, vm.liveCategory == cat.id && !favOnly) {
                        vm.liveCategory = cat.id; favOnly = false
                    }
                }
            }
        }

        // ---------------------------------------------------- Senderliste
        Box(Modifier.weight(0.38f).fillMaxHeight()) {
            if (list.isEmpty()) {
                CenterText(if (favOnly) "Noch keine Lieblingssender." else "Keine Sender gefunden")
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
                    items(list, key = { it.streamId }) { ch ->
                        WideChannelRow(
                            vm, ch, isSelected = selected?.streamId == ch.streamId,
                            onFocus = { selected = ch },
                            onClick = { if (!tv && selected?.streamId != ch.streamId) selected = ch else play(ch) },
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------- Vorschau + Info
        Column(
            Modifier.weight(0.36f).fillMaxHeight().padding(start = 8.dp, end = 12.dp, top = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            LivePreview(vm, selected, suspended, vm.previewMode, Modifier.fillMaxWidth())
            val ch = selected
            if (ch == null) {
                Text(
                    "Wähle einen Sender – hier läuft die Vorschau.",
                    color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                LaunchedEffect(ch.streamId) { vm.requestNowNext(ch.streamId) }
                val epg = vm.nowNext[ch.streamId].orEmpty()
                val now = epg.firstOrNull()
                val next = epg.getOrNull(1)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(ch, 44, 32)
                    Spacer(Modifier.width(10.dp))
                    Text(ch.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                if (now != null) {
                    Text("Jetzt  ${timeRange(now)}", fontSize = 12.sp, color = Color.Gray)
                    Text(now.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(
                        progress = { epgProgress(now) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).height(4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    if (now.description.isNotBlank()) {
                        Text(now.description, fontSize = 12.sp, color = Color(0xFFB8C4D6), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    if (next != null) {
                        Spacer(Modifier.height(6.dp))
                        Text("Danach  ${clock(next.start)}  ${next.title}", fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text("Keine Programminfo", fontSize = 13.sp, color = Color.Gray)
                }
                Spacer(Modifier.height(12.dp))
                val fav = vm.isFavorite("live:${ch.streamId}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button({ play(ch) }, Modifier.focusHighlight(RoundedCornerShape(20.dp))) { Text("Vollbild") }
                    OutlinedButton(
                        { vm.open(Route.ChannelEpg(ch)) },
                        Modifier.focusHighlight(RoundedCornerShape(20.dp)),
                    ) {
                        if (ch.hasArchive) {
                            Icon(Icons.Default.History, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(if (ch.hasArchive) "Archiv" else "Programm")
                    }
                    IconButton({ vm.toggleFavorite("live:${ch.streamId}") }, Modifier.focusHighlight(RoundedCornerShape(20.dp))) {
                        Icon(
                            if (fav) Icons.Default.Star else Icons.Default.StarBorder, "Favorit",
                            tint = if (fav) Gold else Color.Gray,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun CatRow(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            .background(
                if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .focusHighlight(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), fontSize = 13.sp, color = Color.Gray)
    }
}

@Composable
internal fun WideChannelRow(vm: AppViewModel, ch: Channel, isSelected: Boolean, onFocus: () -> Unit, onClick: () -> Unit) {
    LaunchedEffect(ch.streamId) { vm.requestNowNext(ch.streamId) }
    val now = vm.nowNext[ch.streamId].orEmpty().firstOrNull()
    val fav = vm.isFavorite("live:${ch.streamId}")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .onFocusChanged { if (it.isFocused || it.hasFocus) onFocus() }
            .focusHighlight(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(ch, 52, 38)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                now?.let { "${clock(it.start)}  ${it.title}" } ?: "Keine Programminfo",
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Color(0xFFB8C4D6),
            )
            if (now != null) {
                LinearProgressIndicator(
                    progress = { epgProgress(now) },
                    modifier = Modifier.fillMaxWidth(0.9f).padding(top = 3.dp).height(2.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
        if (ch.hasArchive) Icon(Icons.Default.History, "Archiv", Modifier.size(14.dp), tint = Color.Gray)
        if (fav) Icon(Icons.Default.Star, "Favorit", Modifier.padding(start = 4.dp).size(14.dp), tint = Gold)
    }
}

/**
 * Live-Vorschau. Startet erst, wenn der Fokus kurz auf dem Sender ruht, und gibt die
 * Verbindung frei, sobald [suspended] gesetzt ist (Vollbild/App im Hintergrund).
 * [mode]: 0 aus · 1 stumm · 2 mit Ton.
 */
@OptIn(UnstableApi::class)
@Composable
private fun LivePreview(vm: AppViewModel, channel: Channel?, suspended: Boolean, mode: Int, modifier: Modifier) {
    val ctx = LocalContext.current
    val enabled = mode > 0
    val player = remember { ExoPlayer.Builder(ctx).build().apply { playWhenReady = true } }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(mode) { player.volume = if (mode == 2) 1f else 0f }

    var buffering by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // (Versuch, Sender-ID): erst HLS, bei Fehler einmal .ts
    var retry by remember { mutableStateOf(0 to -1) }
    val currentId by rememberUpdatedState(channel?.streamId ?: -1)
    val tryNo = if (retry.second == (channel?.streamId ?: -1)) retry.first else 0

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
            override fun onPlayerError(error: PlaybackException) {
                val id = currentId
                val n = if (retry.second == id) retry.first else 0
                if (n < 1) retry = (n + 1) to id else failed = true
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    LaunchedEffect(channel?.streamId, suspended, enabled, tryNo) {
        failed = false
        val api = vm.api
        if (channel == null || suspended || !enabled || api == null) {
            player.stop(); player.clearMediaItems(); buffering = false
            return@LaunchedEffect
        }
        if (tryNo == 0) delay(600)
        val ext = if (tryNo == 0) "m3u8" else "ts"
        val url = api.liveUrl(channel.streamId, ext)
        val item = MediaItem.Builder().setUri(url)
            .apply { if (ext == "m3u8") setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.aspectRatio(16f / 9f).clip(shape).background(Color.Black).border(3.dp, PreviewBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (channel != null && enabled && !suspended) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        isFocusable = false
                        isFocusableInTouchMode = false
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        when {
            !enabled -> Text("Vorschau ist aus (Einstellungen)", color = Color.Gray, fontSize = 13.sp)
            channel == null -> Text("Sender wählen", color = Color.Gray, fontSize = 13.sp)
            failed -> Text("Keine Vorschau möglich", color = Color.Gray, fontSize = 13.sp)
            buffering && !suspended -> CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 3.dp)
        }
    }
}
