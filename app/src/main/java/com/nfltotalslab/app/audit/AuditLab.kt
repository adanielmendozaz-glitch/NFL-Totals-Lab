package com.nfltotalslab.app.audit

import com.nfltotalslab.app.data.GameRecord
import com.nfltotalslab.app.data.Prediction
import com.nfltotalslab.app.data.ShadowPrediction
import com.nfltotalslab.app.data.MatchupScoreCensus
import com.nfltotalslab.app.data.MatchupScoreAudit
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

data class AuditStat(
    val label:String,
    val n:Int,
    val wins:Int,
    val losses:Int,
    val pushes:Int,
    val hitRate:Double?,
    val roi:Double?,
    val brier:Double?,
    val avgProbability:Double?
)

data class AuditSnapshot(
    val odds:Double,
    val currentVersion:String,
    val core:AuditStat,
    val legacyCore:AuditStat,
    val ece:Double?,
    val modelComparison:List<AuditStat>,
    val byProbability:List<AuditStat>,
    val byMarket:List<AuditStat>,
    val byClassification:List<AuditStat>,
    val byTeam:List<AuditStat>,
    val invalidPostKickoff:Int,
    val duplicateAutoGames:Int,
    val matchupScore:MatchupScoreAudit
)

private data class Obs(
    val gameId:String,
    val probability:Double,
    val result:String,
    val pick:String,
    val classification:String,
    val awayTeam:String,
    val homeTeam:String
)

object AuditLab {
    private val eastern=ZoneId.of("America/New_York")
    private val timeFormats=listOf(
        DateTimeFormatter.ofPattern("H:mm"),
        DateTimeFormatter.ofPattern("HH:mm")
    )

    fun build(
        predictions:List<Prediction>,
        shadows:List<ShadowPrediction>,
        games:List<GameRecord>,
        matchupScores:List<MatchupScoreCensus> = emptyList(),
        currentVersion:String="0.9.0-integrity",
        odds:Double=1.91
    ):AuditSnapshot{
        val gameMap=games.associateBy{it.gameId}
        val autos=predictions.filter{it.analysisSource=="AUTO_CENSUS"}

        val duplicateAutoGames=autos.groupBy{it.gameId}.count{it.value.size>1}
        val invalidPostKickoff=autos.count{p->
            val kickoff=gameMap[p.gameId]?.let{kickoffEpochMs(it)}
            kickoff!=null && p.createdAt>kickoff
        }

        fun officialFor(versionMatch:(Prediction)->Boolean):List<Prediction> =
            autos.filter(versionMatch)
                .groupBy{it.gameId}
                .mapNotNull{(gameId,rows)->
                    val kickoff=gameMap[gameId]?.let{kickoffEpochMs(it)}
                    val valid=if(kickoff==null) rows else rows.filter{it.createdAt<=kickoff}
                    valid.maxByOrNull{it.createdAt}
                }

        val currentOfficial=officialFor{it.modelVersion==currentVersion}
        val legacyOfficial=officialFor{it.modelVersion!=currentVersion}

        val matchupSettled=matchupScores.filter{
            it.finalAway!=null && it.finalHome!=null
        }

        val matchupScoreAudit=if(matchupSettled.isEmpty()){
            MatchupScoreAudit(
                n=0,
                totalMae=null,
                teamMae=null,
                awayMae=null,
                homeMae=null,
                totalBias=null,
                disagreementN=0,
                coreWinsWhenDisagree=0,
                matchupWinsWhenDisagree=0
            )
        }else{
            val totalErrors=matchupSettled.map{
                (it.finalAway!!+it.finalHome!!).toDouble()-it.totalProjection
            }
            val awayErrors=matchupSettled.map{
                it.finalAway!!.toDouble()-it.awayProjection
            }
            val homeErrors=matchupSettled.map{
                it.finalHome!!.toDouble()-it.homeProjection
            }

            val coreByGame=currentOfficial.associateBy{it.gameId}
            val disagreements=matchupSettled.filter{x->
                coreByGame[x.gameId]?.pick?.let{it!=x.pick}==true
            }

            MatchupScoreAudit(
                n=matchupSettled.size,
                totalMae=totalErrors.map{kotlin.math.abs(it)}.average(),
                teamMae=(awayErrors.map{kotlin.math.abs(it)}+homeErrors.map{kotlin.math.abs(it)}).average(),
                awayMae=awayErrors.map{kotlin.math.abs(it)}.average(),
                homeMae=homeErrors.map{kotlin.math.abs(it)}.average(),
                totalBias=totalErrors.average(),
                disagreementN=disagreements.size,
                coreWinsWhenDisagree=disagreements.count{x->
                    coreByGame[x.gameId]?.result=="WIN"
                },
                matchupWinsWhenDisagree=disagreements.count{it.result=="WIN"}
            )
        }

        fun toObs(rows:List<Prediction>):List<Obs> = rows
            .filter{it.result in setOf("WIN","LOSS","PUSH")}
            .map{
                Obs(
                    gameId=it.gameId,
                    probability=it.probability,
                    result=it.result ?: "PUSH",
                    pick=it.pick,
                    classification=canonicalClass(it.classification),
                    awayTeam=it.awayTeam,
                    homeTeam=it.homeTeam
                )
            }

        val coreRows=toObs(currentOfficial)
        val legacyRows=toObs(legacyOfficial)
        val core=stats("CORE CURRENT",coreRows,odds)
        val legacyCore=stats("CORE LEGACY",legacyRows,odds)

        val probabilitySpecs=listOf(
            Triple("50–54.9%",.50,.55),
            Triple("55–59.9%",.55,.60),
            Triple("60–64.9%",.60,.65),
            Triple("65–69.9%",.65,.70),
            Triple("70%+",.70,1.01)
        )
        val byProbability=probabilitySpecs.map{(label,lo,hi)->
            stats(label,coreRows.filter{it.probability>=lo && it.probability<hi},odds)
        }

        val decisionN=byProbability.sumOf{it.wins+it.losses}
        val ece=if(decisionN==0)null else byProbability.sumOf{b->
            val d=b.wins+b.losses
            if(d==0 || b.hitRate==null || b.avgProbability==null)0.0
            else d.toDouble()/decisionN * abs(b.hitRate-b.avgProbability)
        }

        val byMarket=listOf("OVER","UNDER").map{side->
            stats(side,coreRows.filter{it.pick==side},odds)
        }
        val byClassification=listOf("PASS","LEAN","JUGABLE").map{c->
            stats(c,coreRows.filter{it.classification==c},odds)
        }

        val teams=coreRows.flatMap{listOf(it.awayTeam,it.homeTeam)}.distinct().sorted()
        val byTeam=teams.map{team->
            stats(team,coreRows.filter{it.awayTeam==team || it.homeTeam==team},odds)
        }.sortedWith(compareByDescending<AuditStat>{it.n}.thenByDescending{it.roi ?: Double.NEGATIVE_INFINITY})

        val shadowLatest=shadows
            .filter{
                it.result in setOf("WIN","LOSS","PUSH") &&
                it.inputKey.startsWith("$currentVersion|")
            }
            .groupBy{"${it.gameId}|${it.modelName}"}
            .mapNotNull{(_,rows)->rows.maxByOrNull{it.createdAt}}

        val shadowModels=shadowLatest.groupBy{it.modelName}.map{(name,rows)->
            stats(name,rows.map{
                Obs(it.gameId,it.probability,it.result ?: "PUSH",it.pick,"SHADOW",it.awayTeam,it.homeTeam)
            },odds)
        }

        val modelComparison=(listOf(core)+shadowModels).sortedWith(
            compareBy<AuditStat>{it.brier ?: Double.POSITIVE_INFINITY}
                .thenByDescending{it.roi ?: Double.NEGATIVE_INFINITY}
        )

        return AuditSnapshot(
            odds=odds,
            currentVersion=currentVersion,
            core=core,
            legacyCore=legacyCore,
            ece=ece,
            modelComparison=modelComparison,
            byProbability=byProbability,
            byMarket=byMarket,
            byClassification=byClassification,
            byTeam=byTeam,
            invalidPostKickoff=invalidPostKickoff,
            duplicateAutoGames=duplicateAutoGames,
            matchupScore=matchupScoreAudit
        )
    }

    private fun kickoffEpochMs(game:GameRecord):Long?{
        val day=runCatching{LocalDate.parse(game.gameDay)}.getOrNull() ?: return null
        var time:LocalTime?=null
        for(f in timeFormats){
            time=runCatching{LocalTime.parse(game.gameTime,f)}.getOrNull()
            if(time!=null)break
        }
        return time?.let{day.atTime(it).atZone(eastern).toInstant().toEpochMilli()}
    }

    private fun stats(label:String,rows:List<Obs>,odds:Double):AuditStat{
        val wins=rows.count{it.result=="WIN"}
        val losses=rows.count{it.result=="LOSS"}
        val pushes=rows.count{it.result=="PUSH"}
        val decisions=wins+losses
        val n=rows.size
        val hit=if(decisions==0)null else wins.toDouble()/decisions
        val roi=if(n==0)null else (wins*(odds-1.0)-losses)/n
        val decisionRows=rows.filter{it.result=="WIN"||it.result=="LOSS"}
        val brier=if(decisionRows.isEmpty())null else decisionRows.map{
            val y=if(it.result=="WIN")1.0 else 0.0
            val e=it.probability-y
            e*e
        }.average()
        val avgP=if(decisionRows.isEmpty())null else decisionRows.map{it.probability}.average()
        return AuditStat(label,n,wins,losses,pushes,hit,roi,brier,avgP)
    }

    private fun canonicalClass(raw:String):String=when{
        raw.startsWith("JUGABLE",true)->"JUGABLE"
        raw.startsWith("LEAN",true)->"LEAN"
        else->"PASS"
    }
}
