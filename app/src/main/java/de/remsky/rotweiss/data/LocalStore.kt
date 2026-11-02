package de.remsky.rotweiss.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Ein angefangener Film oder eine angefangene Folge ("Weiterschauen"). */
data class WatchEntry(
    val key: String,          // "movie:123" oder "episode:456"
    val title: String,
    val subtitle: String,
    val image: String?,
    val url: String,
    val position: Long,       // ms
    val duration: Long,       // ms
    val time: Long,           // zuletzt gesehen (ms)
    val seriesId: Int = 0,
) {
    val progress: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/** Favoriten, Weiterschauen und Kindersperre – lokal auf dem Gerät. */
class LocalStore(context: Context) {

    private val prefs = context.getSharedPreferences("rotweiss_local", Context.MODE_PRIVATE)

    // ------------------------------------------------------------ Favoriten
    // Schlüssel: "live:<id>", "movie:<id>", "series:<id>"

    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet())!!.toSet()

    fun setFavorites(set: Set<String>) = prefs.edit().putStringSet("favorites", set).apply()

    // ------------------------------------------------------------ Weiterschauen

    fun history(): List<WatchEntry> {
        val arr = try { JSONArray(prefs.getString("history", "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            WatchEntry(
                key = o.optString("key"),
                title = o.optString("title"),
                subtitle = o.optString("subtitle"),
                image = o.optString("image").takeIf { it.startsWith("http") },
                url = o.optString("url"),
                position = o.optLong("position"),
                duration = o.optLong("duration"),
                time = o.optLong("time"),
                seriesId = o.optInt("seriesId"),
            )
        }
    }

    fun position(key: String): Long = history().firstOrNull { it.key == key }?.position ?: 0L

    /** Speichert den Fortschritt. Fast zu Ende gesehen → aus der Liste entfernen. */
    fun savePosition(entry: WatchEntry) {
        val list = history().filter { it.key != entry.key }.toMutableList()
        val finished = entry.duration > 0 && entry.position > entry.duration * 0.95
        if (!finished && entry.position > 10_000) list.add(0, entry)
        writeHistory(list.take(40))
    }

    fun removeHistory(key: String) = writeHistory(history().filter { it.key != key })

    private fun writeHistory(list: List<WatchEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("key", it.key).put("title", it.title).put("subtitle", it.subtitle)
                    .put("image", it.image ?: "").put("url", it.url)
                    .put("position", it.position).put("duration", it.duration)
                    .put("time", it.time).put("seriesId", it.seriesId)
            )
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    // ------------------------------------------------------------ Kindersperre
    // Gesperrte Kategorien: "live:<catId>", "movie:<catId>", "series:<catId>"

    var pin: String?
        get() = prefs.getString("pin", null)
        set(v) = prefs.edit().putString("pin", v).apply()

    fun lockedCategories(): Set<String> = prefs.getStringSet("locked", emptySet())!!.toSet()

    fun setLockedCategories(set: Set<String>) = prefs.edit().putStringSet("locked", set).apply()

    // ------------------------------------------------------------ FC-Sender (Spieltag-Modus)

    fun fcChannels(): Set<String> = prefs.getStringSet("fc_channels", emptySet())!!.toSet()

    fun setFcChannels(set: Set<String>) = prefs.edit().putStringSet("fc_channels", set).apply()

    // ------------------------------------------------------------ Zuletzt gesehener Sender

    var lastChannel: Int
        get() = prefs.getInt("last_channel", -1)
        set(v) = prefs.edit().putInt("last_channel", v).apply()
}
