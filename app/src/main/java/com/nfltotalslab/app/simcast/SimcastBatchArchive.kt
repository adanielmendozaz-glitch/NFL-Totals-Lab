package com.nfltotalslab.app.simcast

import org.json.JSONArray
import org.json.JSONObject

/** Compact audit record. A thousand runs contribute aggregates, not a thousand full animations. */
object SimcastBatchArchive {
    fun toJson(b:BatchReport):JSONObject = JSONObject().apply {
        put("schema","NFL_SIMCAST_BATCH_V2")
        put("version","1.6.3-DRIVE-SCORING-SHADOW")
        put("gameId",b.gameId);put("season",b.season);put("week",b.week)
        put("away",b.away);put("home",b.home);put("seed",b.baseSeed);put("n",b.n)
        put("batchKey","${b.gameId}|${b.builtAt}|${b.baseSeed}|${b.n}")
        put("inputSignature",b.inputSignature)
        put("createdAt",b.builtAt);put("pregame",b.pregame)
        put("line",b.totalLine ?: JSONObject.NULL)
        b.scoringShadow?.let { sc ->
            fun parameter(t:ScoringTeamParameters)=JSONObject().apply{
                put("team",t.team);put("expectedPpg",t.expectedPpg)
                put("drivesPerGame",t.drivesPerGame);put("tdPerDrive",t.pTouchdown)
                put("fgPerDrive",t.pFieldGoal);put("qb",t.qb)
                put("qbAttemptsEvidence",t.qbAttemptsEvidence)
                put("qbMultiplier",t.qbMultiplier)
                put("skillInjuryMultiplier",t.skillInjuryMultiplier)
                put("offenseOutShare",t.offenseOutShare)
                put("epaMultiplier",t.epaMultiplier);put("rateSource",t.rateSource)
                put("lineupVerified",t.lineupVerified);put("warning",t.warning)
            }
            put("scoringShadow",JSONObject().apply {
                put("method",sc.method);put("note",sc.warning)
                put("n",sc.n);put("meanAway",sc.meanAway);put("meanHome",sc.meanHome)
                put("meanTotal",sc.meanTotal);put("medianTotal",sc.medianTotal)
                put("sdTotal",sc.sdTotal);put("p10Total",sc.p10Total);put("p90Total",sc.p90Total)
                put("pOver",sc.pOver ?: JSONObject.NULL)
                put("pUnder",sc.pUnder ?: JSONObject.NULL)
                put("pPush",sc.pPush ?: JSONObject.NULL)
                put("awayWinRate",sc.awayWinRate);put("homeWinRate",sc.homeWinRate)
                put("tieRate",sc.tieRate)
                put("awayInputs",parameter(sc.away));put("homeInputs",parameter(sc.home))
                put("topScores",JSONArray().apply { sc.topScores.forEach{ f ->
                    put(JSONObject().apply { put("away",f.away);put("home",f.home);put("n",f.count) })
                }})
                put("sourceCutoffWeek",b.calibration.cutoffWeek)
                put("pregameSnapshot",b.pregame)
            })
        }
        put("meanTotal",b.meanTotal);put("medianTotal",b.medianTotal);put("sdTotal",b.sdTotal)
        put("p10",b.p10);put("p25",b.p25);put("p75",b.p75);put("p90",b.p90)
        put("meanAway",b.meanAway);put("meanHome",b.meanHome)
        put("awayWinRate",b.awayWinRate);put("homeWinRate",b.homeWinRate);put("tieRate",b.tieRate)
        put("overtimeRate",b.overtimeRate);put("regulationTieRate",b.regulationTieRate)
        put("integrity",JSONObject().apply {
            put("rulesVersion","V1.6_GAME_INTEGRITY")
            put("acceptedRuns",b.n);put("rejectedRuns",0)
            put("meanErrors",0)
        })
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
            put("matchupExpectedAwayPpgReference",c.awayMatchupPpg)
            put("matchupExpectedHomePpgReference",c.homeMatchupPpg)
            put("awayMetricsSource",c.awayMetricsSource)
            put("homeMetricsSource",c.homeMetricsSource)
            put("playerSource",c.playerSource)
            put("seasonTotalCutoff", "UNKNOWN - excluded from pregame calibration")
            put("awayScoringGap",b.meanAway-c.awayMatchupPpg)
            put("homeScoringGap",b.meanHome-c.homeMatchupPpg)
            put("scoringReferenceNotTarget",true)
            put("calibrationWarning",if(kotlin.math.abs(b.meanAway-c.awayMatchupPpg)/c.awayMatchupPpg>.25 ||
                kotlin.math.abs(b.meanHome-c.homeMatchupPpg)/c.homeMatchupPpg>.25)
                    "Simulated score differs >25% from prior-week matchup reference; unvalidated" else "")
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
