package de.remsky.rotweiss

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
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
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlayerActivity : ComponentActivity() {

    companion object {
        /** Nur ein Player gleichzeitig (z. B. wenn einer im Bild-in-Bild läuft). */
        private var current: WeakReference<PlayerActivity>? = null
    }

    private lateinit var player: ExoPlayer
    private var entries: List<PlayEntry> = emptyList()
    private var index by mutableIntStateOf(0)
    private var inPip by mutableStateOf(false)

    private val entry: PlayEntry? get() = entries.getOrNull(index)

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            var urlIndex by remember { mutableIntStateOf(0) }
            var buffering by remember { mutableStateOf(true) }
            var error by remember { mutableStateOf<String?>(null) }
            var showInfo by remember { mutableStateOf(true) }
            var controlsVisible by remember { mutableStateOf(true) }
            var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
            var ended by remember { mutableStateOf(false) }
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

            // Senderinfo + Jetzt/Danach
            LaunchedEffect(index) {
                epg = emptyList()
                if (e.live) {
                    showInfo = true
                    val api = PlayQueue.api
                    if (api != null) {
                        epg = try {
                            val now = System.currentTimeMillis() / 1000
                            api.shortEpg(e.channelId, 3).filter { it.end > now }.take(2)
                        } catch (_: Exception) { emptyList() }
                    }
                    delay(4000)
                    showInfo = false
                }
            }

            // Fortschritt regelmäßig speichern
            LaunchedEffect(index) {
                if (!e.live && e.resumeKey != null) {
                    while (true) { delay(10_000); savePosition() }
                }
            }

            val overlayVisible = !inPip && (if (e.live) showInfo else controlsVisible)

            Box(
                Modifier.fillMaxSize().background(Color.Black).then(
                    if (e.live) Modifier.pointerInput(Unit) {
                        detectTapGestures { showInfo = !showInfo }
                    }.pointerInput(Unit) {
                        var drag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                val n = entries.size
                                if (n > 1 && drag < -120) { index = (index + 1) % n; urlIndex = 0 }
                                else if (n > 1 && drag > 120) { index = (index - 1 + n) % n; urlIndex = 0 }
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
                        view.useController = !e.live && !inPip
                        if (e.live || inPip) view.hideController()
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

                // Obere Leiste: Zurück, Titel, Bild-in-Bild
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
                        IconButton({ enterPip() }) {
                            Icon(Icons.Default.PictureInPictureAlt, "Bild-in-Bild", tint = Color.White)
                        }
                    }
                }

                // Live: Senderinfo mit Jetzt/Danach
                if (e.live) {
                    AnimatedVisibility(overlayVisible, Modifier.align(Alignment.BottomStart).padding(20.dp), enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            Modifier.widthIn(max = 520.dp).background(Color(0xCC000000), RoundedCornerShape(12.dp)).padding(16.dp, 10.dp),
                        ) {
                            Text("${index + 1}  ${e.title}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            epg.getOrNull(0)?.let {
                                Text("Jetzt  ${hm(it.start)}–${hm(it.end)}  ${it.title}", color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            epg.getOrNull(1)?.let {
                                Text("Danach  ${hm(it.start)}  ${it.title}", color = Color.LightGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (entries.size > 1) Text("Wischen ↑↓ zum Senderwechsel", color = Color.Gray, fontSize = 12.sp)
                        }
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

    private val hmFmt = SimpleDateFormat("HH:mm", Locale.GERMANY)
    private fun hm(sec: Long) = hmFmt.format(Date(sec * 1000))

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
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (_: Exception) { }
    }

    /** Home-Taste während der Wiedergabe → automatisch Bild-in-Bild. */
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
