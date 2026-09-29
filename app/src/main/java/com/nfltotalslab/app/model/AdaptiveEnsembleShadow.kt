package com.nfltotalslab.app.model

import com.nfltotalslab.app.data.Prediction
import com.nfltotalslab.app.data.ShadowPrediction
import kotlin.math.max

object AdaptiveEnsembleShadow {
    private val baseWeights=linkedMapOf(
        "Markov" to (1.0/3.0),
        "Distribution" to (1.0/3.0),
        "DriveMC" to (1.0/3.0)
    )

    fun fromCore(
        core:Prediction,
        history:List<ShadowPrediction>
    ):ShadowPrediction?{
        val current=core.engines.associateBy{it.name}
        val markov=current["Markov Drive"] ?: return null
        val negbin=current["Negative Binomial"] ?: return null
        val bayes=current["Bayesian"] ?: return null
        val drive=current["Drive Monte Carlo"] ?: return null

        data class FamilySlice(
            val projection:Double,
            val pOver:Double,
            val pUnder:Double
        )

        val slices=linkedMapOf(
            "Markov" to FamilySlice(markov.projection,markov.pOver,markov.pUnder),
            "Distribution" to FamilySlice(
                (negbin.projection+bayes.projection)/2.0,
                (negbin.pOver+bayes.pOver)/2.0,
                (negbin.pUnder+bayes.pUnder)/2.0
            ),
            "DriveMC" to FamilySlice(drive.projection,drive.pOver,drive.pUnder)
        )

        val sameGeneration=history.filter{
            it.inputKey.startsWith("${core.modelVersion}|") &&
            (it.result=="WIN" || it.result=="LOSS")
        }

        fun brierFor(modelName:String):Pair<Int,Double?>{
            val rows=sameGeneration
                .filter{it.modelName==modelName}
                .groupBy{it.gameId}
                .mapNotNull{(_,xs)->xs.maxByOrNull{it.createdAt}}

            if(rows.isEmpty())return 0 to null

            val brier=rows.map{
                val y=if(it.result=="WIN")1.0 else 0.0
                val e=it.probability-y
                e*e
            }.average()

            return rows.size to brier
        }

        fun familyHistory(name:String):Pair<Int,Double?> = when(name){
            "Markov" -> brierFor("Markov Drive")
            "DriveMC" -> brierFor("Drive Monte Carlo")
            "Distribution" -> {
                val a=brierFor("Negative Binomial")
                val b=brierFor("Bayesian")
                val values=listOfNotNull(a.second,b.second)
                maxOf(a.first,b.first) to
                    if(values.isEmpty())null else values.average()
            }
            else -> 0 to null
        }

        val adjusted=linkedMapOf<String,Double>()

        baseWeights.forEach{(name,base)->
            val (n,brier)=familyHistory(name)
            val reliability=n.toDouble()/(n+32.0)
            val skill=if(brier==null){
                1.0
            }else{
                (0.25/brier.coerceAtLeast(.08)).coerceIn(.65,1.35)
            }
            adjusted[name]=base*(1.0+reliability*(skill-1.0))
        }

        val z=adjusted.values.sum().takeIf{it>0.0} ?: return null
        val weights=adjusted.mapValues{it.value/z}

        var projection=0.0
        var pOver=0.0
        var pUnder=0.0

        weights.forEach{(name,w)->
            val f=slices.getValue(name)
            projection+=f.projection*w
            pOver+=f.pOver*w
            pUnder+=f.pUnder*w
        }

        val pick=if(pOver>=pUnder)"OVER" else "UNDER"
        val prob=max(pOver,pUnder)

        val fingerprint=weights.entries.joinToString(","){
            "${it.key}=${
                java.lang.String.format(
                    java.util.Locale.US,"%.4f",it.value
                )
            }"
        }

        return ShadowPrediction(
            id=core.id*100L+91L,
            gameId=core.gameId,
            season=core.season,
            week=core.week,
            awayTeam=core.awayTeam,
            homeTeam=core.homeTeam,
            line=core.line,
            modelName="Adaptive Family Ensemble V2",
            pick=pick,
            probability=prob,
            projection=projection,
            inputKey="${core.inputKey}|ADAPT_FAMILY_V2|$fingerprint",
            createdAt=System.currentTimeMillis()
        )
    }
}
