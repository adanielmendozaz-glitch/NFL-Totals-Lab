package com.nfltotalslab.app.simcast

import org.json.JSONArray
import org.json.JSONObject

/** Portable deterministic simulation report; includes player lines and full events. */
object SimcastArchive {
    fun toJson(s:GameSim):JSONObject = JSONObject().apply{
        put("schema","NFL_SIMCAST_PLAYER_USAGE_V1")
        put("model","Experimental down-distance / prior-week player usage; NOT official Core probability")
        put("gameId",s.gameId);put("season",s.season);put("week",s.week)
        put("away",s.away);put("home",s.home);put("seed",s.seed)
        put("awayUsageWeek",s.awayUsageWeek);put("homeUsageWeek",s.homeUsageWeek)
        put("awayPlayerCount",s.awayPlayerCount);put("homePlayerCount",s.homePlayerCount)
        put("awayQuarters",JSONArray(s.awayQuarters));put("homeQuarters",JSONArray(s.homeQuarters))
        put("awayFinal",s.awayFinal);put("homeFinal",s.homeFinal)
        put("lineup",JSONObject().apply{
            fun team(l:TeamLineup)=JSONObject().apply{
                put("startingQb",l.startingQb);put("outPlayers",JSONArray(l.outNames.toList()))
                put("source",l.source);put("verified",l.verified)
            }
            put("away",team(s.lineup.away));put("home",team(s.lineup.home))
        })
        put("usageInputs",JSONArray().apply{s.usageInputs.forEach{p->put(JSONObject().apply{
            put("id",p.id);put("team",p.team);put("name",p.name)
            put("position",p.position);put("passAttemptsWeighted",p.passAttempts)
            put("carriesWeighted",p.carries);put("targetsWeighted",p.targets)
        })}})
        put("plays",JSONArray().apply{s.plays.forEach{p->put(JSONObject().apply{
            put("index",p.index);put("drive",p.drive);put("team",p.offense)
            put("quarter",p.quarter);put("clock",p.clock);put("down",p.down)
            put("distance",p.distance);put("fromYard",p.fromYard);put("toYard",p.toYard)
            put("kind",p.kind);put("description",p.description);put("actor",p.actor)
            put("secondary",p.secondary);put("yards",p.yards);put("points",p.points)
            put("awayScore",p.awayScore);put("homeScore",p.homeScore)
        })}})
        put("players",JSONArray().apply{s.players.forEach{p->put(JSONObject().apply{
            put("team",p.team);put("name",p.name);put("position",p.position)
            put("passAttempts",p.passAttempts);put("completions",p.completions)
            put("passYards",p.passYards);put("interceptions",p.interceptions)
            put("rushAttempts",p.rushAttempts);put("rushYards",p.rushYards)
            put("targets",p.targets);put("receptions",p.receptions)
            put("receivingYards",p.receivingYards);put("passingTds",p.passingTds)
            put("rushingTds",p.rushingTds);put("receivingTds",p.receivingTds)
        })}})
    }
}
