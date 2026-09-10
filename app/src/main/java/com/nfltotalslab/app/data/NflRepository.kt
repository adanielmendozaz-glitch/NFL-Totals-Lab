package com.nfltotalslab.app.data

import com.nfltotalslab.app.model.TotalsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NflRepository(
    private val db:DbHelper,
    private val api:NflverseService=NflverseService()
){
    private val engine=TotalsEngine(100_000)
    private val modelVersion="0.4"

    suspend fun sync(season:Int):SyncSummary = withContext(Dispatchers.IO){
        val schedule=api.fetchSchedule(season)
        db.upsertGames(schedule)

        val pbp = runCatching { api.fetchPbpMetrics(season) }.getOrElse { emptyMap() }
        val roster = runCatching { api.fetchRosterCounts(season) }.getOrElse { emptyMap<String,Int>() to 0 }
        val injuries = runCatching { api.fetchInjuryCounts(season) }.getOrElse { emptyMap<String,Int>() to 0 }

        val scoring=mutableMapOf<String,Pair<Int,Int>>()
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
        val settledBets = settlePendingBets(db)

        val activeWeek=schedule
            .asSequence()
            .filter{it.gameType=="REG" && !it.finished}
            .map{it.week}
            .minOrNull()

        val metricMap=metrics.associateBy{it.team}
        val candidates=if(activeWeek==null) emptyList() else schedule.filter{
            it.gameType=="REG" &&
            it.week==activeWeek &&
            !it.finished &&
            it.totalLine!=null
        }

        val autoAnalyzed=withContext(Dispatchers.Default){
            var count=0
            candidates.forEach{game->
                val away=metricMap[game.awayTeam]
                val home=metricMap[game.homeTeam]
                val key=inputKey(game,away,home)
                if(!db.hasAutoPrediction(game.gameId,key)){
                    val p=engine.predict(game,away,home).copy(
                        analysisSource="AUTO_CENSUS",
                        modelVersion=modelVersion,
                        inputKey=key
                    )
                    db.savePrediction(p)
                    count++
                }
            }
            count
        }

        db.putKv("last_sync",System.currentTimeMillis().toString())
        db.putKv("active_week",activeWeek?.toString() ?: "")

        val bits=mutableListOf<String>()
        bits += "Sync NFL completado"
        activeWeek?.let{bits += "Week $it"}
        bits += "AUTO $autoAnalyzed"
        if(settledBets>0) bits += "$settledBets apuesta(s) liquidada(s)"

        SyncSummary(
            scheduleGames=schedule.size,
            pbpTeams=pbp.size,
            rosterPlayers=roster.second,
            injuryRows=injuries.second,
            message=bits.joinToString(" · "),
            autoAnalyzed=autoAnalyzed,
            activeWeek=activeWeek
        )
    }

    fun games(season:Int)=db.loadGames(season)
    fun metrics(season:Int)=db.loadMetrics(season)
    fun predictions()=db.loadPredictions()
    fun bets()=db.loadBets()
    fun bank()=db.loadBank()

    suspend fun analyze(game:GameRecord):Prediction = withContext(Dispatchers.Default){
        require(!game.finished){"No se permite análisis postgame"}
        require(game.totalLine!=null){"El partido no tiene línea O/U disponible"}
        val m=db.loadMetrics(game.season).associateBy{it.team}
        val away=m[game.awayTeam]
        val home=m[game.homeTeam]
        val p=engine.predict(game,away,home).copy(
            analysisSource="MANUAL",
            modelVersion=modelVersion,
            inputKey=inputKey(game,away,home)
        )
        db.savePrediction(p)
        p
    }

    fun addBet(p:Prediction,odds:Double=1.91,stake:Double=100.0){
        db.saveBet(BetRecord(predictionId=p.id,gameId=p.gameId,market="${p.pick} ${p.line}",odds=odds,stake=stake))
    }

    fun addBank(amount:Double,note:String)=db.saveBankEntry(BankEntry(amount=amount,note=note))
    fun lastSync():Long?=db.getKv("last_sync")?.toLongOrNull()

    private fun inputKey(game:GameRecord,away:TeamMetrics?,home:TeamMetrics?):String{
        fun m(x:TeamMetrics?):String = if(x==null) "NA" else listOf(
            x.games,x.plays,x.drives,x.offEpaPerPlay,x.defEpaAllowedPerPlay,
            x.successRate,x.explosiveRate,x.turnoverRate,x.tdPerDrive,
            x.fgPerDrive,x.drivesPerGame
        ).joinToString(",")

        return listOf(
            modelVersion,game.gameId,game.totalLine,m(away),m(home)
        ).joinToString("|")
    }
}
