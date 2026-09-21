package com.nfltotalslab.app.data

import org.json.JSONArray
import org.json.JSONObject

object DataVaultExporter {
    fun build(
        season:Int,
        games:List<GameRecord>,
        metrics:List<TeamMetrics>,
        predictions:List<Prediction>,
        shadows:List<ShadowPrediction>,
        calibrations:List<CalibrationSnapshot>,
        bets:List<BetRecord>,
        bank:List<BankEntry>,
        lastSync:Long?,
        lastDeepSync:Long?
    ):String{
        val root=JSONObject()
        root.put("schema","NFL_TOTALS_LAB_DATA_VAULT_V1")
        root.put("exported_at",System.currentTimeMillis())
        root.put("season",season)
        root.put("app_version","0.9.2.3")
        root.put("current_model_version","0.9.0-integrity")
        root.put("last_sync",lastSync ?: JSONObject.NULL)
        root.put("last_deep_sync",lastDeepSync ?: JSONObject.NULL)

        val official=predictions.filter{it.analysisSource=="AUTO_CENSUS"}
            .groupBy{it.gameId}.mapNotNull{(_,rows)->rows.maxByOrNull{it.createdAt}}
        val settled=official.filter{it.result=="WIN"||it.result=="LOSS"||it.result=="PUSH"}
        val versions=official.groupingBy{it.modelVersion}.eachCount()
        val expectedTeams=games.filter{it.gameType=="REG"}.flatMap{listOf(it.awayTeam,it.homeTeam)}.toSet()
        val metricTeams=metrics.map{it.team}.toSet()

        root.put("diagnostics",JSONObject().apply{
            put("official_games",official.size)
            put("official_settled",settled.size)
            put("official_wins",settled.count{it.result=="WIN"})
            put("official_losses",settled.count{it.result=="LOSS"})
            put("official_pushes",settled.count{it.result=="PUSH"})
            put("duplicate_auto_games",predictions.filter{it.analysisSource=="AUTO_CENSUS"}.groupBy{it.gameId}.count{it.value.size>1})
            put("missing_metric_teams",JSONArray((expectedTeams-metricTeams).sorted()))
            put("model_versions",JSONObject().apply{versions.forEach{(k,v)->put(k,v)}})
            put("note","Predicciones históricas pueden conservar modelVersion=0.4; V0.9 corrige el versionado hacia adelante.")
        })

        root.put("games",JSONArray().apply{games.forEach{g->put(JSONObject().apply{
            put("gameId",g.gameId);put("season",g.season);put("week",g.week);put("gameType",g.gameType)
            put("gameDay",g.gameDay);put("gameTime",g.gameTime);put("awayTeam",g.awayTeam);put("homeTeam",g.homeTeam)
            putNullable("awayScore",g.awayScore);putNullable("homeScore",g.homeScore);putNullable("totalLine",g.totalLine)
            putNullable("spreadLine",g.spreadLine);putNullable("roof",g.roof);putNullable("surface",g.surface)
            putNullable("temp",g.temp);putNullable("wind",g.wind);put("finished",g.finished)
        })}})

        root.put("metrics",JSONArray().apply{metrics.forEach{m->put(JSONObject().apply{
            put("season",m.season);put("team",m.team);put("games",m.games);put("plays",m.plays);put("drives",m.drives)
            put("pointsFor",m.pointsFor);put("offEpaPerPlay",m.offEpaPerPlay);put("defEpaAllowedPerPlay",m.defEpaAllowedPerPlay)
            put("successRate",m.successRate);put("explosiveRate",m.explosiveRate);put("turnoverRate",m.turnoverRate)
            put("tdPerDrive",m.tdPerDrive);put("fgPerDrive",m.fgPerDrive);put("drivesPerGame",m.drivesPerGame)
            put("pointsPerDrive",m.pointsPerDrive);put("rosterCount",m.rosterCount);put("injuryCount",m.injuryCount)
        })}})

        root.put("predictions",JSONArray().apply{predictions.forEach{p->put(JSONObject().apply{
            put("id",p.id);put("gameId",p.gameId);put("season",p.season);put("week",p.week);put("awayTeam",p.awayTeam);put("homeTeam",p.homeTeam)
            put("line",p.line);put("pick",p.pick);put("probability",p.probability);put("projection",p.projection);put("classification",p.classification)
            put("createdAt",p.createdAt);putNullable("finalTotal",p.finalTotal);putNullable("result",p.result);put("analysisSource",p.analysisSource)
            put("modelVersion",p.modelVersion);put("inputKey",p.inputKey)
            put("engines",JSONArray().apply{p.engines.forEach{e->put(JSONObject().apply{
                put("name",e.name);put("projection",e.projection);put("pOver",e.pOver);put("pUnder",e.pUnder)
            })}})
        })}})

        root.put("shadows",JSONArray().apply{shadows.forEach{x->put(JSONObject().apply{
            put("id",x.id);put("gameId",x.gameId);put("season",x.season);put("week",x.week);put("awayTeam",x.awayTeam);put("homeTeam",x.homeTeam)
            put("line",x.line);put("modelName",x.modelName);put("pick",x.pick);put("probability",x.probability);put("projection",x.projection)
            put("inputKey",x.inputKey);put("createdAt",x.createdAt);putNullable("finalTotal",x.finalTotal);putNullable("result",x.result)
        })}})

        root.put("calibrations",JSONArray().apply{calibrations.forEach{x->put(JSONObject().apply{
            put("id",x.id);put("predictionId",x.predictionId);put("gameId",x.gameId);put("season",x.season);put("week",x.week)
            put("awayTeam",x.awayTeam);put("homeTeam",x.homeTeam);put("line",x.line);put("pick",x.pick);put("rawProbability",x.rawProbability)
            put("calibratedProbability",x.calibratedProbability);put("intercept",x.intercept);put("slope",x.slope);put("trainN",x.trainN)
            put("maturity",x.maturity);put("inputKey",x.inputKey);put("createdAt",x.createdAt);putNullable("finalTotal",x.finalTotal);putNullable("result",x.result)
        })}})

        root.put("bets",JSONArray().apply{bets.forEach{b->put(JSONObject().apply{
            put("id",b.id);put("predictionId",b.predictionId);put("gameId",b.gameId);put("market",b.market);put("odds",b.odds)
            put("stake",b.stake);put("status",b.status);put("pnl",b.pnl);put("createdAt",b.createdAt)
        })}})

        root.put("bank",JSONArray().apply{bank.forEach{b->put(JSONObject().apply{
            put("id",b.id);put("amount",b.amount);put("note",b.note);put("createdAt",b.createdAt)
        })}})

        return root.toString(2)
    }

    private fun JSONObject.putNullable(key:String,value:Any?){put(key,value ?: JSONObject.NULL)}
}
