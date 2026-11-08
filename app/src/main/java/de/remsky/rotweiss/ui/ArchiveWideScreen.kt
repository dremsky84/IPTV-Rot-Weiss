@file:OptIn(ExperimentalMaterial3Api::class)

package de.remsky.rotweiss.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.remsky.rotweiss.data.Channel
import de.remsky.rotweiss.data.EpgEntry
import kotlinx.coroutines.delay

/** Archiv am Fernseher/Tablet: Kategorien · Archiv-Sender · Tage und Sendungen. */
@Composable
fun ArchiveWideScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var category by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Channel?>(null) }

    val all = vm.archiveChannels
    val counts = remember(all) { all.groupingBy { it.categoryId }.eachCount() }
    val cats = vm.visibleCategories("live", vm.liveCategories).filter { (counts[it.id] ?: 0) > 0 }
    val list = if (category == null) all else all.filter { it.categoryId == category }

    Row(Modifier.fillMaxSize()) {
        // ---------------------------------------------------- Kategorien
        Column(Modifier.weight(0.26f).fillMaxHeight()) {
            Spacer(Modifier.height(8.dp))
            SearchField(vm.archiveQuery, { vm.archiveQuery = it }, "Sender suchen")
            Spacer(Modifier.height(6.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                item { CatRow("Alle", all.size, category == null) { category = null } }
                items(cats, key = { it.id }) { cat ->
                    CatRow(cat.name, counts[cat.id] ?: 0, category == cat.id) { category = cat.id }
                }
            }
        }

        // ---------------------------------------------------- Archiv-Sender
        Box(Modifier.weight(0.34f).fillMaxHeight()) {
            if (list.isEmpty()) {
                CenterText(
                    if (vm.channels.none { it.hasArchive }) "Dein Anbieter bietet für keinen Sender ein Archiv an."
                    else "Keine Sender gefunden",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
                    items(list, key = { it.streamId }) { ch ->
                        WideChannelRow(
                            vm, ch, isSelected = selected?.streamId == ch.streamId,
                            onFocus = { selected = ch },
                            onClick = { selected = ch },
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------- Tage und Sendungen
        Column(Modifier.weight(0.40f).fillMaxHeight().padding(start = 8.dp, end = 12.dp, top = 12.dp)) {
            val ch = selected
            if (ch == null) {
                Text("Wähle links einen Sender.", color = Color.Gray, fontSize = 14.sp)
            } else {
                ArchivePrograms(vm, ch) { e, running ->
                    if (running) vm.playChannel(listOf(ch), ch) else vm.playCatchup(ch, e)
                    ctx.openPlayer()
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ArchivePrograms(vm: AppViewModel, ch: Channel, onPlay: (EpgEntry, Boolean) -> Unit) {
    var epg by remember(ch.streamId) { mutableStateOf<List<EpgEntry>?>(null) }
    var dayKey by remember(ch.streamId) { mutableStateOf<String?>(null) }

    // Kurz warten, damit schnelles Durchschalten nicht jede Anfrage auslöst
    LaunchedEffect(ch.streamId) {
        delay(400)
        epg = try { vm.fullEpg(ch.streamId) } catch (_: Exception) { emptyList() }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        ChannelLogo(ch, 44, 32)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(ch.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (ch.archiveDays > 0) "Archiv: ${ch.archiveDays} Tage" else "Archiv",
                fontSize = 12.sp, color = Color.Gray,
            )
        }
    }
    Spacer(Modifier.height(8.dp))

    val entries = epg
    when {
        entries == null -> CenterLoading("Programm wird geladen …")
        else -> {
            val now = System.currentTimeMillis() / 1000
            val oldest = if (ch.archiveDays > 0) now - (ch.archiveDays + 1) * 86400L else 0L
            val past = entries.filter { it.start <= now && it.start >= oldest }
            val byDay = past.groupBy { day(it.start) }
            // neueste Tage zuerst
            val days = byDay.keys.toList().reversed()
            if (days.isEmpty()) {
                CenterText("Für diesen Sender gibt es kein Programm im Archiv.")
            } else {
                val today = day(now)
                val current = dayKey?.takeIf { it in byDay } ?: days.first()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(days, key = { it }) { d ->
                        FilterChip(
                            selected = d == current, onClick = { dayKey = d },
                            label = { Text(if (d == today) "Heute" else d, maxLines = 1) },
                            modifier = Modifier.focusHighlight(RoundedCornerShape(8.dp)),
                        )
                    }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(byDay[current].orEmpty(), key = { it.start }) { e ->
                        val running = e.start <= now && e.end > now
                        ArchiveEntryRow(e, running) { onPlay(e, running) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveEntryRow(e: EpgEntry, running: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp)
            .background(
                if (running) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .focusHighlight(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(clock(e.start), Modifier.width(48.dp), fontSize = 13.sp, color = Color(0xFFB8C4D6))
        Column(Modifier.weight(1f)) {
            Text(e.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e.description.isNotBlank()) {
                Text(e.description, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            if (running) Icons.Default.PlayArrow else Icons.Default.History,
            if (running) "Live" else "Archiv",
            Modifier.padding(start = 8.dp).size(18.dp),
            tint = if (running) MaterialTheme.colorScheme.primary else Gold,
        )
    }
}
