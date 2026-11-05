package com.nfltotalslab.app.simcast

import org.json.JSONArray
import org.json.JSONObject

/** Compact audit record. A thousand runs contribute aggregates, not a thousand full animations. */
object SimcastBatchArchive {
    fun toJson(b:BatchReport):JSONObject = JSONObject().apply {
        put("schema","NFL_SIMCAST_BATCH_V1")
        put("version","1.5.0-EXPERIMENTAL")
        put("gameId",b.gameId);put("season",b.season);put("week",b.week)
        put("away",b.away);put("home",b.home);put("seed",b.baseSeed);put("n",b.n)
        put("batchKey","${b.gameId}|${b.builtAt}|${b.baseSeed}|${b.n}")
        put("inputSignature",b.inputSignature)
        put("createdAt",b.builtAt);put("pregame",b.pregame)
        put("line",b.totalLine ?: JSONObject.NULL)
        put("meanTotal",b.meanTotal);put("medianTotal",b.medianTotal);put("sdTotal",b.sdTotal)
        put("p10",b.p10);put("p25",b.p25);put("p75",b.p75);put("p90",b.p90)
        put("meanAway",b.meanAway);put("meanHome",b.meanHome)
        put("awayWinRate",b.awayWinRate);put("homeWinRate",b.homeWinRate);put("tieRate",b.tieRate)
        put("pOver",b.overRate ?: JSONObject.NULL)
        put("pUnder",b.underRate ?: JSONObject.NULL)
        put("pPush",b.pushRate ?: JSONObject.NULL)
        put("usage",JSONObject().apply {
            put("awayWeek",b.awayUsageWeek);put("homeWeek",b.homeUsageWeek)
            put("awayPlayers",b.awayPlayers);put("homePlayers",b.homePlayers)
        })
        put("lineup",JSONObject().apply{
            fun pack(l:TeamLineup)=JSONObject().apply {
                put("startingQb",l.startingQb)
                put("out",JSONArray(l.outNames.sorted()))
                put("verified",l.verified)
                put("source",l.source)
            }
            put("away",pack(b.lineup.away));put("home",pack(b.lineup.home))
        })
        put("calibration",JSONObject().apply{
            val c=b.calibration
            put("method",c.method);put("cutoffWeek",c.cutoffWeek)
            put("leagueGames",c.leagueGames);put("awayGames",c.awayGames);put("homeGames",c.homeGames)
            put("leaguePpg",c.leaguePpg);put("awayPpg",c.awayPpg);put("homePpg",c.homePpg)
            put("awayScoringFactor",c.awayScoringFactor);put("homeScoringFactor",c.homeScoringFactor)
            put("clockFactor",c.clockFactor);put("driveTarget",c.driveTarget)
            put("driveSampleTeams",c.driveSampleTeams)
        })
        put("histogram",JSONArray().apply { b.totalHistogram.forEach{(start,n)->
            put(JSONObject().apply { put("from",start);put("to",start+4);put("n",n) })
        }})
        put("topScores",JSONArray().apply { b.mostCommonScores.forEach{s->
            put(JSONObject().apply { put("away",s.away);put("home",s.home);put("n",s.count) })
        }})
        put("teams",JSONArray().apply { b.teamLines.forEach{t->
            put(JSONObject().apply {
                put("team",t.team);put("drives",t.meanDrives);put("passes",t.meanPasses)
                put("rushes",t.meanRuns);put("tds",t.meanTd);put("fgs",t.meanFg)
                put("punts",t.meanPunts);put("turnovers",t.meanTurnovers)
            })
        }})
        put("players",JSONArray().apply { b.playerLines.forEach{p->
            put(JSONObject().apply {
                put("team",p.team);put("name",p.name);put("position",p.position)
                put("passYardsMean",p.meanPassYards);put("rushYardsMean",p.meanRushYards)
                put("receivingYardsMean",p.meanRecYards)
                put("passAttemptsMean",p.meanAttempts);put("rushAttemptsMean",p.meanCarries)
                put("targetsMean",p.meanTargets)
                put("p90PassYards",p.p90PassYards);put("p90RushYards",p.p90RushYards)
                put("p90ReceivingYards",p.p90RecYards)
            })
        }})
        put("note","Simulación experimental, no probabilidades calibradas para apuestas. PBP ilustrativo no incluido.")
    }
}
