package de.remsky.rotweiss

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import de.remsky.rotweiss.data.EpgEntry
import de.remsky.rotweiss.data.WatchEntry
import de.remsky.rotweiss.player.PlayEntry
import de.remsky.rotweiss.player.PlayQueue
import de.remsky.rotweiss.ui.FcRed
import de.remsky.rotweiss.ui.TvMode
import de.remsky.rotweiss.ui.TvTheme
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlayerActivity : ComponentActivity() {

    companion object {
        /** Nur ein Player gleichzeitig (z. B. wenn einer im Bild-in-Bild läuft). */
        private var current: WeakReference<PlayerActivity>? = null

        /** Tasten, die der Player selbst übernimmt (fernbedienung/Tastatur). */
        private val REMOTE_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO,
        )
    }

    private lateinit var player: ExoPlayer
    private var entries: List<PlayEntry> = emptyList()

    private var index by mutableIntStateOf(0)
    private var urlIndex by mutableIntStateOf(0)
    private var inPip by mutableStateOf(false)

    // Einblendung (Senderinfo bzw. Fortschrittsleiste); der Zähler startet den Ausblend-Timer neu
    private var overlay by mutableStateOf(true)
    private var overlayTick by mutableIntStateOf(0)
    private var position by mutableLongStateOf(0L)
    private var duration by mutableLongStateOf(0L)
    private var paused by mutableStateOf(false)

    private val entry: PlayEntry? get() = entries.getOrNull(index)

    private val pipSupported: Boolean by lazy {
        !TvMode.enabled && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TvMode.detect(this)

        current?.get()?.takeIf { it != this }?.finish()
        current = WeakReference(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        entries = PlayQueue.entries
        if (entries.isEmpty()) { finish(); return }
        index = PlayQueue.index.coerceIn(entries.indices)

        player = ExoPlayer.Builder(this).build()

        setContent {
            TvTheme {
                var buffering by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }
                var controlsVisible by remember { mutableStateOf(true) }
                var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
                var ended by remember { mutableStateOf(false) }
                val tv = TvMode.enabled
                val e = entries[index]

                // Eintrag laden; bei Fehler nächste Adresse (z. B. HLS → TS)
                LaunchedEffect(index, urlIndex) {
                    error = null
                    ended = false
                    val url = e.urls[urlIndex.coerceIn(e.urls.indices)]
                    val item = MediaItem.Builder().setUri(url)
                        .apply { if (url.endsWith(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }
                        .build()
                    val start = e.resumeKey?.let { PlayQueue.store?.position(it) } ?: 0L
                    if (start > 0) player.setMediaItem(item, start) else player.setMediaItem(item)
                    player.prepare()
                    player.play()
                }

                DisposableEffect(Unit) {
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            buffering = state == Player.STATE_BUFFERING
                            if (state == Player.STATE_ENDED) {
                                val cur = entry
                                if (cur != null && !cur.live) {
                                    cur.resumeKey?.let { PlayQueue.store?.removeHistory(it) }
                                    if (index < entries.lastIndex) { index += 1; urlIndex = 0 } else ended = true
                                }
                            }
                        }

                        override fun onPlayerError(err: PlaybackException) {
                            val cur = entry ?: return
                            if (err.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                                player.seekToDefaultPosition()
                                player.prepare()
                            } else if (urlIndex < cur.urls.lastIndex) {
                                urlIndex += 1
                            } else {
                                error = if (cur.live) "Sender nicht verfügbar (${err.errorCodeName})"
                                else "Wiedergabe nicht möglich (${err.errorCodeName})"
                            }
                        }
                    }
                    player.addListener(listener)
                    onDispose { player.removeListener(listener) }
                }

                // Senderinfo + Jetzt/Danach bei jedem Eintrag
                LaunchedEffect(index) {
                    epg = emptyList()
                    showOverlay()
                    if (e.live) {
                        val api = PlayQueue.api
                        if (api != null) {
                            epg = try {
                                val now = System.currentTimeMillis() / 1000
                                api.shortEpg(e.channelId, 3).filter { it.end > now }.take(2)
                            } catch (_: Exception) { emptyList() }
                        }
                    }
                }

                // Einblendung nach ein paar Sekunden ausblenden (bei Pause im Film bleibt sie stehen)
                LaunchedEffect(overlayTick) {
                    delay(4000)
                    if (e.live || !paused) overlay = false
                }

                // Position für die Fortschrittsleiste
                LaunchedEffect(index) {
                    while (true) {
                        position = player.currentPosition
                        duration = player.duration.let { if (it == C.TIME_UNSET) 0L else it }
                        paused = !player.playWhenReady
                        delay(500)
                    }
                }

                // Fortschritt regelmäßig speichern
                LaunchedEffect(index) {
                    if (!e.live && e.resumeKey != null) {
                        while (true) { delay(10_000); savePosition() }
                    }
                }

                val overlayVisible = !inPip && (if (e.live || tv) overlay else controlsVisible)

                Box(
                    Modifier.fillMaxSize().background(Color.Black).then(
                        if (e.live) Modifier.pointerInput(Unit) {
                            detectTapGestures { if (overlay) overlay = false else showOverlay() }
                        }.pointerInput(Unit) {
                            var drag = 0f
                            detectVerticalDragGestures(
                                onDragStart = { drag = 0f },
                                onDragEnd = {
                                    if (drag < -120) zap(+1) else if (drag > 120) zap(-1)
                                },
                            ) { _, amount -> drag += amount }
                        } else Modifier
                    ),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = this@PlayerActivity.player
                                setShowNextButton(false)
                                setShowPreviousButton(false)
                                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                                    controlsVisible = v == android.view.View.VISIBLE
                                })
                            }
                        },
                        update = { view ->
                            // Am Fernseher steuert die App selbst (eigene Leiste), am Handy der Standard-Controller
                            view.useController = !e.live && !inPip && !tv
                            if (e.live || inPip || tv) view.hideController()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (buffering && error == null && !inPip) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                    }
                    error?.let {
                        Text(
                            it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            fontSize = if (inPip) 11.sp else 15.sp,
                        )
                    }

                    // Handy: obere Leiste mit Zurück, Titel, Bild-in-Bild
                    if (!tv) {
                        AnimatedVisibility(overlayVisible, Modifier.align(Alignment.TopStart).fillMaxWidth(), enter = fadeIn(), exit = fadeOut()) {
                            Row(
                                Modifier.fillMaxWidth().background(Color(0x88000000)).padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton({ finish() }) { Icon(Icons.Default.ArrowBack, "Zurück", tint = Color.White) }
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (e.subtitle.isNotBlank()) {
                                        Text(e.subtitle, color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (!e.live && index < entries.lastIndex) {
                                    IconButton({ savePosition(); index += 1; urlIndex = 0 }) {
                                        Icon(Icons.Default.SkipNext, "Nächste Folge", tint = Color.White)
                                    }
                                }
                                if (pipSupported) {
                                    IconButton({ enterPip() }) {
                                        Icon(Icons.Default.PictureInPictureAlt, "Bild-in-Bild", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // Live: Senderinfo mit Jetzt/Danach
                    if (e.live) {
                        AnimatedVisibility(overlayVisible, Modifier.align(Alignment.BottomStart).padding(20.dp), enter = fadeIn(), exit = fadeOut()) {
                            Column(
                                Modifier.widthIn(max = 560.dp).background(Color(0xCC000000), RoundedCornerShape(12.dp)).padding(16.dp, 10.dp),
                            ) {
                                Text("${index + 1}  ${e.title}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                epg.getOrNull(0)?.let {
                                    Text("Jetzt  ${hm(it.start)}–${hm(it.end)}  ${it.title}", color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                epg.getOrNull(1)?.let {
                                    Text("Danach  ${hm(it.start)}  ${it.title}", color = Color.LightGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (entries.size > 1) {
                                    Text(
                                        if (tv) "▲ ▼ Sender wechseln · OK Info" else "Wischen ↑↓ zum Senderwechsel",
                                        color = Color.Gray, fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }

                    // Fernseher, Film/Folge: eigene Leiste mit Fortschritt
                    if (tv && !e.live) {
                        AnimatedVisibility(overlayVisible, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                                    .padding(horizontal = 48.dp, vertical = 28.dp),
                            ) {
                                Text(e.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (e.subtitle.isNotBlank()) {
                                    Text(e.subtitle, color = Color.LightGray, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = FcRed,
                                    trackColor = Color(0x55FFFFFF),
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${fmt(position)} / ${fmt(duration)}", color = Color.White, fontSize = 14.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "◀ ▶ Spulen · OK Pause" + if (entries.size > 1) " · ⏮ ⏭ Folge" else "",
                                        color = Color.Gray, fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                        if (paused && !buffering && !inPip) {
                            Icon(Icons.Default.Pause, "Pausiert", Modifier.align(Alignment.Center).size(72.dp), tint = Color.White)
                        }
                    }

                    if (ended && !inPip) {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Zu Ende", color = Color.White, fontSize = 18.sp)
                            TextButton({ finish() }) { Text("Schließen") }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ Fernbedienung

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        val e = entry
        if (e == null || !::player.isInitialized || ev.keyCode !in REMOTE_KEYS) return super.dispatchKeyEvent(ev)
        if (ev.action == KeyEvent.ACTION_DOWN) handleKey(ev.keyCode, ev.repeatCount, e)
        return true
    }

    private fun handleKey(key: Int, repeat: Int, e: PlayEntry) {
        when (key) {
            // ▲ = nächster Sender (wie Wischen nach oben), ▼ = vorheriger
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP ->
                if (e.live) { if (repeat == 0) zap(+1) } else showOverlay()
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN ->
                if (e.live) { if (repeat == 0) zap(-1) } else showOverlay()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND ->
                if (e.live) showOverlay() else seekBy(-10_000L * seekFactor(repeat))
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ->
                if (e.live) showOverlay() else seekBy(10_000L * seekFactor(repeat))
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                if (repeat == 0) {
                    if (e.live) { if (overlay) overlay = false else showOverlay() } else togglePlay()
                }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> if (repeat == 0) togglePlay()
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); paused = false; showOverlay() }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); paused = true; showOverlay() }
            KeyEvent.KEYCODE_MEDIA_NEXT -> if (repeat == 0) skip(+1)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if (repeat == 0) skip(-1)
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> showOverlay()
        }
    }

    private fun showOverlay() { overlay = true; overlayTick++ }

    /** Beim Gedrückthalten immer schneller spulen: 10 s, dann 30 s, dann 60 s pro Schritt. */
    private fun seekFactor(repeat: Int) = if (repeat > 10) 6 else if (repeat > 4) 3 else 1

    private fun zap(step: Int) {
        val n = entries.size
        if (n <= 1) { showOverlay(); return }
        index = (index + step + n) % n
        urlIndex = 0
    }

    /** Nächste/vorherige Folge bzw. Sender. */
    private fun skip(step: Int) {
        if (entry?.live == true) { zap(step); return }
        val target = index + step
        if (target in entries.indices) { savePosition(); index = target; urlIndex = 0 } else showOverlay()
    }

    private fun togglePlay() {
        if (player.playWhenReady) player.pause() else player.play()
        paused = !player.playWhenReady
        showOverlay()
    }

    private fun seekBy(deltaMs: Long) {
        val dur = player.duration
        val max = if (dur == C.TIME_UNSET) Long.MAX_VALUE else dur
        val target = (player.currentPosition + deltaMs).coerceIn(0L, max)
        player.seekTo(target)
        position = target
        showOverlay()
    }

    // ------------------------------------------------------------ Hilfen

    private val hmFmt = SimpleDateFormat("HH:mm", Locale.GERMANY)
    private fun hm(sec: Long) = hmFmt.format(Date(sec * 1000))

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s / 60) % 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.GERMANY, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.GERMANY, "%d:%02d", m, sec)
    }

    /** Fortschritt für "Weiterschauen" speichern. */
    private fun savePosition() {
        if (!::player.isInitialized) return
        val e = entry ?: return
        val key = e.resumeKey ?: return
        val dur = player.duration
        if (dur == C.TIME_UNSET || dur <= 0) return
        PlayQueue.store?.savePosition(
            WatchEntry(
                key = key, title = e.title, subtitle = e.subtitle, image = e.image,
                url = e.urls.first(), position = player.currentPosition, duration = dur,
                time = System.currentTimeMillis(), seriesId = e.seriesId,
            )
        )
    }

    private fun enterPip() {
        if (!pipSupported) return
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (_: Exception) { }
    }

    /** Home-Taste während der Wiedergabe → automatisch Bild-in-Bild (nur Handy). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::player.isInitialized && player.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onPause() {
        super.onPause()
        savePosition()
    }

    override fun onStop() {
        super.onStop()
        savePosition()
        if (::player.isInitialized) player.pause()
        // Bild-in-Bild-Fenster wurde geschlossen → Player beenden
        if (inPip) finish()
    }

    override fun onStart() {
        super.onStart()
        if (::player.isInitialized) player.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (current?.get() == this) current = null
        if (::player.isInitialized) player.release()
    }
}
