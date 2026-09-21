package com.nfltotalslab.app.model

import com.nfltotalslab.app.data.GameRosterIntelligence
import com.nfltotalslab.app.data.Prediction
import com.nfltotalslab.app.data.ShadowPrediction
import kotlin.math.max

object RosterAdjustmentShadow {
    fun fromCore(core:Prediction,intel:GameRosterIntelligence):ShadowPrediction{
        val reliability=intel.reliability.coerceIn(0.0,1.0)
        val projection=core.projection+intel.totalAdjustment

        val baseOver=if(core.pick=="OVER")core.probability else 1.0-core.probability
        val rosterShift=(intel.totalAdjustment*.025*reliability).coerceIn(-.15,.15)
        val pOver=(baseOver+rosterShift).coerceIn(.05,.95)
        val pUnder=1.0-pOver
        val pick=if(pOver>=pUnder)"OVER" else "UNDER"
        val prob=max(pOver,pUnder)

        val key="${core.inputKey}|ROSTER_V1|R=${fmt(reliability)}|D=${fmt(intel.totalAdjustment)}|${intel.fingerprint}"

        return ShadowPrediction(
            id=core.id*100L+92L,
            gameId=core.gameId,
            season=core.season,
            week=core.week,
            awayTeam=core.awayTeam,
            homeTeam=core.homeTeam,
            line=core.line,
            modelName="Roster Adjustment V1",
            pick=pick,
            probability=prob,
            projection=projection,
            inputKey=key,
            createdAt=System.currentTimeMillis()
        )
    }

    private fun fmt(v:Double)=java.lang.String.format(java.util.Locale.US,"%.4f",v)
}
