package com.nfltotalslab.app.model

import com.nfltotalslab.app.data.GameRecord
import com.nfltotalslab.app.data.Prediction
import com.nfltotalslab.app.data.ShadowPrediction
import com.nfltotalslab.app.data.TeamMetrics
import kotlin.math.min

/**
 * Opponent Adjustment Shadow V1
 *
 * Observacional: nunca modifica el Core.
 * El peso crece de forma gradual según historial real de temporada:
 * 1 juego ~= 16.7%, 3 ~= 50%, 6+ = 100%.
 */
object OpponentAdjustmentShadow {

    private enum class Variant(
        val label:String,
        val idOffset:Long
    ){
        EPA("OppAdj EPA",61L),
        TEMPO("OppAdj Tempo",62L),
        COMPOSITE("OppAdj Composite",63L)
    }

    fun fromCore(
        game:GameRecord,
        core:Prediction,
        metrics:List<TeamMetrics>,
        schedule:List<GameRecord>,
        engine:TotalsEngine
    ):List<ShadowPrediction>{
        val metricMap=metrics.associateBy{it.team}
        val awayRaw=metricMap[game.awayTeam] ?: return emptyList()
        val homeRaw=metricMap[game.homeTeam] ?: return emptyList()

        val awayOpps=priorOpponents(game.awayTeam,schedule)
        val homeOpps=priorOpponents(game.homeTeam,schedule)

        // No fabricamos señal sin historial de ambos lados.
        if(awayOpps.isEmpty() || homeOpps.isEmpty())return emptyList()

        val league=leagueContext(metrics)
        val historyN=min(awayOpps.size,homeOpps.size)
        val reliability=(historyN/6.0).coerceIn(0.0,1.0)

        return Variant.values().map{variant->
            val away=adjust(
                raw=awayRaw,
                opponents=awayOpps,
                metricMap=metricMap,
                league=league,
                reliability=reliability,
                variant=variant
            )
            val home=adjust(
                raw=homeRaw,
                opponents=homeOpps,
                metricMap=metricMap,
                league=league,
                reliability=reliability,
                variant=variant
            )

            val p=engine.predict(game,away,home)
            val contextKey=contextKey(
                core=core,
                variant=variant,
                awayOpps=awayOpps,
                homeOpps=homeOpps,
                metricMap=metricMap,
                reliability=reliability
            )

            ShadowPrediction(
                id=core.id*100L+variant.idOffset,
                gameId=core.gameId,
                season=core.season,
                week=core.week,
                awayTeam=core.awayTeam,
                homeTeam=core.homeTeam,
                line=core.line,
                modelName=variant.label,
                pick=p.pick,
                probability=p.probability,
                projection=p.projection,
                inputKey=contextKey,
                createdAt=System.currentTimeMillis()
            )
        }
    }

    private data class LeagueContext(
        val offEpa:Double,
        val defEpa:Double,
        val drives:Double
    )

    private fun leagueContext(metrics:List<TeamMetrics>):LeagueContext{
        fun avg(values:List<Double>,fallback:Double)=
            if(values.isEmpty())fallback else values.average()

        return LeagueContext(
            offEpa=avg(metrics.map{it.offEpaPerPlay},0.0),
            defEpa=avg(metrics.map{it.defEpaAllowedPerPlay},0.0),
            drives=avg(metrics.map{it.drivesPerGame},10.6)
        )
    }

    private fun priorOpponents(
        team:String,
        schedule:List<GameRecord>
    ):List<String> =
        schedule.asSequence()
            .filter{it.gameType=="REG" && it.finished}
            .mapNotNull{g->
                when(team){
                    g.awayTeam -> g.homeTeam
                    g.homeTeam -> g.awayTeam
                    else -> null
                }
            }
            .toList()

    private fun adjust(
        raw:TeamMetrics,
        opponents:List<String>,
        metricMap:Map<String,TeamMetrics>,
        league:LeagueContext,
        reliability:Double,
        variant:Variant
    ):TeamMetrics{
        val oppMetrics=opponents.mapNotNull{metricMap[it]}
        if(oppMetrics.isEmpty())return raw

        val avgOppDef=oppMetrics.map{it.defEpaAllowedPerPlay}.average()
        val avgOppOff=oppMetrics.map{it.offEpaPerPlay}.average()
        val avgOppDrives=oppMetrics.map{it.drivesPerGame}.average()

        // >0 = ofensiva enfrentó defensas más permisivas que la media.
        val offenseEase=avgOppDef-league.defEpa

        // >0 = defensa enfrentó ofensivas más fuertes que la media.
        val offenseStrengthFaced=avgOppOff-league.offEpa

        // >0 = rivales tuvieron más drives que la media NFL.
        val paceInflation=avgOppDrives-league.drives

        return when(variant){
            Variant.EPA -> raw.copy(
                offEpaPerPlay=(
                    raw.offEpaPerPlay-offenseEase*reliability
                ).coerceIn(-0.45,0.45),
                defEpaAllowedPerPlay=(
                    raw.defEpaAllowedPerPlay-offenseStrengthFaced*reliability
                ).coerceIn(-0.45,0.45)
            )

            Variant.TEMPO -> raw.copy(
                drivesPerGame=(
                    raw.drivesPerGame-paceInflation*0.65*reliability
                ).coerceIn(8.0,14.0)
            )

            Variant.COMPOSITE -> raw.copy(
                offEpaPerPlay=(
                    raw.offEpaPerPlay-offenseEase*reliability
                ).coerceIn(-0.45,0.45),

                defEpaAllowedPerPlay=(
                    raw.defEpaAllowedPerPlay-offenseStrengthFaced*reliability
                ).coerceIn(-0.45,0.45),

                drivesPerGame=(
                    raw.drivesPerGame-paceInflation*0.65*reliability
                ).coerceIn(8.0,14.0),

                successRate=(
                    raw.successRate-offenseEase*0.16*reliability
                ).coerceIn(.25,.65),

                explosiveRate=(
                    raw.explosiveRate-offenseEase*0.06*reliability
                ).coerceIn(.03,.25),

                tdPerDrive=(
                    raw.tdPerDrive-offenseEase*0.22*reliability
                ).coerceIn(.07,.45),

                fgPerDrive=(
                    raw.fgPerDrive-offenseEase*0.05*reliability
                ).coerceIn(.04,.30)
            )
        }
    }

    /**
     * Si cambia la fuerza de los rivales ya enfrentados, el contexto cambia.
     * El mismo Shadow puede refrescarse antes del kickoff y queda congelado
     * después por el gating existente del repositorio.
     */
    private fun contextKey(
        core:Prediction,
        variant:Variant,
        awayOpps:List<String>,
        homeOpps:List<String>,
        metricMap:Map<String,TeamMetrics>,
        reliability:Double
    ):String{
        fun opponentFingerprint(opps:List<String>):String =
            opps.sorted().joinToString(";"){team->
                val m=metricMap[team]
                if(m==null)"$team:NA"
                else "$team:${m.offEpaPerPlay.formatKey()}:${m.defEpaAllowedPerPlay.formatKey()}:${m.drivesPerGame.formatKey()}"
            }

        return listOf(
            core.inputKey,
            "OPPADJ_V1",
            variant.name,
            "R=${reliability.formatKey()}",
            "A=${opponentFingerprint(awayOpps)}",
            "H=${opponentFingerprint(homeOpps)}"
        ).joinToString("|")
    }

    private fun Double.formatKey():String =
        java.lang.String.format(java.util.Locale.US,"%.5f",this)
}
