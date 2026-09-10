package com.nfltotalslab.app.audit

import com.nfltotalslab.app.data.Prediction
import com.nfltotalslab.app.data.ShadowPrediction
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
    val core:AuditStat,
    val ece:Double?,
    val modelComparison:List<AuditStat>,
    val byProbability:List<AuditStat>,
    val byMarket:List<AuditStat>,
    val byClassification:List<AuditStat>,
    val byTeam:List<AuditStat>
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
    fun build(
        predictions:List<Prediction>,
        shadows:List<ShadowPrediction>,
        odds:Double=1.91
    ):AuditSnapshot{
        val coreRows=predictions
            .filter{it.analysisSource=="AUTO_CENSUS" && it.result in setOf("WIN","LOSS","PUSH")}
            .groupBy{it.gameId}
            .mapNotNull{(_,rows)->rows.maxByOrNull{it.createdAt}}
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

        val core=stats("CORE ENSEMBLE",coreRows,odds)

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
        }.sortedWith(
            compareByDescending<AuditStat>{it.n}
                .thenByDescending{it.roi ?: Double.NEGATIVE_INFINITY}
        )

        val shadowLatest=shadows
            .filter{it.result in setOf("WIN","LOSS","PUSH")}
            .groupBy{"${it.gameId}|${it.modelName}"}
            .mapNotNull{(_,rows)->rows.maxByOrNull{it.createdAt}}

        val shadowModels=shadowLatest.groupBy{it.modelName}.map{(name,rows)->
            val obs=rows.map{
                Obs(
                    gameId=it.gameId,
                    probability=it.probability,
                    result=it.result ?: "PUSH",
                    pick=it.pick,
                    classification="SHADOW",
                    awayTeam=it.awayTeam,
                    homeTeam=it.homeTeam
                )
            }
            stats(name,obs,odds)
        }

        val modelComparison=(listOf(core)+shadowModels).sortedWith(
            compareBy<AuditStat>{it.brier ?: Double.POSITIVE_INFINITY}
                .thenByDescending{it.roi ?: Double.NEGATIVE_INFINITY}
        )

        return AuditSnapshot(
            odds=odds,
            core=core,
            ece=ece,
            modelComparison=modelComparison,
            byProbability=byProbability,
            byMarket=byMarket,
            byClassification=byClassification,
            byTeam=byTeam
        )
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
