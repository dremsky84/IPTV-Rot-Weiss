@file:OptIn(ExperimentalMaterial3Api::class)

package de.remsky.rotweiss.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class PinAction { CREATE, CONFIRM, UNLOCK, CHANGE_OLD, REMOVE, OPEN_LOCKS }

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var action by remember { mutableStateOf<PinAction?>(null) }
    var newPin by remember { mutableStateOf("") }
    var lockType by remember { mutableStateOf("live") }
    var confirmLogout by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TitleBar("Einstellungen", onBack = vm::back)

        // ------------------------------------------------ Konto
        SectionHeader("Konto")
        vm.account?.let { a ->
            Info("Status", if (a.status.equals("Active", true)) "Aktiv" else a.status)
            Info("Gültig bis", a.expires)
            Info("Verbindungen", "${a.activeConnections} von ${a.maxConnections} genutzt")
        }
        vm.savedCredentials?.let { Info("Server", it.baseUrl) }
        Item(Icons.Default.Refresh, "Listen aktualisieren", "Sender, Filme und Serien neu laden") { vm.refreshAll() }

        // ------------------------------------------------ Anzeige
        SectionHeader("Anzeige")
        Item(
            Icons.Default.LiveTv, "Live-Vorschau (Fernseher/Tablet)",
            "${listOf("Aus", "Stumm", "Mit Ton")[vm.previewMode]} · Tippen zum Wechseln",
        ) { vm.cyclePreviewMode() }

        // ------------------------------------------------ FC
        SectionHeader("FC")
        Item(Icons.Default.SportsSoccer, "FC-Sender festlegen", "${vm.fcChannels.size} ausgewählt · erscheinen am Spieltag als Schnellstart") {
            vm.open(Route.FcChannels)
        }

        // ------------------------------------------------ Kindersperre
        SectionHeader("Kindersperre")
        if (!vm.pinSet) {
            Text(
                "Lege eine PIN fest und wähle Kategorien aus, die ohne PIN ausgeblendet werden.",
                color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Item(Icons.Default.Lock, "PIN festlegen") { action = PinAction.CREATE }
        } else {
            if (vm.unlocked) {
                Item(Icons.Default.LockOpen, "Entsperrt", "Gesperrte Kategorien sind gerade sichtbar · Tippen zum Sperren") { vm.lockAgain() }
            } else {
                Item(Icons.Default.Lock, "Gesperrt", "${vm.locked.size} Kategorien ausgeblendet · Tippen zum Entsperren") {
                    action = PinAction.UNLOCK
                }
            }
            Item(null, "Gesperrte Kategorien: Live-TV") { lockType = "live"; action = PinAction.OPEN_LOCKS }
            Item(null, "Gesperrte Kategorien: Filme") { lockType = "movie"; action = PinAction.OPEN_LOCKS }
            Item(null, "Gesperrte Kategorien: Serien") { lockType = "series"; action = PinAction.OPEN_LOCKS }
            Item(null, "PIN ändern") { action = PinAction.CHANGE_OLD }
            Item(null, "Kindersperre entfernen") { action = PinAction.REMOVE }
        }

        // ------------------------------------------------ Abmelden
        SectionHeader("App")
        Item(Icons.Default.Logout, "Abmelden", "Zugangsdaten von diesem Gerät löschen") { confirmLogout = true }
        Text(
            "IPTV Rot-Weiss 1.0 · Wischen ↑↓ im Player wechselt den Sender, Bild-in-Bild über die Home-Taste.",
            color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(16.dp),
        )
    }

    when (action) {
        PinAction.CREATE -> PinDialog("Neue PIN festlegen", { action = null }) { p ->
            newPin = p; action = PinAction.CONFIRM; false
        }
        PinAction.CONFIRM -> PinDialog("PIN wiederholen", { action = null; newPin = "" }) { p ->
            if (p == newPin) { vm.setPin(p); newPin = ""; true } else false
        }
        PinAction.UNLOCK -> PinDialog("PIN eingeben", { action = null }) { p -> vm.unlock(p) }
        PinAction.CHANGE_OLD -> PinDialog("Aktuelle PIN", { action = null }) { p ->
            if (vm.checkPin(p)) { action = PinAction.CREATE; false } else false
        }
        PinAction.REMOVE -> PinDialog("PIN zum Entfernen eingeben", { action = null }) { p ->
            if (vm.checkPin(p)) { vm.setPin(null); vm.setLockedAll(emptySet()); true } else false
        }
        PinAction.OPEN_LOCKS -> {
            if (vm.unlocked) {
                LaunchedEffect(Unit) { action = null; vm.open(Route.LockSettings(lockType)) }
            } else {
                PinDialog("PIN eingeben", { action = null }) { p ->
                    if (vm.unlock(p)) { vm.open(Route.LockSettings(lockType)); true } else false
                }
            }
        }
        null -> {}
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Abmelden?") },
            text = { Text("Server, Benutzername und Passwort werden von diesem Gerät gelöscht.") },
            confirmButton = { TextButton({ confirmLogout = false; vm.logout() }) { Text("Abmelden") } },
            dismissButton = { TextButton({ confirmLogout = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, color = Color.Gray, modifier = Modifier.width(120.dp))
        Text(value)
    }
}

@Composable
private fun Item(icon: ImageVector?, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.Gray)
            Spacer(Modifier.width(16.dp))
        } else {
            Spacer(Modifier.width(40.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
    }
}

// ---------------------------------------------------------------- Kategorien sperren

@Composable
fun LockSettingsScreen(vm: AppViewModel, type: String) {
    LaunchedEffect(type) {
        if (type == "movie") vm.ensureMovies()
        if (type == "series") vm.ensureSeries()
    }
    val cats = when (type) {
        "movie" -> vm.movieCategories
        "series" -> vm.seriesCategories
        else -> vm.liveCategories
    }
    val title = when (type) { "movie" -> "Filme"; "series" -> "Serien"; else -> "Live-TV" }
    val loading = (type == "movie" && vm.moviesLoading) || (type == "series" && vm.seriesLoading)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Sperren: $title", fontWeight = FontWeight.Bold)
                    Text("Angehakte Kategorien brauchen die PIN", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            },
            navigationIcon = { IconButton(vm::back) { Icon(Icons.Default.ArrowBack, "Zurück") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        when {
            cats.isEmpty() && loading -> CenterLoading()
            cats.isEmpty() -> CenterText("Keine Kategorien gefunden")
            else -> LazyColumn {
                items(cats, key = { it.id }) { c ->
                    val checked = "$type:${c.id}" in vm.locked
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.toggleLock(type, c.id) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked, { vm.toggleLock(type, c.id) })
                        Text(c.name)
                    }
                }
            }
        }
    }
}
