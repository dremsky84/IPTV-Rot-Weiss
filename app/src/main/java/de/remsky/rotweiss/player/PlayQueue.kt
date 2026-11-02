package de.remsky.rotweiss.player

import de.remsky.rotweiss.data.LocalStore
import de.remsky.rotweiss.data.XtreamApi

/** Ein abspielbarer Eintrag: Sender, Film, Folge oder Catch-up-Sendung. */
data class PlayEntry(
    val title: String,
    val subtitle: String = "",
    val urls: List<String>,           // erste Adresse zuerst, weitere als Ausweich
    val live: Boolean,
    val channelId: Int = 0,           // für Senderinfo/EPG im Player
    val resumeKey: String? = null,    // "movie:<id>" / "episode:<id>" → Fortsetzen
    val image: String? = null,
    val seriesId: Int = 0,
)

/**
 * Übergabe an den Player. Bei Live-TV enthält die Liste alle Sender der aktuellen Ansicht
 * (Wischen ↑↓), bei Serien die restlichen Folgen der Staffel (automatisch weiter).
 */
object PlayQueue {
    var api: XtreamApi? = null
    var store: LocalStore? = null
    var entries: List<PlayEntry> = emptyList()
    var index: Int = 0
}
