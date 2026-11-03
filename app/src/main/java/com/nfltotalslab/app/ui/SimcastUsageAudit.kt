package com.nfltotalslab.app.ui

import org.json.JSONArray
import org.json.JSONObject

/** Observed simulation usage, not actual NFL 2026 player usage. */
internal object SimcastUsageAudit {
    fun build(events: JSONArray): JSONObject {
        val teamRows = linkedMapOf<String, MutableMap<String, Int>>()
        val playerRows = linkedMapOf<String, MutableMap<String, Int>>()
        val teamPasses = mutableMapOf<String, Int>()
        val teamRushes = mutableMapOf<String, Int>()
        val teamPunts = mutableMapOf<String, Int>()
        for (i in 0 until events.length()) {
            val p = events.optJSONObject(i) ?: continue
            val team = p.optString("team", "")
            if (team.isBlank()) continue
            val kind = p.optString("kind", "")
            val passer = p.optString("passer", "")
            val receiver = p.optString("receiver", "")
            val rusher = p.optString("rusher", "")
            val isPass = kind == "Pase completo" || kind == "Pase incompleto" || kind == "Intercepcion"
            val isRush = kind == "Carrera" || kind == "Carrera detenida"
            fun add(name: String, key: String) {
                if (name.isBlank()) return
                val row = playerRows.getOrPut("$team|$name") { mutableMapOf() }
                row[key] = (row[key] ?: 0) + 1
            }
            if (isPass) {
                teamPasses[team] = (teamPasses[team] ?: 0) + 1
                add(passer, "pass_attempts")
                add(receiver, "targets")
                if (kind == "Pase completo") add(receiver, "receptions")
            }
            if (isRush) {
                teamRushes[team] = (teamRushes[team] ?: 0) + 1
                add(rusher, "carries")
            }
            if (kind == "Despeje") teamPunts[team] = (teamPunts[team] ?: 0) + 1
        }
        val players = JSONArray()
        for ((key, row) in playerRows) {
            val team = key.substringBefore('|')
            val name = key.substringAfter('|')
            val targets = row["targets"] ?: 0
            val carries = row["carries"] ?: 0
            players.put(JSONObject().put("team", team).put("player", name)
                .put("pass_attempts", row["pass_attempts"] ?: 0)
                .put("targets", targets).put("receptions", row["receptions"] ?: 0)
                .put("carries", carries)
                .put("target_share", if ((teamPasses[team] ?: 0) > 0) targets.toDouble() / teamPasses.getValue(team) else 0.0)
                .put("carry_share", if ((teamRushes[team] ?: 0) > 0) carries.toDouble() / teamRushes.getValue(team) else 0.0))
        }
        val teams = JSONArray()
        val flags = JSONArray()
        for (team in (teamPasses.keys + teamRushes.keys + teamPunts.keys).toSortedSet()) {
            val passes = teamPasses[team] ?: 0
            val rushes = teamRushes[team] ?: 0
            val punts = teamPunts[team] ?: 0
            teams.put(JSONObject().put("team", team).put("pass_attempts", passes)
                .put("rush_attempts", rushes).put("punts", punts))
            if (passes > 50) flags.put("HIGH_PASS_VOLUME:$team:$passes")
            if (punts > 9) flags.put("HIGH_PUNT_VOLUME:$team:$punts")
            for (i in 0 until players.length()) {
                val p = players.getJSONObject(i)
                if (p.optString("team") != team) continue
                if (p.optInt("targets") > 15) flags.put("HIGH_TARGETS:$team:${p.optString("player")}:${p.optInt("targets")}")
                if (p.optInt("carries") > 32) flags.put("HIGH_CARRIES:$team:${p.optString("player")}:${p.optInt("carries")}")
            }
        }
        return JSONObject().put("source", "SIMULATED_PLAY_LEDGER_ONLY")
            .put("historical_player_usage_loaded", false)
            .put("teams", teams).put("players", players).put("review_flags", flags)
    }
}
