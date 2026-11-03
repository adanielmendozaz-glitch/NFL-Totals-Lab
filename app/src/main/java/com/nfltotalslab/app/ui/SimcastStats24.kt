package com.nfltotalslab.app.ui

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.random.Random

/** Experimental Simcast accounting, isolated from NFL Totals Lab predictive Core. */
internal class SimcastStats24(private val away: String, private val home: String, raw: String, rosterRaw: String, private val gameKey: String) {
    private val plays = JSONArray(raw)
    private val roster = try { JSONObject(rosterRaw) } catch (_: Exception) { JSONObject() }
    private val events = JSONArray()
    private val teams = linkedMapOf(away to linkedMapOf<String, JSONObject>(), home to linkedMapOf<String, JSONObject>())
    private val strengths = JSONObject()
    init {
        for (team in listOf(away, home)) {
            val obj = roster.optJSONObject(team) ?: JSONObject()
            strengths.put(team, JSONObject().put("source", "NFL Totals Lab roster (availability only)")
                .put("offense_rating", JSONObject.NULL).put("defense_rating", JSONObject.NULL)
                .put("active_players", obj.optJSONArray("players")?.length() ?: 0))
        }
        for (i in 0 until plays.length()) {
            val p = plays.optJSONObject(i) ?: continue
            val team = if (p.optBoolean("a")) away else home
            val kind = p.optString("k")
            val yards = p.optInt("y")
            val seed = Random((gameKey + ":" + i + ":v24").hashCode())
            val passer = choose(team, "QB", 0)
            val runner = choose(team, "RB", seed.nextInt(100))
            val receiver = choose(team, "WR", seed.nextInt(100))
            val event = JSONObject().put("play", i + 1).put("team", team).put("kind", kind)
                .put("yards", yards).put("elapsed", p.optInt("t"))
                .put("away_score", p.optInt("as")).put("home_score", p.optInt("hs"))
            val pass = kind.contains("Pase", true) || kind.contains("Intercepcion", true) || kind.contains("Sack", true)
            val complete = kind.contains("Pase completo", true)
            val rush = kind.contains("Carrera", true) || kind.contains("Fumble", true)
            if (pass) {
                val q = stat(team, passer, "QB")
                event.put("passer", passer)
                if (!kind.contains("Sack", true)) inc(q, "pass_attempts", 1)
                if (complete) {
                    inc(q, "completions", 1); inc(q, "passing_yards", yards)
                    val r = stat(team, receiver, "WR")
                    inc(r, "targets", 1); inc(r, "receptions", 1); inc(r, "receiving_yards", yards)
                    event.put("receiver", receiver)
                } else if (kind.contains("Pase incompleto", true) || kind.contains("Intercepcion", true)) {
                    inc(stat(team, receiver, "WR"), "targets", 1)
                    event.put("receiver", receiver)
                }
                if (kind.contains("Intercepcion", true)) inc(q, "interceptions", 1)
                if (kind.contains("Sack", true)) inc(q, "sacks_taken", 1)
            }
            if (rush) {
                val r = stat(team, runner, "RB")
                inc(r, "rush_attempts", 1); inc(r, "rushing_yards", yards)
                event.put("rusher", runner)
                if (kind.contains("Fumble", true)) inc(r, "fumbles", 1)
            }
            // The original generator collapses the scoring play into "Touchdown" and loses
            // its pass/rush type. Never invent individual TD or yardage credit.
            if (kind.contains("Touchdown", true)) event.put("attribution", "unknown: original play generator discards scoring play type")
            events.put(event)
        }
    }
    private fun choose(team: String, position: String, seed: Int): String {
        val players = roster.optJSONObject(team)?.optJSONArray("players") ?: JSONArray()
        val matches = mutableListOf<String>()
        for (i in 0 until players.length()) {
            val p = players.optJSONObject(i) ?: continue
            if (p.optString("position").equals(position, true) && !p.optBoolean("unavailable"))
                matches.add(p.optString("name"))
        }
        return if (matches.isNotEmpty()) matches[seed % matches.size] else "$team $position (sin roster)"
    }
    private fun stat(team: String, name: String, pos: String): JSONObject {
        val map = teams.getValue(team)
        return map.getOrPut(name) { JSONObject().put("player", name).put("position", pos).put("team", team) }
    }
    private fun inc(p: JSONObject, field: String, delta: Int) = p.put(field, p.optInt(field) + delta)
    fun report(until: Int): JSONObject {
        val stop = until.coerceIn(0, events.length())
        val accum = linkedMapOf<String, LinkedHashMap<String, JSONObject>>()
        for (team in listOf(away, home)) {
            accum[team] = linkedMapOf()
            val all = roster.optJSONObject(team)?.optJSONArray("players") ?: JSONArray()
            for (j in 0 until all.length()) {
                val pl = all.optJSONObject(j) ?: continue
                val name = pl.optString("name")
                if (name.isNotBlank()) accum.getValue(team)[name] = JSONObject()
                    .put("player", name).put("position", pl.optString("position"))
                    .put("team", team).put("unavailable", pl.optBoolean("unavailable"))
            }
        }
        for (i in 0 until stop) {
            val e = events.getJSONObject(i); val team = e.getString("team")
            fun add(name: String, pos: String, field: String, value: Int) {
                val m = accum.getValue(team)
                val p = m.getOrPut(name) { JSONObject().put("player", name).put("position", pos).put("team", team) }
                p.put(field, p.optInt(field) + value)
            }
            val k = e.getString("kind"); val y = e.optInt("yards")
            val qb = e.optString("passer"); val wr = e.optString("receiver"); val rb = e.optString("rusher")
            if (qb.isNotEmpty()) {
                if (!k.contains("Sack", true)) add(qb, "QB", "pass_attempts", 1)
                if (k.contains("Pase completo", true)) { add(qb, "QB", "completions", 1); add(qb, "QB", "passing_yards", y) }
                if (k.contains("Intercepcion", true)) add(qb, "QB", "interceptions", 1)
                if (k.contains("Sack", true)) add(qb, "QB", "sacks_taken", 1)
            }
            if (wr.isNotEmpty()) {
                add(wr, "WR", "targets", 1)
                if (k.contains("Pase completo", true)) { add(wr, "WR", "receptions", 1); add(wr, "WR", "receiving_yards", y) }
            }
            if (rb.isNotEmpty()) {
                add(rb, "RB", "rush_attempts", 1); add(rb, "RB", "rushing_yards", y)
                if (k.contains("Fumble", true)) add(rb, "RB", "fumbles", 1)
            }
        }
        val playerArray = JSONArray()
        for (team in listOf(away, home)) for (p in accum.getValue(team).values) playerArray.put(p)
        val eventArray = JSONArray()
        for (i in 0 until stop) eventArray.put(events.getJSONObject(i))
        return JSONObject().put("schema", "simcast24-experimental").put("game_key", gameKey)
            .put("away", away).put("home", home).put("plays_processed", stop)
            .put("plays_total", plays.length()).put("final", stop == plays.length())
            .put("strengths", strengths).put("players", playerArray).put("events", eventArray)
            .put("note", "SIMULATED. No calibrated team strengths; touchdown attribution unavailable from source events. Not real NFL box score.")
    }
    fun summary(until: Int): List<String> {
        val report = report(until); val a = report.getJSONArray("players")
        val lines = mutableListOf<String>()
        for (team in listOf(away, home)) {
            lines.add("$team  |  PASS / RUSH / REC (YD)")
            for (i in 0 until a.length()) {
                val p = a.getJSONObject(i)
                if (p.getString("team") != team) continue
                lines.add("${p.getString("player").take(20)}  P:${p.optInt("passing_yards")}  R:${p.optInt("rushing_yards")}  C:${p.optInt("receiving_yards")}")
            }
        }
        return lines
    }
}
