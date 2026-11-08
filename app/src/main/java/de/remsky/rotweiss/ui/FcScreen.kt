@file:OptIn(ExperimentalMaterial3Api::class)

package de.remsky.rotweiss.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import de.remsky.rotweiss.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.remsky.rotweiss.data.FcData
import de.remsky.rotweiss.data.FcMatch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Win = Color(0xFF3CB371)
private val Loss = FcRed
private val Draw = Color(0xFF8A8F98)

private val dateFmt = SimpleDateFormat("EEE, dd.MM. · HH:mm 'Uhr'", Locale.GERMANY)
private val shortDate = SimpleDateFormat("dd.MM.", Locale.GERMANY)
private val timeFmt = SimpleDateFormat("HH:mm", Locale.GERMANY)

private fun sameDay(a: Long, b: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = a }
    val c2 = Calendar.getInstance().apply { timeInMillis = b }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

/** Vereinsnamen kürzen ("1. FC Köln" bleibt, sonst Vorsätze weglassen). */
private fun short(name: String) = name.replace("Borussia Mönchengladbach", "Gladbach")
    .replace("Bayer 04 Leverkusen", "Leverkusen").replace("FC Bayern München", "Bayern")
    .replace("Borussia Dortmund", "Dortmund").replace("Eintracht Frankfurt", "Frankfurt")

private fun countdown(ms: Long): String {
    if (ms <= 0) return "Anpfiff!"
    val min = ms / 60_000
    val d = min / (60 * 24)
    val h = (min / 60) % 24
    val m = min % 60
    return when {
        d > 0 -> "noch $d Tag${if (d == 1L) "" else "e"}, $h Std."
        h > 0 -> "noch $h Std. $m Min."
        else -> "noch $m Min. ${(ms / 1000) % 60} Sek."
    }
}

// ---------------------------------------------------------------- FC-Startseite

@Composable
fun FcScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadFc() }

    // Uhr für Countdown und Live-Erkennung
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val next = vm.fc?.next(now)
            delay(if (next != null && next.kickoff - now < 60 * 60 * 1000) 1000 else 30_000)
        }
    }
    // Am Spieltag während des Spiels alle 2 Minuten den Spielstand aktualisieren
    LaunchedEffect(Unit) {
        while (true) {
            delay(120_000)
            val n = System.currentTimeMillis()
            if (vm.fc?.matches?.any { it.isLive(n) } == true) vm.loadFc(force = true)
        }
    }

    var view by remember { mutableIntStateOf(0) } // 0 = Spielplan, 1 = Tabelle
    val scroll = rememberScrollState()
    val data = vm.fc

    Column(Modifier.fillMaxSize()) {
        TitleBar(
            "FC-Zentrale",
            data?.let { "${it.league} ${it.season}/${(it.season + 1) % 100}" },
        ) {
            IconButton({ vm.loadFc(force = true) }) { Icon(Icons.Default.Refresh, "Aktualisieren") }
            IconButton({ vm.open(Route.Settings) }) { Icon(Icons.Default.Settings, "Einstellungen") }
        }

        when {
            data == null && vm.fcLoading -> CenterLoading("Spieldaten werden geladen …")
            data == null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(vm.fcError ?: "Keine Spieldaten", color = Color.Gray, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button({ vm.loadFc(force = true) }) { Text("Erneut versuchen") }
                Spacer(Modifier.height(24.dp))
                FcChannelStrip(vm)
            }
            else -> {
                val next = data.next(now)
                val last = data.last(now)
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 24.dp)) {
                    FcHero()
                    if (next != null) {
                        NextMatchCard(vm, data, next, now)
                    } else {
                        Box(Modifier.padding(16.dp)) { FcChannelStrip(vm) }
                    }
                    if (last != null) LastMatchCard(data, last)

                    run {
                        Row(
                            Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(view == 0, { view = 0 }, { Text("Spielplan") })
                            FilterChip(view == 1, { view = 1 }, { Text("Tabelle") })
                        }
                    }

                    if (view == 0) {
                        data.matches.forEach { m -> ScheduleRow(data, m, now) }
                    } else {
                        TableHeader()
                        data.table.forEach { r ->
                            val fc = r.teamId == data.teamId
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (fc) FcRed.copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable(onClick = {})
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${r.pos}", Modifier.width(28.dp), fontWeight = FontWeight.Bold, color = zoneColor(r.pos, data.table.size))
                                Text(
                                    short(r.name), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (fc) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text("${r.played}", Modifier.width(30.dp), textAlign = TextAlign.End, color = Color.Gray)
                                Text("${r.won}-${r.draw}-${r.lost}", Modifier.width(62.dp), textAlign = TextAlign.End, color = Color.Gray, fontSize = 13.sp)
                                Text(
                                    if (r.diff > 0) "+${r.diff}" else "${r.diff}",
                                    Modifier.width(40.dp), textAlign = TextAlign.End, color = Color.Gray,
                                )
                                Text("${r.points}", Modifier.width(36.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun zoneColor(pos: Int, size: Int): Color = when {
    pos <= 4 -> Color(0xFF4F8CFF)
    pos <= 6 -> Color(0xFFFFA94D)
    size >= 18 && pos == size - 2 -> Color(0xFFFFC145)
    size >= 18 && pos > size - 2 -> FcRed
    else -> Color.White
}

@Composable
private fun TableHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        val c = Color.Gray
        Text("#", Modifier.width(28.dp), color = c, fontSize = 12.sp)
        Text("Verein", Modifier.weight(1f), color = c, fontSize = 12.sp)
        Text("Sp", Modifier.width(30.dp), textAlign = TextAlign.End, color = c, fontSize = 12.sp)
        Text("S-U-N", Modifier.width(62.dp), textAlign = TextAlign.End, color = c, fontSize = 12.sp)
        Text("Diff", Modifier.width(40.dp), textAlign = TextAlign.End, color = c, fontSize = 12.sp)
        Text("Pkt", Modifier.width(36.dp), textAlign = TextAlign.End, color = c, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------- Nächstes Spiel / Spieltag-Modus

@Composable
private fun NextMatchCard(vm: AppViewModel, data: FcData, m: FcMatch, now: Long) {
    val live = m.isLive(now)
    val today = sameDay(m.kickoff, now)
    val headline = when {
        live -> "LIVE · läuft gerade"
        today -> "HEUTE IST SPIELTAG!"
        else -> "Nächstes Spiel · ${m.matchday}. Spieltag"
    }

    Column(
        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(
                if (live || today) Brush.verticalGradient(listOf(FcRed, Color(0xFF8E0B10)))
                else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface))
            )
            .padding(16.dp),
    ) {
        Text(headline, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (live || today) Color.White else FcRed)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamName(short(m.home), m.homeId == data.teamId, Modifier.weight(1f), TextAlign.Start)
            Text(
                if (live && m.goalsHome != null) "${m.goalsHome} : ${m.goalsAway}" else "–",
                fontSize = if (live) 30.sp else 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            TeamName(short(m.away), m.awayId == data.teamId, Modifier.weight(1f), TextAlign.End)
        }
        Spacer(Modifier.height(10.dp))
        Text(dateFmt.format(Date(m.kickoff)), color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        if (m.stadium.isNotBlank()) Text(m.stadium, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        if (!live) {
            Spacer(Modifier.height(6.dp))
            Text(countdown(m.kickoff - now), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        // Spieltag-Modus: Schnellstart der FC-Sender
        if (live || today || m.kickoff - now < 3 * 60 * 60 * 1000) {
            Spacer(Modifier.height(14.dp))
            FcChannelStrip(vm, onRed = live || today)
        }
    }
}

@Composable
private fun TeamName(name: String, isFc: Boolean, modifier: Modifier, align: TextAlign) {
    Text(
        name, modifier, textAlign = align, maxLines = 2, overflow = TextOverflow.Ellipsis,
        fontWeight = if (isFc) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 17.sp,
    )
}

/** Knöpfe für die ausgewählten FC-Sender – ein Tipp startet direkt das Spiel. */
@Composable
fun FcChannelStrip(vm: AppViewModel, onRed: Boolean = false) {
    val ctx = LocalContext.current
    val list = vm.fcChannelList
    if (list.isEmpty()) {
        OutlinedButton({ vm.open(Route.FcChannels) }) {
            Icon(Icons.Default.SportsSoccer, null)
            Spacer(Modifier.width(6.dp))
            Text("FC-Sender festlegen")
        }
        return
    }
    Column {
    Text("Zum Spiel:", fontSize = 12.sp, color = if (onRed) Color.White else Color.Gray)
    Spacer(Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.streamId }) { ch ->
            Row(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (onRed) Color.White else FcRed)
                    .clickable { vm.playChannel(list, ch); ctx.openPlayer() }
                    .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChannelLogo(ch, 40, 28)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp), tint = if (onRed) FcRed else Color.White)
                Text(
                    ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, color = if (onRed) FcRed else Color.White,
                    modifier = Modifier.widthIn(max = 150.dp),
                )
            }
        }
    }
    }
}

// ---------------------------------------------------------------- Letztes Ergebnis

@Composable
private fun LastMatchCard(data: FcData, m: FcMatch) {
    val (res, color) = outcome(data, m)
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).background(color, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(res, fontWeight = FontWeight.Bold, color = Color.White) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Letztes Spiel · ${shortDate.format(Date(m.kickoff))}", color = Color.Gray, fontSize = 12.sp)
            Text("${short(m.home)} – ${short(m.away)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${m.goalsHome ?: "-"} : ${m.goalsAway ?: "-"}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

private fun outcome(data: FcData, m: FcMatch): Pair<String, Color> {
    val h = m.goalsHome ?: return "–" to Draw
    val a = m.goalsAway ?: return "–" to Draw
    val fcGoals = if (m.homeId == data.teamId) h else a
    val other = if (m.homeId == data.teamId) a else h
    return when {
        fcGoals > other -> "S" to Win
        fcGoals < other -> "N" to Loss
        else -> "U" to Draw
    }
}

// ---------------------------------------------------------------- Spielplan

@Composable
private fun ScheduleRow(data: FcData, m: FcMatch, now: Long) {
    val home = m.homeId == data.teamId
    val opponent = short(if (home) m.away else m.home)
    val live = m.isLive(now)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = {}).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${m.matchday}.", Modifier.width(30.dp), color = Color.Gray, fontSize = 13.sp)
        Column(Modifier.weight(1f)) {
            Text(
                (if (home) "vs. " else "@ ") + opponent,
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
            )
            Text(
                "${shortDate.format(Date(m.kickoff))} ${timeFmt.format(Date(m.kickoff))} · ${if (home) "Heim" else "Auswärts"}",
                color = Color.Gray, fontSize = 12.sp,
            )
        }
        when {
            live -> Text(
                "LIVE ${m.goalsHome ?: 0}:${m.goalsAway ?: 0}", color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(FcRed, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
            m.finished || m.goalsHome != null -> {
                val (res, color) = outcome(data, m)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${m.goalsHome}:${m.goalsAway}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(22.dp).border(1.5.dp, color, RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(res, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
            else -> Text(if (home) "H" else "A", color = Color.Gray)
        }
    }
}

// ---------------------------------------------------------------- FC-Sender auswählen

@Composable
fun FcChannelsScreen(vm: AppViewModel) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val list = vm.channels
        .filter { !vm.isLocked("live", it.categoryId) && (q.isEmpty() || it.name.contains(q, ignoreCase = true)) }
        .sortedByDescending { vm.isFcChannel(it.streamId) }

    Column(Modifier.fillMaxSize()) {
        TitleBar("FC-Sender", "Erscheinen am Spieltag als Schnellstart", onBack = vm::back)
        SearchField(query, { query = it }, "Sender suchen (z. B. Sky, DAZN, WDR)")
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(list, key = { it.streamId }) { ch ->
                val on = vm.isFcChannel(ch.streamId)
                Row(
                    Modifier.fillMaxWidth().clickable { vm.toggleFcChannel(ch.streamId) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChannelLogo(ch)
                    Spacer(Modifier.width(12.dp))
                    Text(ch.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Checkbox(on, { vm.toggleFcChannel(ch.streamId) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Kopfbereich mit Foto

/** Foto im Rahmen, darüber eine Wimpelkette, darunter Stadion und Dom als Silhouette. */
@Composable
private fun FcHero() {
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF3A0A0E), MaterialTheme.colorScheme.background))),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Wimpelkette in Rot und Weiß
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val w = 26.dp.toPx()
            val h = size.height
            var x = 0f
            var i = 0
            while (x < size.width) {
                val p = Path().apply {
                    moveTo(x, 0f); lineTo(x + w, 0f); lineTo(x + w / 2, h); close()
                }
                drawPath(p, if (i % 2 == 0) FcRed else Color.White)
                x += w
                i++
            }
        }
        Spacer(Modifier.height(10.dp))
        Image(
            painterResource(R.drawable.fan_faces), null,
            Modifier.height(170.dp).aspectRatio(876f / 500f)
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, Color.White, RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.Bottom) {
            Image(painterResource(R.drawable.fc_stadium), null, Modifier.height(62.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.weight(1f))
            Image(painterResource(R.drawable.fc_dom), null, Modifier.height(62.dp), contentScale = ContentScale.Fit)
        }
    }
}
