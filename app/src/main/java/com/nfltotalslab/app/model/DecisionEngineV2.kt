package com.nfltotalslab.app.model

import com.nfltotalslab.app.data.GameRecord
import com.nfltotalslab.app.data.MatchupScoreCensus
import com.nfltotalslab.app.data.Prediction
import com.nfltotalslab.app.data.ShadowPrediction
import com.nfltotalslab.app.data.TeamMetrics
import kotlin.math.max

object DecisionEngineV2 {
    fun fromCore(
        game:GameRecord,
        core:Prediction,
        away:TeamMetrics?,
        home:TeamMetrics?,
        matchup:MatchupScoreCensus?
    ):List<ShadowPrediction>{
        val markov=core.engines.firstOrNull{it.name=="Markov Drive"} ?: return emptyList()
        val negbin=core.engines.firstOrNull{it.name=="Negative Binomial"} ?: return emptyList()
        val bayes=core.engines.firstOrNull{it.name=="Bayesian"} ?: return emptyList()
        val possession=core.engines.firstOrNull{it.name=="Possession Monte Carlo V2"} ?: return emptyList()

        val distOver=(negbin.pOver+bayes.pOver)/2.0
        val distUnder=(negbin.pUnder+bayes.pUnder)/2.0
        val distProjection=(negbin.projection+bayes.projection)/2.0

        val sides=listOf(
            if(markov.pOver>=markov.pUnder)"OVER" else "UNDER",
            if(distOver>=distUnder)"OVER" else "UNDER",
            if(possession.pOver>=possession.pUnder)"OVER" else "UNDER"
        )
        val agreement=sides.count{it==core.pick}
        val projections=listOf(markov.projection,distProjection,possession.projection)
        val dispersion=projections.maxOrNull()!!-projections.minOrNull()!!

        val turnoverSum=(away?.turnoverRate ?: .018)+(home?.turnoverRate ?: .018)
        val tdDriveSum=(away?.tdPerDrive ?: .22)+(home?.tdPerDrive ?: .22)

        val consensusTrap=agreement==3 && dispersion<4.0
        val turnoverChaos=core.pick=="UNDER" && turnoverSum>=.040
        val scoringForm=core.pick=="OVER" && tdDriveSum>=.56

        val matchupUsable=matchup!=null &&
            matchup.status!="NO CONCLUYENTE" &&
            matchup.coverageTotal>0 &&
            matchup.coverageCount.toDouble()/matchup.coverageTotal.toDouble()>=.60

        val matchupConfirms=matchupUsable && matchup!!.pick==core.pick
        val out=mutableListOf<ShadowPrediction>()

        if(
            !consensusTrap &&
            !turnoverChaos &&
            matchupConfirms &&
            (agreement==2 || scoringForm)
        ){
            val probability=max(core.probability,matchup!!.probability).coerceIn(.55,.65)
            out+=shadow(
                core=core,
                name="Decision Engine V2",
                pick=core.pick,
                probability=probability,
                projection=(core.projection+matchup.totalProjection)/2.0,
                suffix="QUALIFIED|A=$agreement|D=${fmt(dispersion)}|TO=${fmt(turnoverSum)}|TD=${fmt(tdDriveSum)}|M=${matchup.pick}"
            )
        }

        if(consensusTrap){
            out+=shadow(
                core=core,
                name="Pattern Shadow · Consensus Trap",
                pick=if(core.pick=="OVER")"UNDER" else "OVER",
                probability=.55,
                projection=core.projection,
                suffix="CONSENSUS_TRAP|A=$agreement|D=${fmt(dispersion)}"
            )
        }

        if(turnoverChaos){
            out+=shadow(
                core=core,
                name="Pattern Shadow · Turnover Chaos",
                pick="OVER",
                probability=.55,
                projection=core.projection,
                suffix="TURNOVER_CHAOS|TO=${fmt(turnoverSum)}"
            )
        }

        return out
    }

    private fun shadow(
        core:Prediction,
        name:String,
        pick:String,
        probability:Double,
        projection:Double,
        suffix:String
    ):ShadowPrediction = ShadowPrediction(
        id=stableId("${core.gameId}|$name|${core.inputKey}"),
        gameId=core.gameId,
        season=core.season,
        week=core.week,
        awayTeam=core.awayTeam,
        homeTeam=core.homeTeam,
        line=core.line,
        modelName=name,
        pick=pick,
        probability=probability,
        projection=projection,
        inputKey="${core.inputKey}|DECISION_V2|$suffix",
        createdAt=System.currentTimeMillis()
    )

    private fun stableId(raw:String):Long{
        var h=1125899906842597L
        raw.forEach{ch->h=31L*h+ch.code.toLong()}
        return h and Long.MAX_VALUE
    }

    private fun fmt(x:Double):String =
        java.lang.String.format(java.util.Locale.US,"%.5f",x)
}
