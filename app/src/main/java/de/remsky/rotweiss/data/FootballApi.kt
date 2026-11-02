package de.remsky.rotweiss.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class FcMatch(
    val id: Int,
    val kickoff: Long,            // ms (UTC)
    val matchday: Int,
    val homeId: Int,
    val home: String,
    val awayId: Int,
    val away: String,
    val goalsHome: Int?,
    val goalsAway: Int?,
    val finished: Boolean,
    val stadium: String,
) {
    /** Läuft gerade (angepfiffen, noch nicht beendet). */
    fun isLive(now: Long) = !finished && now >= kickoff && now < kickoff + 2 * 60 * 60 * 1000
}

data class TableRow(
    val pos: Int,
    val teamId: Int,
    val name: String,
    val shortName: String,
    val played: Int,
    val won: Int,
    val draw: Int,
    val lost: Int,
    val goals: Int,
    val against: Int,
    val points: Int,
) {
    val diff: Int get() = goals - against
}

data class FcData(
    val league: String,       // "1. Bundesliga" / "2. Bundesliga"
    val season: Int,          // Startjahr, z. B. 2026
    val teamId: Int,
    val matches: List<FcMatch>,
    val table: List<TableRow>,
) {
    fun next(now: Long) = matches.filter { !it.finished && it.kickoff + 2 * 60 * 60 * 1000 > now }.minByOrNull { it.kickoff }
    fun last(now: Long) = matches.filter { it.finished || (it.goalsHome != null && it.kickoff < now) }
        .filter { !it.isLive(now) }.maxByOrNull { it.kickoff }
}

/**
 * Spieldaten des 1. FC Köln über OpenLigaDB (kostenlos, ohne Anmeldung).
 * Die Liga wird automatisch erkannt (1. oder 2. Bundesliga).
 */
class FootballApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val base = "https://api.openligadb.de"

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$base/$path").header("Accept", "application/json").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Fußballdaten nicht erreichbar (${resp.code})")
            resp.body?.string() ?: throw IOException("Leere Antwort")
        }
    }

    /** Saison-Startjahr: ab Juli zählt die neue Saison. */
    private fun currentSeason(): Int {
        val c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"))
        return if (c.get(Calendar.MONTH) >= Calendar.JULY) c.get(Calendar.YEAR) else c.get(Calendar.YEAR) - 1
    }

    private fun isFc(name: String) =
        name.contains("Köln", ignoreCase = true) && name.contains("FC", ignoreCase = true) &&
            !name.contains("Fortuna", ignoreCase = true) && !name.contains("Viktoria", ignoreCase = true)

    suspend fun load(): FcData = coroutineScope {
        val season = currentSeason()
        val leagues = listOf("bl1" to "1. Bundesliga", "bl2" to "2. Bundesliga")
        for ((code, label) in leagues) {
            val all = try { JSONArray(get("getmatchdata/$code/$season")) } catch (e: Exception) { continue }
            val raw = (0 until all.length()).mapNotNull { all.optJSONObject(it) }
            val teamId = raw.asSequence()
                .flatMap { sequenceOf(it.optJSONObject("team1"), it.optJSONObject("team2")) }
                .filterNotNull()
                .firstOrNull { isFc(it.optString("teamName")) }
                ?.optInt("teamId") ?: continue

            val matches = raw.mapNotNull { parseMatch(it) }
                .filter { it.homeId == teamId || it.awayId == teamId }
                .sortedBy { it.kickoff }
            val table = async { try { parseTable(JSONArray(get("getbltable/$code/$season"))) } catch (e: Exception) { emptyList() } }
            return@coroutineScope FcData(label, season, teamId, matches, table.await())
        }
        throw IOException("Keine Spieldaten für den 1. FC Köln gefunden")
    }

    private fun parseMatch(o: JSONObject): FcMatch? {
        val t1 = o.optJSONObject("team1") ?: return null
        val t2 = o.optJSONObject("team2") ?: return null
        val kickoff = parseTime(o.optString("matchDateTimeUTC"), utc = true)
            ?: parseTime(o.optString("matchDateTime"), utc = false) ?: return null
        val finished = o.optBoolean("matchIsFinished", o.optBoolean("matchIdFinished", false))

        // Endergebnis (resultTypeID 2) bevorzugen, sonst höchster Zwischenstand/letztes Tor
        var gh: Int? = null
        var ga: Int? = null
        val results = o.optJSONArray("matchResults")
        if (results != null && results.length() > 0) {
            val list = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            val r = list.firstOrNull { it.optInt("resultTypeID") == 2 } ?: list.maxByOrNull { it.optInt("resultOrderID") }
            if (r != null) { gh = r.optInt("pointsTeam1"); ga = r.optInt("pointsTeam2") }
        }
        val goals = o.optJSONArray("goals")
        if (!finished && goals != null && goals.length() > 0) {
            goals.optJSONObject(goals.length() - 1)?.let { g -> gh = g.optInt("scoreTeam1"); ga = g.optInt("scoreTeam2") }
        }

        return FcMatch(
            id = o.optInt("matchID"),
            kickoff = kickoff,
            matchday = o.optJSONObject("group")?.optInt("groupOrderID") ?: 0,
            homeId = t1.optInt("teamId"),
            home = t1.optString("teamName"),
            awayId = t2.optInt("teamId"),
            away = t2.optString("teamName"),
            goalsHome = gh,
            goalsAway = ga,
            finished = finished,
            stadium = o.optJSONObject("location")?.optString("locationStadium").orEmpty().takeIf { it != "null" }.orEmpty(),
        )
    }

    private fun parseTable(arr: JSONArray): List<TableRow> =
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapIndexed { i, o ->
            TableRow(
                pos = i + 1,
                teamId = o.optInt("teamInfoId"),
                name = o.optString("teamName"),
                shortName = o.optString("shortName").ifBlank { o.optString("teamName") },
                played = o.optInt("matches"),
                won = o.optInt("won"),
                draw = o.optInt("draw"),
                lost = o.optInt("lost"),
                goals = o.optInt("goals"),
                against = o.optInt("opponentGoals"),
                points = o.optInt("points"),
            )
        }

    private fun parseTime(s: String, utc: Boolean): Long? {
        if (s.isBlank() || s == "null") return null
        val clean = s.removeSuffix("Z").substringBefore('.')
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone(if (utc) "UTC" else "Europe/Berlin")
            }.parse(clean)?.time
        } catch (e: Exception) {
            null
        }
    }
}
