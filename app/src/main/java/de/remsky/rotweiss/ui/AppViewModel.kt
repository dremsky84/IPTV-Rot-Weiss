package de.remsky.rotweiss.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.remsky.rotweiss.data.AccountInfo
import de.remsky.rotweiss.data.Category
import de.remsky.rotweiss.data.Channel
import de.remsky.rotweiss.data.CredentialStore
import de.remsky.rotweiss.data.Credentials
import de.remsky.rotweiss.data.EpgEntry
import de.remsky.rotweiss.data.FcData
import de.remsky.rotweiss.data.FootballApi
import de.remsky.rotweiss.data.Episode
import de.remsky.rotweiss.data.LocalStore
import de.remsky.rotweiss.data.Movie
import de.remsky.rotweiss.data.MovieInfo
import de.remsky.rotweiss.data.Series
import de.remsky.rotweiss.data.SeriesInfo
import de.remsky.rotweiss.data.WatchEntry
import de.remsky.rotweiss.data.XtreamApi
import de.remsky.rotweiss.player.PlayEntry
import de.remsky.rotweiss.player.PlayQueue
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class Tab(val title: String) { FC("FC"), LIVE("Live-TV"), MOVIES("Filme"), SERIES("Serien"), FAVORITES("Meine") }

enum class SortMode(val label: String) { DEFAULT("Standard"), NEWEST("Neueste"), AZ("A–Z"), RATING("Bewertung") }

/** Unterseiten, die über den Tabs liegen (Zurück-Taste schließt sie). */
sealed class Route {
    data class ChannelEpg(val channel: Channel) : Route()
    data class MovieDetail(val movie: Movie) : Route()
    data class SeriesDetail(val series: Series) : Route()
    data class LockSettings(val type: String) : Route()
    data object Settings : Route()
    data object FcChannels : Route()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val creds = CredentialStore(app)
    val store = LocalStore(app)
    var api: XtreamApi? = null; private set

    // ------------------------------------------------------------ Anmeldung
    var loggedIn by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var account by mutableStateOf<AccountInfo?>(null); private set
    val savedCredentials: Credentials? = creds.load()

    // ------------------------------------------------------------ Navigation
    var tab by mutableStateOf(Tab.FC)
    val routes = mutableStateListOf<Route>()
    fun open(r: Route) { routes.add(r) }
    fun back() { if (routes.isNotEmpty()) routes.removeAt(routes.lastIndex) }

    // ------------------------------------------------------------ Live-TV
    var liveCategories by mutableStateOf<List<Category>>(emptyList()); private set
    var channels by mutableStateOf<List<Channel>>(emptyList()); private set
    var liveCategory by mutableStateOf<String?>(null)
    var liveQuery by mutableStateOf("")

    // ------------------------------------------------------------ Filme
    var movieCategories by mutableStateOf<List<Category>>(emptyList()); private set
    var movies by mutableStateOf<List<Movie>>(emptyList()); private set
    var moviesLoading by mutableStateOf(false); private set
    private var moviesLoaded = false
    var movieCategory by mutableStateOf<String?>(null)
    var movieQuery by mutableStateOf("")
    var movieSort by mutableStateOf(SortMode.DEFAULT)

    // ------------------------------------------------------------ Serien
    var seriesCategories by mutableStateOf<List<Category>>(emptyList()); private set
    var seriesList by mutableStateOf<List<Series>>(emptyList()); private set
    var seriesLoading by mutableStateOf(false); private set
    private var seriesLoaded = false
    var seriesCategory by mutableStateOf<String?>(null)
    var seriesQuery by mutableStateOf("")
    var seriesSort by mutableStateOf(SortMode.DEFAULT)

    // ------------------------------------------------------------ Lokal
    var favorites by mutableStateOf(store.favorites()); private set
    var history by mutableStateOf(store.history()); private set
    var pinSet by mutableStateOf(store.pin != null); private set
    var locked by mutableStateOf(store.lockedCategories()); private set
    var unlocked by mutableStateOf(false); private set

    // ------------------------------------------------------------ FC-Daten
    private val football = FootballApi()
    var fc by mutableStateOf<FcData?>(null); private set
    var fcLoading by mutableStateOf(false); private set
    var fcError by mutableStateOf<String?>(null); private set
    var fcChannels by mutableStateOf(store.fcChannels()); private set

    fun loadFc(force: Boolean = false) {
        if (fcLoading || (fc != null && !force)) return
        fcLoading = true
        fcError = null
        viewModelScope.launch {
            try {
                fc = football.load()
            } catch (e: Exception) {
                fcError = e.message ?: "Fußballdaten nicht erreichbar"
            } finally {
                fcLoading = false
            }
        }
    }

    fun isFcChannel(id: Int) = id.toString() in fcChannels

    fun toggleFcChannel(id: Int) {
        val k = id.toString()
        fcChannels = if (k in fcChannels) fcChannels - k else fcChannels + k
        store.setFcChannels(fcChannels)
    }

    /** Ausgewählte FC-Sender in der Reihenfolge der Senderliste. */
    val fcChannelList: List<Channel>
        get() = channels.filter { it.streamId.toString() in fcChannels && !isLocked("live", it.categoryId) }

    // ------------------------------------------------------------ EPG-Zwischenspeicher
    val nowNext = mutableStateMapOf<Int, List<EpgEntry>>()
    private val epgRequested = HashSet<Int>()
    private val epgLimit = Semaphore(4)

    init {
        savedCredentials?.let { login(it, remember = false) }
    }

    fun login(c: Credentials, remember: Boolean = true) {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val a = XtreamApi(c)
                account = a.login()
                val cats = async { a.liveCategories() }
                val streams = async { a.liveStreams() }
                liveCategories = cats.await()
                channels = streams.await()
                api = a
                PlayQueue.api = a
                PlayQueue.store = store
                if (remember) creds.save(c)
                loggedIn = true
            } catch (e: Exception) {
                error = e.message ?: "Verbindung fehlgeschlagen"
            } finally {
                loading = false
            }
        }
    }

    fun logout() {
        creds.clear()
        loggedIn = false
        api = null
        PlayQueue.api = null
        account = null
        channels = emptyList(); liveCategories = emptyList()
        movies = emptyList(); movieCategories = emptyList(); moviesLoaded = false
        seriesList = emptyList(); seriesCategories = emptyList(); seriesLoaded = false
        liveCategory = null; movieCategory = null; seriesCategory = null
        liveQuery = ""; movieQuery = ""; seriesQuery = ""
        nowNext.clear(); epgRequested.clear()
        routes.clear()
        tab = Tab.FC
    }

    fun refreshAll() {
        val a = api ?: return
        viewModelScope.launch {
            try {
                account = a.login()
                liveCategories = a.liveCategories()
                channels = a.liveStreams()
            } catch (_: Exception) { }
        }
        if (moviesLoaded) { moviesLoaded = false; ensureMovies() }
        if (seriesLoaded) { seriesLoaded = false; ensureSeries() }
        nowNext.clear(); epgRequested.clear()
    }

    fun ensureMovies() {
        val a = api ?: return
        if (moviesLoaded || moviesLoading) return
        moviesLoading = true
        viewModelScope.launch {
            try {
                val cats = async { a.movieCategories() }
                val list = async { a.movies() }
                movieCategories = cats.await()
                movies = list.await()
                moviesLoaded = true
            } catch (e: Exception) {
                error = e.message
            } finally {
                moviesLoading = false
            }
        }
    }

    fun ensureSeries() {
        val a = api ?: return
        if (seriesLoaded || seriesLoading) return
        seriesLoading = true
        viewModelScope.launch {
            try {
                val cats = async { a.seriesCategories() }
                val list = async { a.series() }
                seriesCategories = cats.await()
                seriesList = list.await()
                seriesLoaded = true
            } catch (e: Exception) {
                error = e.message
            } finally {
                seriesLoading = false
            }
        }
    }

    // ------------------------------------------------------------ Gefilterte Listen

    fun isLocked(type: String, categoryId: String) =
        pinSet && !unlocked && "$type:$categoryId" in locked

    fun visibleCategories(type: String, cats: List<Category>) = cats.filter { !isLocked(type, it.id) }

    val visibleChannels: List<Channel>
        get() {
            val q = liveQuery.trim()
            return channels.filter { ch ->
                !isLocked("live", ch.categoryId) &&
                    (liveCategory == null || ch.categoryId == liveCategory) &&
                    (q.isEmpty() || ch.name.contains(q, ignoreCase = true))
            }
        }

    val visibleMovies: List<Movie>
        get() {
            val q = movieQuery.trim()
            val list = movies.filter { m ->
                !isLocked("movie", m.categoryId) &&
                    (movieCategory == null || m.categoryId == movieCategory) &&
                    (q.isEmpty() || m.name.contains(q, ignoreCase = true))
            }
            return when (movieSort) {
                SortMode.DEFAULT -> list
                SortMode.NEWEST -> list.sortedByDescending { it.added }
                SortMode.AZ -> list.sortedBy { it.name.lowercase() }
                SortMode.RATING -> list.sortedByDescending { it.rating.replace(',', '.').toDoubleOrNull() ?: 0.0 }
            }
        }

    val visibleSeries: List<Series>
        get() {
            val q = seriesQuery.trim()
            val list = seriesList.filter { s ->
                !isLocked("series", s.categoryId) &&
                    (seriesCategory == null || s.categoryId == seriesCategory) &&
                    (q.isEmpty() || s.name.contains(q, ignoreCase = true))
            }
            return when (seriesSort) {
                SortMode.DEFAULT -> list
                SortMode.NEWEST -> list.sortedByDescending { it.lastModified }
                SortMode.AZ -> list.sortedBy { it.name.lowercase() }
                SortMode.RATING -> list.sortedByDescending { it.rating.replace(',', '.').toDoubleOrNull() ?: 0.0 }
            }
        }

    // ------------------------------------------------------------ Favoriten

    fun isFavorite(key: String) = key in favorites

    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites - key else favorites + key
        store.setFavorites(favorites)
    }

    fun refreshHistory() { history = store.history() }

    fun removeHistory(key: String) { store.removeHistory(key); refreshHistory() }

    // ------------------------------------------------------------ Kindersperre

    fun checkPin(pin: String) = store.pin == pin

    fun setPin(pin: String?) {
        store.pin = pin
        pinSet = pin != null
        if (pin == null) unlocked = false
    }

    fun unlock(pin: String): Boolean {
        if (!checkPin(pin)) return false
        unlocked = true
        return true
    }

    fun lockAgain() { unlocked = false }

    fun setLockedAll(set: Set<String>) {
        locked = set
        store.setLockedCategories(set)
    }

    fun toggleLock(type: String, categoryId: String) {
        val key = "$type:$categoryId"
        locked = if (key in locked) locked - key else locked + key
        store.setLockedCategories(locked)
    }

    // ------------------------------------------------------------ EPG

    /** Jetzt/Danach für einen Sender laden (einmal pro Sitzung, max. 4 gleichzeitig). */
    fun requestNowNext(streamId: Int) {
        val a = api ?: return
        if (!epgRequested.add(streamId)) return
        viewModelScope.launch {
            epgLimit.withPermit {
                val list = try { a.shortEpg(streamId, 3) } catch (_: Exception) { emptyList() }
                val now = System.currentTimeMillis() / 1000
                nowNext[streamId] = list.filter { it.end > now }.take(2)
            }
        }
    }

    suspend fun fullEpg(streamId: Int): List<EpgEntry> = api?.fullEpg(streamId) ?: emptyList()
    suspend fun movieInfo(id: Int): MovieInfo? = api?.movieInfo(id)
    suspend fun seriesInfo(id: Int): SeriesInfo? = api?.seriesInfo(id)

    // ------------------------------------------------------------ Abspielen
    // Diese Funktionen füllen nur die Warteschlange; die Oberfläche startet danach den Player.

    fun playChannel(list: List<Channel>, channel: Channel) {
        val a = api ?: return
        PlayQueue.entries = list.map { liveEntry(a, it) }
        PlayQueue.index = list.indexOf(channel).coerceAtLeast(0)
        store.lastChannel = channel.streamId
    }

    private fun liveEntry(a: XtreamApi, ch: Channel) = PlayEntry(
        title = ch.name,
        urls = listOf(a.liveUrl(ch.streamId, "m3u8"), a.liveUrl(ch.streamId, "ts")),
        live = true,
        channelId = ch.streamId,
        image = ch.logo,
    )

    fun playCatchup(channel: Channel, entry: EpgEntry) {
        val a = api ?: return
        PlayQueue.entries = listOf(
            PlayEntry(
                title = entry.title,
                subtitle = "${channel.name} · ${timeRange(entry)}",
                urls = listOf(a.catchupUrl(channel.streamId, entry)),
                live = false,
            )
        )
        PlayQueue.index = 0
    }

    fun playMovie(m: Movie) {
        val a = api ?: return
        PlayQueue.entries = listOf(
            PlayEntry(
                title = m.name,
                subtitle = "Film",
                urls = listOf(a.movieUrl(m)),
                live = false,
                resumeKey = "movie:${m.streamId}",
                image = m.poster,
            )
        )
        PlayQueue.index = 0
    }

    /** Spielt eine Folge; die weiteren Folgen (auch nächste Staffeln) laufen automatisch weiter. */
    fun playEpisode(series: Series, info: SeriesInfo, episode: Episode) {
        val a = api ?: return
        val all = info.episodes.toSortedMap().values.flatten()
        PlayQueue.entries = all.map { e ->
            PlayEntry(
                title = series.name,
                subtitle = "S${e.season} E${e.number}" + if (e.title.isNotBlank() && !e.title.equals(series.name, true)) " · ${e.title}" else "",
                urls = listOf(a.episodeUrl(e.id, e.ext)),
                live = false,
                resumeKey = "episode:${e.id}",
                image = e.image ?: series.cover,
                seriesId = series.seriesId,
            )
        }
        PlayQueue.index = all.indexOf(episode).coerceAtLeast(0)
    }

    fun playHistory(w: WatchEntry) {
        PlayQueue.entries = listOf(
            PlayEntry(
                title = w.title,
                subtitle = w.subtitle,
                urls = listOf(w.url),
                live = false,
                resumeKey = w.key,
                image = w.image,
                seriesId = w.seriesId,
            )
        )
        PlayQueue.index = 0
    }
}
