package com.nfltotalslab.app.data

import com.nfltotalslab.app.model.ShadowLab
import com.nfltotalslab.app.model.OpponentAdjustmentShadow
import com.nfltotalslab.app.model.AdaptiveEnsembleShadow
import com.nfltotalslab.app.model.RosterAdjustmentShadow
import com.nfltotalslab.app.model.MatchupScoreLab
import com.nfltotalslab.app.model.TotalsEngine
import com.nfltotalslab.app.model.CoreV10ControlEngine
import com.nfltotalslab.app.model.DecisionEngineV2
import com.nfltotalslab.app.model.LegacyTotalsEngine
import com.nfltotalslab.app.calibration.ProgressiveCalibrator
import com.nfltotalslab.app.integrity.KickoffGuard
import com.nfltotalslab.app.simcast.*
import java.security.MessageDigest
import org.json.JSONObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NflRepository(
    private val db:DbHelper,
    private val api:NflverseService=NflverseService()
){
    private val engine=TotalsEngine(100_000)
    private val v10ControlEngine=CoreV10ControlEngine(100_000)
    private val legacyEngine=LegacyTotalsEngine(100_000)
    private val dataCenter=DataCenter(db)
    private val rosterApi=EspnRosterService()
    private val matchupApi=MatchupFeatureService()
    private val modelVersion="1.2.0-data-center"
    private val deepCacheMs=4L*60L*60L*1000L
    private val matchupCacheMs=12L*60L*60L*1000L

    suspend fun fastSync(season:Int):SyncSummary = withContext(Dispatchers.IO){
        val schedule=api.fetchSchedule(season)
        db.upsertGames(schedule)
        db.settlePredictions(schedule)
        db.settleShadowPredictions(schedule)
        db.settleCalibrationSnapshots(schedule)
        db.settleMatchupSnapshots(schedule)
        db.settleMatchupScoreCensus(schedule)
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

        var matchupFeatures=db.loadMatchupFeatures(season)
        var matchupDataSource="CACHE"
        val lastMatchup=db.getKv("last_matchup_feature_sync")?.toLongOrNull() ?: 0L
        val matchupFresh=!force && matchupFeatures.isNotEmpty() && now-lastMatchup<matchupCacheMs

        if(!matchupFresh){
            val fresh=runCatching{matchupApi.fetchFeatures(season)}.getOrElse{emptyList()}
            if(fresh.isNotEmpty()){
                db.upsertMatchupFeatures(fresh)
                matchupFeatures=fresh
                db.putKv("last_matchup_feature_sync",System.currentTimeMillis().toString())
                matchupDataSource="FRESH"
            }else{
                matchupDataSource=if(matchupFeatures.isEmpty())"NO DATA" else "STALE CACHE"
            }
        }

        val week=activeWeek(schedule)
        val liveStates=if(week==null) emptyMap() else
            runCatching{api.fetchLiveScores(season,week)}.getOrElse{emptyMap()}
        val blocked=liveStates.values.filter{it.state!="pre"}.map{it.matchKey}.toSet()

        val auto=autoCensus(schedule,metrics,week,blocked)
        val matchupSnapshots=ensureMatchupSnapshots(schedule,matchupFeatures,week,blocked)
        val matchupScore=ensureMatchupScoreCensus(
            schedule,metrics,matchupFeatures,week,blocked
        )
        val calibration=rollCalibration(schedule,week,blocked)
        val shadow=ensureShadowCensus(schedule,metrics,week,blocked)

        SyncSummary(
            scheduleGames=schedule.size,
            pbpTeams=pbpTeams,
            rosterPlayers=rosterRows,
            injuryRows=injuryRows,
            message="DEEP ✓ · $source · FEAT $matchupDataSource ${matchupFeatures.size} · SNAP $matchupSnapshots · MATCHUP $matchupScore · AUTO $auto · CAL↻ $calibration · SHADOW $shadow${week?.let{" · Week $it"} ?: ""}",
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
            db.settleMatchupSnapshots(updated)
            db.settleMatchupScoreCensus(updated)

            // Nuevo FINAL -> recalibra juegos posteriores todavía pregame.
            // KickoffGuard impide modificar partidos ya iniciados.
            rollCalibration(updated,week)

            settlePendingBets(db)
        }
        states
    }

    /** Separate experimental play-level scenario; does not overwrite official predictions. */
    suspend fun simulateMatch(game:GameRecord,seed:Int,lineup:GameLineup):GameSim = withContext(Dispatchers.Default){
        val a=dataCenter.usageFor(game,game.awayTeam)
        val h=dataCenter.usageFor(game,game.homeTeam)
        val m=db.loadMetrics(game.season).associateBy{it.team}
        val calibration=PregameSimcastCalibration.build(game,db.loadGames(game.season),m[game.awayTeam],m[game.homeTeam])
        // Strict cutoff for season-to-date PBP: if a snapshot includes more games than
        // were completed before this fixture, discard it instead of leaking future plays.
        val safeA=dataCenter.simcastMetrics(game,game.awayTeam,m[game.awayTeam],calibration.awayGames)
        val safeH=dataCenter.simcastMetrics(game,game.homeTeam,m[game.homeTeam],calibration.homeGames)
        val inputs=calibration.copy(awayMetricsSource=safeA.origin,homeMetricsSource=safeH.origin)
        val run=PlayerUsageDriveEngine().simulate(game,a,h,safeA.metrics,safeH.metrics,seed,lineup,inputs)
        check(run.integrityErrors.isEmpty()) { "Reglas NFL inválidas: ${run.integrityErrors.joinToString()}" }
        withContext(Dispatchers.IO){db.saveSimcastReport(game,seed,SimcastArchive.toJson(run).toString())}
        run
    }

    /** Experimental batches never change the official Core pick or calibrated probabilities. */
    suspend fun simulateBatch(
        game:GameRecord, seed:Int, lineup:GameLineup, count:Int, onProgress:(Int)->Unit
    ):BatchReport = withContext(Dispatchers.Default) {
        val a=dataCenter.usageFor(game,game.awayTeam)
        val h=dataCenter.usageFor(game,game.homeTeam)
        val m=db.loadMetrics(game.season).associateBy{it.team}
        val c=PregameSimcastCalibration.build(game,db.loadGames(game.season),m[game.awayTeam],m[game.homeTeam])
        val safeA=dataCenter.simcastMetrics(game,game.awayTeam,m[game.awayTeam],c.awayGames)
        val safeH=dataCenter.simcastMetrics(game,game.homeTeam,m[game.homeTeam],c.homeGames)
        val aMetrics=safeA.metrics
        val hMetrics=safeH.metrics
        val calibration=c.copy(awayMetricsSource=safeA.origin,homeMetricsSource=safeH.origin)
        val pregame=!game.finished && !KickoffGuard.isLocked(game)
        val signatureText=buildString {
            append("V161|${game.season}|${game.week}|${game.gameId}|${game.totalLine}|")
            append("${calibration.method}|${c.cutoffWeek}|${c.leagueGames}|")
            listOf(game.awayTeam,game.homeTeam).forEach { team ->
                val mm=if(team==game.awayTeam)aMetrics else hMetrics
                append("${team}:${if(team==game.awayTeam)safeA.origin else safeH.origin}:${mm?.games}:${mm?.offEpaPerPlay}:${mm?.defEpaAllowedPerPlay}:${mm?.offPassEpa}:${mm?.defPassEpaAllowed}:${mm?.offRushEpa}:${mm?.defRushEpaAllowed}|")
            }
            // Hash actual selected prior-week player inputs, not a season CSV that may
            // have been overwritten after this matchup. Later weeks cannot influence this signature.
            listOf(a,h).forEach { input->
                append("${input.team}:W${input.throughWeek}|")
                input.players.sortedBy { it.id }.forEach { p->
                    append("${p.id}:${p.passAttempts}:${p.completions}:${p.passingYards}:${p.carries}:${p.rushingYards}:${p.targets}:${p.receptions}:${p.receivingYards}|")
                }
            }
            append("${lineup.away.startingQb}:${lineup.home.startingQb}|")
            append(lineup.away.outNames.sorted().joinToString(","))
            append("|")
            append(lineup.home.outNames.sorted().joinToString(","))
        }
        val signature=MessageDigest.getInstance("SHA-256").digest(signatureText.toByteArray())
            .joinToString(""){ "%02x".format(it) }
        val parentJob=currentCoroutineContext()
        val report=SimcastBatchEngine().run(game,a,h,aMetrics,hMetrics,lineup,calibration,
            count,seed,pregame,onProgress,shouldContinue={parentJob.isActive},inputSignature=signature)
        withContext(Dispatchers.IO){
            val j=SimcastBatchArchive.toJson(report)
            db.saveSimcastBatch(game,j.getString("batchKey"),pregame,j.toString())
        }
        report
    }

    /** Only saved *pregame* batches for subsequently finalized games are auditable. */
    fun batchAudit(season:Int):SimcastBatchAudit {
        val completed=db.loadGames(season).filter{it.finished}.associateBy{it.gameId}
        val latest=linkedMapOf<String,JSONObject>()
        val all=db.exportSimcastBatches(season)
        for(i in 0 until all.length()){
            val j=all.optJSONObject(i) ?: continue
            if(!j.optBoolean("pregame",false))continue
            val id=j.optString("gameId")
            if(id in completed && id !in latest)latest[id]=j
        }
        if(latest.isEmpty())return SimcastBatchAudit()
        var errorSum=0.0;var brierSum=0.0;var graded=0;var correct=0
        latest.forEach{ (id,j)->
            val g=completed.getValue(id)
            val observed=g.finalTotal ?: return@forEach
            errorSum+=kotlin.math.abs(j.optDouble("meanTotal",0.0)-observed)
            if(!j.isNull("line")){
                val line=j.optDouble("line")
                if(observed.toDouble()!=line && !j.isNull("pOver")){
                    val prob=j.optDouble("pOver").coerceIn(0.0,1.0)
                    val result=if(observed>line)1.0 else 0.0
                    brierSum+=(prob-result)*(prob-result)
                    graded++
                    if((prob>=.5)==(observed>line))correct++
                }
            }
        }
        return SimcastBatchAudit(latest.size,errorSum/latest.size,
            if(graded>0)brierSum/graded else null,graded,
            if(graded>0)correct.toDouble()/graded else null)
    }

    fun feeds(season:Int) = dataCenter.all(season)
    suspend fun refreshFeeds(season:Int,force:Boolean=false) = dataCenter.refresh(season,force)
    suspend fun importFeed(season:Int,feed:NflFeed,reader:java.io.BufferedReader) = dataCenter.importDocument(feed,season,reader)

    fun games(season:Int)=db.loadGames(season)
    fun metrics(season:Int)=db.loadMetrics(season)
    fun predictions()=db.loadPredictions()
    fun shadows()=db.loadShadowPredictions()
    fun calibrations()=db.loadCalibrationSnapshots()
    fun matchupFeatures(season:Int)=db.loadMatchupFeatures(season)
    fun matchupSnapshots()=db.loadMatchupSnapshots()
    fun matchupScoreCensus()=db.loadMatchupScoreCensus()
    fun calibrationState()=ProgressiveCalibrator.fit(db.loadPredictions())
    fun bets()=db.loadBets()
    fun bank()=db.loadBank()

    suspend fun analyze(game:GameRecord):Prediction = withContext(Dispatchers.Default){
        require(!game.finished){"No se permite análisis postgame"}
        require(!KickoffGuard.isLocked(game)){"KICKOFF LOCK: el partido ya alcanzó su hora oficial"}
        require(game.totalLine!=null){"El partido no tiene línea O/U disponible"}
        val m=db.loadMetrics(game.season).associateBy{it.team}
        val away=dataCenter.enriched(game,m[game.awayTeam])
        val home=dataCenter.enriched(game,m[game.homeTeam])
        val p=engine.predict(game,away,home).copy(
            analysisSource="MANUAL",
            modelVersion=modelVersion,
            inputKey=inputKey(game,away,home)
        )
        db.savePrediction(p)
        p
    }

    fun exportDataVault(season:Int):String =
        DataVaultExporter.build(
            season=season,
            games=db.loadGames(season),
            metrics=db.loadMetrics(season),
            predictions=db.loadPredictions(),
            shadows=db.loadShadowPredictions(),
            calibrations=db.loadCalibrationSnapshots(),
            matchupFeatures=db.loadMatchupFeatures(season),
            matchupSnapshots=db.loadMatchupSnapshots(),
            matchupScores=db.loadMatchupScoreCensus(),
            bets=db.loadBets(),
            bank=db.loadBank(),
            lastSync=lastSync(),
            lastDeepSync=lastDeepSync(),
            feedBackup=dataCenter.export(season),
            feedHistory=dataCenter.exportHistory(season),
            simcastReports=db.exportSimcastReports(season),
            batchReports=db.exportSimcastBatches(season)
        )

    fun previewDataVault(raw:String):VaultImportPreview =
        DataVaultImporter.preview(raw)

    suspend fun importDataVault(raw:String):VaultRestoreResult =
        withContext(Dispatchers.IO){
            DataVaultImporter.restore(db,raw)
        }

    suspend fun rosterIntelligence(game:GameRecord,core:Prediction?):GameRosterIntelligence =
        withContext(Dispatchers.IO){
            val intel=rosterApi.fetchGameIntelligence(game)
            if(core!=null && !game.finished && !KickoffGuard.isLocked(game)){
                RosterAdjustmentShadow.fromCore(core,intel)?.let{shadow->
                    if(!db.hasShadowPrediction(shadow.gameId,shadow.modelName,shadow.inputKey)){
                        db.saveShadowPrediction(shadow)
                    }
                }
            }
            intel
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
            val away=dataCenter.enriched(game,metricMap[game.awayTeam])
            val home=dataCenter.enriched(game,metricMap[game.homeTeam])
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

    /**
     * Rolling Pregame Calibration:
     * - Core RAW permanece intacto.
     * - CAL puede actualizarse cuando aumenta la muestra de FINALs.
     * - Después del kickoff no vuelve a modificarse.
     * - Se conserva un snapshot efectivo por predictionId.
     */
    private fun rollCalibration(
        schedule:List<GameRecord>,
        week:Int?,
        blocked:Set<String> = emptySet()
    ):Int{
        if(week==null)return 0

        val predictions=db.loadPredictions()
        val state=ProgressiveCalibrator.fit(predictions)

        val cores=predictions
            .filter{it.analysisSource=="AUTO_CENSUS"}
            .groupBy{it.gameId}
            .mapValues{(_,rows)->rows.maxByOrNull{it.createdAt}}

        val existing=db.loadCalibrationSnapshots().associateBy{it.predictionId}

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
            val core=cores[game.gameId] ?: return@forEach
            val old=existing[core.id]

            val coefficientsChanged=old!=null && (
                kotlin.math.abs(old.intercept-state.intercept)>1e-9 ||
                kotlin.math.abs(old.slope-state.slope)>1e-9
            )

            val shouldRefresh=
                old==null ||
                (
                    old.result==null &&
                    (
                        state.trainN>old.trainN ||
                        coefficientsChanged
                    )
                )

            if(shouldRefresh){
                val now=System.currentTimeMillis()
                val calibrated=state.calibrate(core.probability)

                val snapshot=if(old==null){
                    CalibrationSnapshot(
                        id=core.id,
                        predictionId=core.id,
                        gameId=core.gameId,
                        season=core.season,
                        week=core.week,
                        awayTeam=core.awayTeam,
                        homeTeam=core.homeTeam,
                        line=core.line,
                        pick=core.pick,
                        rawProbability=core.probability,
                        calibratedProbability=calibrated,
                        intercept=state.intercept,
                        slope=state.slope,
                        trainN=state.trainN,
                        maturity=state.maturity,
                        inputKey=core.inputKey,
                        createdAt=now
                    )
                }else{
                    old.copy(
                        rawProbability=core.probability,
                        calibratedProbability=calibrated,
                        intercept=state.intercept,
                        slope=state.slope,
                        trainN=state.trainN,
                        maturity=state.maturity,
                        inputKey=core.inputKey,
                        createdAt=now
                    )
                }

                db.saveCalibrationSnapshot(snapshot)
                count++
            }
        }

        return count
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
        val shadowHistory=db.loadShadowPredictions()
        val matchupByGame=db.loadMatchupScoreCensus().associateBy{it.gameId}
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

            // A/B control: Core V1.0 exacto, sólo Shadow.
            val v10=v10ControlEngine.predict(
                game,
                metricMap[game.awayTeam],
                metricMap[game.homeTeam]
            )
            val v10Shadow=ShadowPrediction(
                id=core.id*100L+89L,
                gameId=core.gameId,
                season=core.season,
                week=core.week,
                awayTeam=core.awayTeam,
                homeTeam=core.homeTeam,
                line=core.line,
                modelName="Core 1.0.0 Control",
                pick=v10.pick,
                probability=v10.probability,
                projection=v10.projection,
                inputKey="${core.inputKey}|CORE_V1_0_CONTROL",
                createdAt=core.createdAt
            )
            if(!db.hasShadowPrediction(
                    v10Shadow.gameId,
                    v10Shadow.modelName,
                    v10Shadow.inputKey
                )){
                db.saveShadowPrediction(v10Shadow)
                count++
            }

            // A/B control: Core V0.9 exacto, sólo Shadow.
            val legacyCore=legacyEngine.predict(
                game,
                metricMap[game.awayTeam],
                metricMap[game.homeTeam]
            )
            val legacyShadow=ShadowPrediction(
                id=core.id*100L+90L,
                gameId=core.gameId,
                season=core.season,
                week=core.week,
                awayTeam=core.awayTeam,
                homeTeam=core.homeTeam,
                line=core.line,
                modelName="Core 0.9.0 Control",
                pick=legacyCore.pick,
                probability=legacyCore.probability,
                projection=legacyCore.projection,
                inputKey="${core.inputKey}|LEGACY_CORE_0_9_0",
                createdAt=core.createdAt
            )
            if(!db.hasShadowPrediction(
                    legacyShadow.gameId,
                    legacyShadow.modelName,
                    legacyShadow.inputKey
                )){
                db.saveShadowPrediction(legacyShadow)
                count++
            }

            ShadowLab.fromCore(core).forEach{x->
                if(!db.hasShadowPrediction(x.gameId,x.modelName,x.inputKey)){
                    db.saveShadowPrediction(x)
                    count++
                }
            }

            OpponentAdjustmentShadow.fromCore(
                game=game,
                core=core,
                metrics=metrics,
                schedule=schedule,
                engine=engine
            ).forEach{x->
                if(!db.hasShadowPrediction(x.gameId,x.modelName,x.inputKey)){
                    db.saveShadowPrediction(x)
                    count++
                }
            }

            AdaptiveEnsembleShadow.fromCore(core,shadowHistory)?.let{x->
                if(!db.hasShadowPrediction(x.gameId,x.modelName,x.inputKey)){
                    db.saveShadowPrediction(x)
                    count++
                }
            }

            DecisionEngineV2.fromCore(
                game=game,
                core=core,
                away=metricMap[game.awayTeam],
                home=metricMap[game.homeTeam],
                matchup=matchupByGame[game.gameId]
            ).forEach{x->
                if(!db.hasShadowPrediction(x.gameId,x.modelName,x.inputKey)){
                    db.saveShadowPrediction(x)
                    count++
                }
            }
        }
        count
    }

    private fun ensureMatchupScoreCensus(
        schedule:List<GameRecord>,
        metrics:List<TeamMetrics>,
        features:List<MatchupFeature>,
        week:Int?,
        blocked:Set<String>
    ):Int{
        if(week==null)return 0

        val history=db.loadMatchupScoreCensus()
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
            if(db.hasMatchupScoreCensus(game.gameId))return@forEach

            val score=MatchupScoreLab.build(
                game=game,
                metrics=metrics,
                features=features,
                schedule=schedule,
                history=history
            ) ?: return@forEach

            db.saveMatchupScoreCensus(score)

            val shadow=MatchupScoreLab.toShadow(score)
            if(!db.hasShadowPrediction(shadow.gameId,shadow.modelName,shadow.inputKey)){
                db.saveShadowPrediction(shadow)
            }

            count++
        }
        return count
    }

    private fun ensureMatchupSnapshots(
        schedule:List<GameRecord>,features:List<MatchupFeature>,week:Int?,blocked:Set<String>
    ):Int{
        if(week==null || features.isEmpty())return 0
        val byTeam=features.groupBy{it.team}
        val cores=db.loadPredictions()
            .filter{it.analysisSource=="AUTO_CENSUS" && it.modelVersion==modelVersion}
            .groupBy{it.gameId}.mapValues{(_,rows)->rows.maxByOrNull{it.createdAt}}

        val candidates=schedule.filter{
            it.gameType=="REG" && it.week==week && !it.finished && it.totalLine!=null &&
            !KickoffGuard.isLocked(it) && "${it.awayTeam}@${it.homeTeam}" !in blocked
        }

        var rows=0
        candidates.forEach{game->
            val core=cores[game.gameId]?:return@forEach
            listOf(game.awayTeam to game.homeTeam,game.homeTeam to game.awayTeam).forEach{(team,opponent)->
                byTeam[team].orEmpty().forEach{f->
                    db.saveMatchupSnapshot(MatchupFeatureSnapshot(
                        gameId=game.gameId,season=game.season,week=game.week,team=team,opponent=opponent,
                        feature=f.feature,side=f.side,value=f.value,sampleN=f.sampleN,source=f.source,scope=f.scope,
                        reliability=f.reliability,corePredictionId=core.id,coreProjection=core.projection,
                        marketLine=game.totalLine,coreInputKey=core.inputKey
                    ))
                    rows++
                }
            }
        }
        return rows
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
            x.fgPerDrive,x.drivesPerGame,x.pointsFor,x.pointsPerDrive
        ).joinToString(",")

        return listOf(modelVersion,game.gameId,game.totalLine,m(away),m(home)).joinToString("|")
    }
}
