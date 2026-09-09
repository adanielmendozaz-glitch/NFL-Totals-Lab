package com.nfltotalslab.app.data

import com.nfltotalslab.app.model.TotalsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NflRepository(
    private val db:DbHelper,
    private val api:NflverseService=NflverseService()
){
    private val engine=TotalsEngine(100_000)

    suspend fun sync(season:Int):SyncSummary = withContext(Dispatchers.IO){
        val schedule=api.fetchSchedule(season)
        db.upsertGames(schedule)

        val pbp = runCatching { api.fetchPbpMetrics(season) }.getOrElse { emptyMap() }
        val roster = runCatching { api.fetchRosterCounts(season) }.getOrElse { emptyMap<String,Int>() to 0 }
        val injuries = runCatching { api.fetchInjuryCounts(season) }.getOrElse { emptyMap<String,Int>() to 0 }

        val scoring=mutableMapOf<String,Pair<Int,Int>>() // team -> games, points
        schedule.filter{it.finished}.forEach{g->
            val a=scoring[g.awayTeam] ?: (0 to 0)
            scoring[g.awayTeam]=(a.first+1 to a.second+(g.awayScore?:0))
            val h=scoring[g.homeTeam] ?: (0 to 0)
            scoring[g.homeTeam]=(h.first+1 to h.second+(g.homeScore?:0))
        }

        val teams=(schedule.flatMap{listOf(it.awayTeam,it.homeTeam)} + pbp.keys).toSet()
        val metrics=teams.map{team->
            val p=pbp[team] ?: TeamMetrics(season,team)
            val sc=scoring[team] ?: (0 to 0)
            val drives=p.drives.coerceAtLeast(1)
            p.copy(
                games=maxOf(p.games,sc.first),
                pointsFor=sc.second,
                pointsPerDrive=if(p.drives>0)sc.second.toDouble()/drives else 2.05,
                rosterCount=roster.first[team] ?: 0,
                injuryCount=injuries.first[team] ?: 0
            )
        }
        db.upsertMetrics(metrics)
        db.settlePredictions(schedule)
        db.putKv("last_sync",System.currentTimeMillis().toString())
        SyncSummary(schedule.size,pbp.size,roster.second,injuries.second,"Sincronización NFL completada")
    }

    fun games(season:Int)=db.loadGames(season)
    fun metrics(season:Int)=db.loadMetrics(season)
    fun predictions()=db.loadPredictions()
    fun bets()=db.loadBets()
    fun bank()=db.loadBank()

    suspend fun analyze(game:GameRecord):Prediction = withContext(Dispatchers.Default){
        val m=db.loadMetrics(game.season).associateBy{it.team}
        val p=engine.predict(game,m[game.awayTeam],m[game.homeTeam])
        db.savePrediction(p)
        p
    }

    fun addBet(p:Prediction,odds:Double=1.91,stake:Double=100.0){
        db.saveBet(BetRecord(predictionId=p.id,gameId=p.gameId,market="${p.pick} ${p.line}",odds=odds,stake=stake))
    }

    fun addBank(amount:Double,note:String)=db.saveBankEntry(BankEntry(amount=amount,note=note))
    fun lastSync():Long?=db.getKv("last_sync")?.toLongOrNull()
}
