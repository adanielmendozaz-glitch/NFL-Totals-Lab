package com.nfltotalslab.app.data

import com.nfltotalslab.app.model.ShadowLab
import com.nfltotalslab.app.model.TotalsEngine
import com.nfltotalslab.app.calibration.ProgressiveCalibrator
import com.nfltotalslab.app.integrity.KickoffGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NflRepository(
    private val db:DbHelper,
    private val api:NflverseService=NflverseService()
){
    private val engine=TotalsEngine(100_000)
    private val modelVersion="0.4"
    private val deepCacheMs=4L*60L*60L*1000L

    suspend fun fastSync(season:Int):SyncSummary = withContext(Dispatchers.IO){
        val schedule=api.fetchSchedule(season)
        db.upsertGames(schedule)
        db.settlePredictions(schedule)
        db.settleShadowPredictions(schedule)
        db.settleCalibrationSnapshots(schedule)
        val settledBets=settlePendingBets(db)
        db.putKv("last_sync",System.currentTimeMillis().toString())

        val activeWeek=activeWeek(schedule)
        db.putKv("active_week",activeWeek?.toString() ?: "")

        SyncSummary(
            scheduleGames=schedule.size,
            message=buildString{
                append("FAST ✓ · ${schedule.size} juegos")
                activeWeek?.let{append(" · Week $it")}
                if(settledBets>0)append(" · $settledBets apuesta(s) liquidada(s)")
            },
            activeWeek=activeWeek
        )
    }

    suspend fun deepSync(season:Int,force:Boolean=false):SyncSummary = withContext(Dispatchers.IO){
        val schedule=db.loadGames(season)
        if(schedule.isEmpty()) return@withContext SyncSummary(message="DEEP omitido · primero FAST SYNC")

        val now=System.currentTimeMillis()
        val lastDeep=db.getKv("last_deep_sync")?.toLongOrNull() ?: 0L
        val cached=db.loadMetrics(season)
        val cacheFresh=!force && cached.isNotEmpty() && now-lastDeep<deepCacheMs

        var pbpTeams=0
        var rosterRows=0
        var injuryRows=0
        var source="CACHE"
        val metrics:List<TeamMetrics>

        if(cacheFresh){
            metrics=cached
        }else{
            source="FRESH"
            val pbp=runCatching { api.fetchPbpMetrics(season) }.getOrElse { emptyMap() }
            val roster=runCatching { api.fetchRosterCounts(season) }.getOrElse { emptyMap<String,Int>() to 0 }
            val injuries=runCatching { api.fetchInjuryCounts(season) }.getOrElse { emptyMap<String,Int>() to 0 }

            pbpTeams=pbp.size
            rosterRows=roster.second
            injuryRows=injuries.second

            if(pbp.isEmpty() && cached.isNotEmpty()){
                source="STALE CACHE"
                metrics=cached
            }else{
                if(pbp.isEmpty())source="PRIORS · PBP NO DISPONIBLE"
                val scoring=mutableMapOf<String,Pair<Int,Int>>()
                schedule.filter{it.finished}.forEach{g->
                    val a=scoring[g.awayTeam] ?: (0 to 0)
                    scoring[g.awayTeam]=(a.first+1 to a.second+(g.awayScore?:0))
                    val h=scoring[g.homeTeam] ?: (0 to 0)
                    scoring[g.homeTeam]=(h.first+1 to h.second+(g.homeScore?:0))
                }

                val teams=(schedule.flatMap{listOf(it.awayTeam,it.homeTeam)} + pbp.keys).toSet()
                metrics=teams.map{team->
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
                if(pbp.isNotEmpty())db.putKv("last_deep_sync",now.toString())
            }
        }

        val week=activeWeek(schedule)
        val liveStates=if(week==null) emptyMap() else
            runCatching{api.fetchLiveScores(season,week)}.getOrElse{emptyMap()}
        val blocked=liveStates.values.filter{it.state!="pre"}.map{it.matchKey}.toSet()

        val auto=autoCensus(schedule,metrics,week,blocked)
        val calibration=ensureCalibrationCensus(schedule,metrics,week,blocked)
        val shadow=ensureShadowCensus(schedule,metrics,week,blocked)

        SyncSummary(
            scheduleGames=schedule.size,
            pbpTeams=pbpTeams,
            rosterPlayers=rosterRows,
            injuryRows=injuryRows,
            message="DEEP ✓ · $source · AUTO $auto · CAL $calibration · SHADOW $shadow${week?.let{" · Week $it"} ?: ""}",
            autoAnalyzed=auto,
            activeWeek=week
        )
    }

    suspend fun liveScores(season:Int,week:Int):Map<String,LiveGameState> = withContext(Dispatchers.IO){
        val states=api.fetchLiveScores(season,week)
        val finalsApplied=db.applyLiveFinals(season,week,states.values)
        if(finalsApplied>0){
            val updated=db.loadGames(season)
            db.settlePredictions(updated)
            db.settleShadowPredictions(updated)
            db.settleCalibrationSnapshots(updated)
            settlePendingBets(db)
        }
        states
    }

    fun games(season:Int)=db.loadGames(season)
    fun metrics(season:Int)=db.loadMetrics(season)
    fun predictions()=db.loadPredictions()
    fun shadows()=db.loadShadowPredictions()
    fun calibrations()=db.loadCalibrationSnapshots()
    fun calibrationState()=ProgressiveCalibrator.fit(db.loadPredictions())
    fun bets()=db.loadBets()
    fun bank()=db.loadBank()

    suspend fun analyze(game:GameRecord):Prediction = withContext(Dispatchers.Default){
        require(!game.finished){"No se permite análisis postgame"}
        require(!KickoffGuard.isLocked(game)){"KICKOFF LOCK: el partido ya alcanzó su hora oficial"}
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
    fun lastDeepSync():Long?=db.getKv("last_deep_sync")?.toLongOrNull()

    private suspend fun autoCensus(
        schedule:List<GameRecord>,
        metrics:List<TeamMetrics>,
        week:Int?,
        blocked:Set<String>
    ):Int = withContext(Dispatchers.Default){
        if(week==null)return@withContext 0
        val metricMap=metrics.associateBy{it.team}
        val candidates=schedule.filter{
            it.gameType=="REG" &&
            it.week==week &&
            !it.finished &&
            it.totalLine!=null &&
            !KickoffGuard.isLocked(it) &&
            "${it.awayTeam}@${it.homeTeam}" !in blocked
        }

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

    private suspend fun ensureCalibrationCensus(schedule:List<GameRecord>,metrics:List<TeamMetrics>,week:Int?,blocked:Set<String>):Int = withContext(Dispatchers.Default){
        if(week==null)return@withContext 0
        val metricMap=metrics.associateBy{it.team};val state=ProgressiveCalibrator.fit(db.loadPredictions());val cores=db.loadPredictions()
        val candidates=schedule.filter{it.gameType=="REG"&&it.week==week&&!it.finished&&it.totalLine!=null&&!KickoffGuard.isLocked(it)&&"${it.awayTeam}@${it.homeTeam}" !in blocked}
        var count=0
        candidates.forEach{game->val key=inputKey(game,metricMap[game.awayTeam],metricMap[game.homeTeam]);val core=cores.firstOrNull{it.gameId==game.gameId&&it.analysisSource=="AUTO_CENSUS"&&it.inputKey==key}?:return@forEach;if(!db.hasCalibrationSnapshot(core.id)){db.saveCalibrationSnapshot(CalibrationSnapshot(core.id,core.id,core.gameId,core.season,core.week,core.awayTeam,core.homeTeam,core.line,core.pick,core.probability,state.calibrate(core.probability),state.intercept,state.slope,state.trainN,state.maturity,core.inputKey,System.currentTimeMillis()));count++}}
        count
    }

    private suspend fun ensureShadowCensus(
        schedule:List<GameRecord>,
        metrics:List<TeamMetrics>,
        week:Int?,
        blocked:Set<String>
    ):Int = withContext(Dispatchers.Default){
        if(week==null)return@withContext 0

        val metricMap=metrics.associateBy{it.team}
        val cores=db.loadPredictions()
        val candidates=schedule.filter{
            it.gameType=="REG" &&
            it.week==week &&
            !it.finished &&
            it.totalLine!=null &&
            !KickoffGuard.isLocked(it) &&
            "${it.awayTeam}@${it.homeTeam}" !in blocked
        }

        var count=0
        candidates.forEach{game->
            val key=inputKey(game,metricMap[game.awayTeam],metricMap[game.homeTeam])
            val core=cores.firstOrNull{
                it.gameId==game.gameId &&
                it.analysisSource=="AUTO_CENSUS" &&
                it.inputKey==key
            } ?: return@forEach

            ShadowLab.fromCore(core).forEach{x->
                if(!db.hasShadowPrediction(x.gameId,x.modelName,x.inputKey)){
                    db.saveShadowPrediction(x)
                    count++
                }
            }
        }
        count
    }

    private fun activeWeek(schedule:List<GameRecord>):Int? =
        schedule.asSequence()
            .filter{it.gameType=="REG" && !it.finished}
            .map{it.week}
            .minOrNull()

    private fun inputKey(game:GameRecord,away:TeamMetrics?,home:TeamMetrics?):String{
        fun m(x:TeamMetrics?):String = if(x==null) "NA" else listOf(
            x.games,x.plays,x.drives,x.offEpaPerPlay,x.defEpaAllowedPerPlay,
            x.successRate,x.explosiveRate,x.turnoverRate,x.tdPerDrive,
            x.fgPerDrive,x.drivesPerGame
        ).joinToString(",")

        return listOf(modelVersion,game.gameId,game.totalLine,m(away),m(home)).joinToString("|")
    }
}
