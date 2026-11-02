package de.remsky.rotweiss.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Credentials(val server: String, val user: String, val pass: String) {
    /** Server-Adresse vereinheitlichen: http:// ergänzen, Slash am Ende entfernen. */
    val baseUrl: String
        get() {
            val s = server.trim().trimEnd('/')
            return if (s.startsWith("http://") || s.startsWith("https://")) s else "http://$s"
        }
}

data class AccountInfo(
    val status: String,
    val expires: String,      // z. B. "31.12.2026" oder "unbegrenzt"
    val maxConnections: String,
    val activeConnections: String,
)

data class Category(val id: String, val name: String)

data class Channel(
    val streamId: Int,
    val name: String,
    val logo: String?,
    val categoryId: String,
    val epgChannelId: String?,
    val hasArchive: Boolean,
    val archiveDays: Int,
)

data class Movie(
    val streamId: Int,
    val name: String,
    val poster: String?,
    val categoryId: String,
    val rating: String,
    val ext: String,
    val added: Long,
)

data class MovieInfo(
    val plot: String,
    val genre: String,
    val cast: String,
    val director: String,
    val year: String,
    val duration: String,
    val rating: String,
    val backdrop: String?,
)

data class Series(
    val seriesId: Int,
    val name: String,
    val cover: String?,
    val categoryId: String,
    val rating: String,
    val plot: String,
    val year: String,
    val lastModified: Long,
)

data class Episode(
    val id: Int,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String,
    val plot: String,
    val duration: String,
    val image: String?,
)

data class SeriesInfo(
    val plot: String,
    val genre: String,
    val cast: String,
    val year: String,
    val backdrop: String?,
    val episodes: Map<Int, List<Episode>>, // Staffel -> Folgen
)

data class EpgEntry(
    val title: String,
    val description: String,
    val start: Long,          // Unix-Sekunden
    val end: Long,
    val startRaw: String,     // Server-Zeit "yyyy-MM-dd HH:mm:ss" (für Catch-up)
    val hasArchive: Boolean,
) {
    val durationMinutes: Int get() = ((end - start) / 60).toInt().coerceAtLeast(1)
}

class XtreamApi(private val creds: Credentials) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private suspend fun call(action: String? = null, vararg params: Pair<String, String>): String =
        withContext(Dispatchers.IO) {
            val url = "${creds.baseUrl}/player_api.php".toHttpUrl().newBuilder()
                .addQueryParameter("username", creds.user)
                .addQueryParameter("password", creds.pass)
                .apply {
                    if (action != null) addQueryParameter("action", action)
                    params.forEach { (k, v) -> addQueryParameter(k, v) }
                }
                .build()
            val req = Request.Builder().url(url).header("User-Agent", "RotWeiss/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Server antwortet mit Fehler ${resp.code}")
                resp.body?.string() ?: throw IOException("Leere Antwort vom Server")
            }
        }

    private fun array(text: String): JSONArray =
        try { JSONArray(text) } catch (e: Exception) { JSONArray() }

    private fun obj(text: String): JSONObject =
        try { JSONObject(text) } catch (e: Exception) { JSONObject() }

    // ------------------------------------------------------------ Konto

    /** Prüft die Zugangsdaten und liefert Kontoinfos. */
    suspend fun login(): AccountInfo {
        val json = try {
            JSONObject(call())
        } catch (e: org.json.JSONException) {
            throw IOException("Keine gültige Xtream-Antwort – Server-Adresse prüfen")
        }
        val u = json.optJSONObject("user_info")
            ?: throw IOException("Keine gültige Xtream-Antwort – Server-Adresse prüfen")
        if (u.optString("auth") != "1") throw IOException("Benutzername oder Passwort falsch")

        val exp = u.optString("exp_date").toLongOrNull()
        val expires = if (exp == null || exp == 0L) "unbegrenzt"
        else SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(exp * 1000))

        return AccountInfo(
            status = u.optString("status", "?"),
            expires = expires,
            maxConnections = u.optString("max_connections", "?"),
            activeConnections = u.optString("active_cons", "?"),
        )
    }

    // ------------------------------------------------------------ Live-TV

    suspend fun liveCategories(): List<Category> = categories("get_live_categories")

    suspend fun liveStreams(): List<Channel> =
        array(call("get_live_streams")).objects().map {
            Channel(
                streamId = it.optInt("stream_id"),
                name = it.optString("name").trim(),
                logo = it.image("stream_icon"),
                categoryId = it.optString("category_id"),
                epgChannelId = it.optString("epg_channel_id").takeIf { s -> s.isNotBlank() && s != "null" },
                hasArchive = it.optString("tv_archive") == "1",
                archiveDays = it.optString("tv_archive_duration").toIntOrNull() ?: 0,
            )
        }.distinctBy { it.streamId }

    /** Stream-Adresse; HLS zuerst, .ts als Ausweichformat. */
    fun liveUrl(streamId: Int, ext: String = "m3u8") =
        "${creds.baseUrl}/live/${creds.user}/${creds.pass}/$streamId.$ext"

    /** Catch-up-Adresse für eine vergangene Sendung. */
    fun catchupUrl(streamId: Int, entry: EpgEntry): String {
        val start = try {
            val inFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val outFmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
            outFmt.format(inFmt.parse(entry.startRaw)!!)
        } catch (e: Exception) {
            SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).format(Date(entry.start * 1000))
        }
        return "${creds.baseUrl}/timeshift/${creds.user}/${creds.pass}/${entry.durationMinutes}/$start/$streamId.ts"
    }

    /** Jetzt/Danach (kurzes EPG). */
    suspend fun shortEpg(streamId: Int, limit: Int = 2): List<EpgEntry> =
        epg(obj(call("get_short_epg", "stream_id" to streamId.toString(), "limit" to limit.toString())))

    /** Ganzes Programm eines Senders (inkl. Archiv-Kennzeichnung). */
    suspend fun fullEpg(streamId: Int): List<EpgEntry> =
        epg(obj(call("get_simple_data_table", "stream_id" to streamId.toString())))

    private fun epg(json: JSONObject): List<EpgEntry> {
        val list = json.optJSONArray("epg_listings") ?: return emptyList()
        return list.objects().mapNotNull {
            val start = it.optString("start_timestamp").toLongOrNull() ?: return@mapNotNull null
            val end = it.optString("stop_timestamp").toLongOrNull()
                ?: it.optString("end_timestamp").toLongOrNull() ?: return@mapNotNull null
            EpgEntry(
                title = b64(it.optString("title")),
                description = b64(it.optString("description")),
                start = start,
                end = end,
                startRaw = it.optString("start"),
                hasArchive = it.optString("has_archive") == "1",
            )
        }.sortedBy { it.start }
    }

    // ------------------------------------------------------------ Filme

    suspend fun movieCategories(): List<Category> = categories("get_vod_categories")

    suspend fun movies(): List<Movie> =
        array(call("get_vod_streams")).objects().map {
            Movie(
                streamId = it.optInt("stream_id"),
                name = it.optString("name").trim(),
                poster = it.image("stream_icon"),
                categoryId = it.optString("category_id"),
                rating = it.rating(),
                ext = it.optString("container_extension").ifBlank { "mp4" },
                added = it.optString("added").toLongOrNull() ?: 0L,
            )
        }.distinctBy { it.streamId }

    suspend fun movieInfo(id: Int): MovieInfo {
        val info = obj(call("get_vod_info", "vod_id" to id.toString())).optJSONObject("info") ?: JSONObject()
        return MovieInfo(
            plot = info.text("plot", "description"),
            genre = info.text("genre"),
            cast = info.text("cast", "actors"),
            director = info.text("director"),
            year = info.text("releasedate", "release_date", "year").take(4),
            duration = info.text("duration"),
            rating = info.rating(),
            backdrop = info.firstImage("backdrop_path") ?: info.image("movie_image"),
        )
    }

    fun movieUrl(m: Movie) = "${creds.baseUrl}/movie/${creds.user}/${creds.pass}/${m.streamId}.${m.ext}"
    fun movieUrl(id: Int, ext: String) = "${creds.baseUrl}/movie/${creds.user}/${creds.pass}/$id.$ext"

    // ------------------------------------------------------------ Serien

    suspend fun seriesCategories(): List<Category> = categories("get_series_categories")

    suspend fun series(): List<Series> =
        array(call("get_series")).objects().map {
            Series(
                seriesId = it.optInt("series_id"),
                name = it.optString("name").trim(),
                cover = it.image("cover"),
                categoryId = it.optString("category_id"),
                rating = it.rating(),
                plot = it.text("plot"),
                year = it.text("releaseDate", "release_date", "year").take(4),
                lastModified = it.optString("last_modified").toLongOrNull() ?: 0L,
            )
        }.distinctBy { it.seriesId }

    suspend fun seriesInfo(id: Int): SeriesInfo {
        val json = obj(call("get_series_info", "series_id" to id.toString()))
        val info = json.optJSONObject("info") ?: JSONObject()

        // "episodes" ist meist ein Objekt {"1":[...]}, bei manchen Servern ein Array von Arrays
        val all = mutableListOf<JSONObject>()
        when (val eps = json.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { k -> eps.optJSONArray(k)?.let { all += it.objects() } }
            is JSONArray -> for (i in 0 until eps.length()) {
                when (val e = eps.opt(i)) {
                    is JSONArray -> all += e.objects()
                    is JSONObject -> all += e
                }
            }
        }
        val episodes = all.map {
            val ei = it.optJSONObject("info") ?: JSONObject()
            Episode(
                id = it.optString("id").toIntOrNull() ?: it.optInt("id"),
                season = it.optString("season").toIntOrNull() ?: 1,
                number = it.optString("episode_num").toIntOrNull() ?: 0,
                title = it.optString("title").trim(),
                ext = it.optString("container_extension").ifBlank { "mp4" },
                plot = ei.text("plot"),
                duration = ei.text("duration"),
                image = ei.image("movie_image"),
            )
        }.sortedWith(compareBy<Episode>({ it.season }, { it.number }))
            .groupBy { it.season }

        return SeriesInfo(
            plot = info.text("plot"),
            genre = info.text("genre"),
            cast = info.text("cast"),
            year = info.text("releaseDate", "release_date").take(4),
            backdrop = info.firstImage("backdrop_path") ?: info.image("cover"),
            episodes = episodes,
        )
    }

    fun episodeUrl(id: Int, ext: String) = "${creds.baseUrl}/series/${creds.user}/${creds.pass}/$id.$ext"

    // ------------------------------------------------------------ Hilfen

    private suspend fun categories(action: String): List<Category> =
        array(call(action)).objects().map {
            Category(it.optString("category_id"), it.optString("category_name").trim())
        }.distinctBy { it.id }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.text(vararg keys: String): String {
        for (k in keys) {
            val v = optString(k)
            if (v.isNotBlank() && v != "null") return v.trim()
        }
        return ""
    }

    private fun JSONObject.image(key: String): String? =
        optString(key).takeIf { it.startsWith("http") }

    private fun JSONObject.firstImage(key: String): String? =
        when (val v = opt(key)) {
            is JSONArray -> (0 until v.length()).map { v.optString(it) }.firstOrNull { it.startsWith("http") }
            is String -> v.takeIf { it.startsWith("http") }
            else -> null
        }

    private fun JSONObject.rating(): String {
        val r = optString("rating").toDoubleOrNull() ?: optString("rating_5based").toDoubleOrNull()?.times(2)
        return if (r == null || r <= 0.0) "" else String.format(Locale.GERMANY, "%.1f", r)
    }

    /** EPG-Texte kommen meist Base64-kodiert, bei manchen Servern als Klartext. */
    private fun b64(s: String): String {
        if (s.isBlank()) return ""
        return try {
            val d = String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim()
            val broken = d.any { it == '�' || (it.code < 32 && it != '\n' && it != '\r' && it != '\t') }
            if (broken) s else d
        } catch (e: Exception) {
            s
        }
    }
}
