package com.nfltotalslab.app.model

import com.nfltotalslab.app.data.GameRecord
import com.nfltotalslab.app.data.MatchupFeature
import com.nfltotalslab.app.data.MatchupScoreCensus
import com.nfltotalslab.app.data.ShadowPrediction
import com.nfltotalslab.app.data.TeamMetrics
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

object MatchupScoreLab {
    const val MODEL_NAME="Matchup Shadow V1"
    private const val CORE_GENERATION="0.9.0-integrity"

    private data class TeamProjection(
        val points:Double,
        val featureAdjustment:Double,
        val learningAdjustment:Double,
        val learningN:Int,
        val coverage:Int,
        val coverageTotal:Int,
        val reliability:Double
    )

    private data class LearnedBias(val adjustment:Double,val n:Int)

    fun build(
        game:GameRecord,
        metrics:List<TeamMetrics>,
        features:List<MatchupFeature>,
        schedule:List<GameRecord>,
        history:List<MatchupScoreCensus>
    ):MatchupScoreCensus?{
        val line=game.totalLine ?: return null
        val byTeam=metrics.associateBy{it.team}
        val awayMetric=byTeam[game.awayTeam] ?: return null
        val homeMetric=byTeam[game.homeTeam] ?: return null

        val away=projectTeam(
            team=game.awayTeam,
            opponent=game.homeTeam,
            isHome=false,
            week=game.week,
            teamMetric=awayMetric,
            opponentMetric=homeMetric,
            metrics=metrics,
            features=features,
            schedule=schedule,
            history=history
        )

        val home=projectTeam(
            team=game.homeTeam,
            opponent=game.awayTeam,
            isHome=true,
            week=game.week,
            teamMetric=homeMetric,
            opponentMetric=awayMetric,
            metrics=metrics,
            features=features,
            schedule=schedule,
            history=history
        )

        val total=(away.points+home.points).coerceIn(20.0,80.0)
        val edge=total-line
        val pick=if(edge>=0.0)"OVER" else "UNDER"

        // The O/U market is consulted only after the two team scores exist.
        val rawPickProbability=1.0/(1.0+exp(-abs(edge)/7.5))
        val reliability=((away.reliability+home.reliability)/2.0).coerceIn(.15,.95)
        val probability=(.50+(rawPickProbability-.50)*reliability).coerceIn(.50,.80)

        val coverageCount=away.coverage+home.coverage
        val coverageTotal=away.coverageTotal+home.coverageTotal
        val coverageRatio=if(coverageTotal==0)0.0 else coverageCount.toDouble()/coverageTotal.toDouble()

        val status=when{
            coverageRatio<.60 || reliability<.35 -> "NO CONCLUYENTE"
            abs(edge)<1.0 -> "OBSERVAR"
            else -> "LEAN"
        }

        val fingerprint=featureFingerprint(game.awayTeam,game.homeTeam,features)

        return MatchupScoreCensus(
            gameId=game.gameId,
            season=game.season,
            week=game.week,
            awayTeam=game.awayTeam,
            homeTeam=game.homeTeam,
            awayProjection=away.points,
            homeProjection=home.points,
            totalProjection=total,
            marketLine=line,
            pick=pick,
            probability=probability,
            reliability=reliability,
            coverageCount=coverageCount,
            coverageTotal=coverageTotal,
            status=status,
            awayFeatureAdjustment=away.featureAdjustment,
            homeFeatureAdjustment=home.featureAdjustment,
            awayLearningAdjustment=away.learningAdjustment,
            homeLearningAdjustment=home.learningAdjustment,
            awayLearningN=away.learningN,
            homeLearningN=home.learningN,
            inputKey="$CORE_GENERATION|MATCHUP_V1|${game.gameId}|$fingerprint"
        )
    }

    fun toShadow(x:MatchupScoreCensus):ShadowPrediction =
        ShadowPrediction(
            id=stableId("${x.gameId}|$MODEL_NAME"),
            gameId=x.gameId,
            season=x.season,
            week=x.week,
            awayTeam=x.awayTeam,
            homeTeam=x.homeTeam,
            line=x.marketLine,
            modelName=MODEL_NAME,
            pick=x.pick,
            probability=x.probability,
            projection=x.totalProjection,
            inputKey=x.inputKey,
            createdAt=x.createdAt,
            finalTotal=x.finalTotal,
            result=x.result
        )

    private fun projectTeam(
        team:String,
        opponent:String,
        isHome:Boolean,
        week:Int,
        teamMetric:TeamMetrics,
        opponentMetric:TeamMetrics,
        metrics:List<TeamMetrics>,
        features:List<MatchupFeature>,
        schedule:List<GameRecord>,
        history:List<MatchupScoreCensus>
    ):TeamProjection{
        val usableMetrics=metrics.filter{it.games>0}

        val leaguePpg=usableMetrics
            .map{it.pointsFor.toDouble()/it.games.toDouble()}
            .takeIf{it.isNotEmpty()}
            ?.average()
            ?: 22.5

        val leagueDrives=usableMetrics
            .map{it.drivesPerGame}
            .filter{it>0.0}
            .takeIf{it.isNotEmpty()}
            ?.average()
            ?: 10.5

        val leagueOffEpa=usableMetrics.map{it.offEpaPerPlay}
            .takeIf{it.isNotEmpty()}?.average() ?: 0.0
        val leagueDefEpa=usableMetrics.map{it.defEpaAllowedPerPlay}
            .takeIf{it.isNotEmpty()}?.average() ?: 0.0
        val leagueSuccess=usableMetrics.map{it.successRate}
            .takeIf{it.isNotEmpty()}?.average() ?: .42
        val leagueExplosive=usableMetrics.map{it.explosiveRate}
            .takeIf{it.isNotEmpty()}?.average() ?: .10

        val offPpg=if(teamMetric.games>0)
            teamMetric.pointsFor.toDouble()/teamMetric.games.toDouble()
        else leaguePpg

        val opponentAllowed=pointsAllowedPerGame(
            opponent=opponent,
            beforeWeek=week,
            schedule=schedule
        ) ?: leaguePpg

        // Independent scoring baseline. No market total and no Core projection.
        val base=.57*offPpg+.43*opponentAllowed

        val teamDrives=teamMetric.drivesPerGame.takeIf{it>0.0} ?: leagueDrives
        val oppDrives=opponentMetric.drivesPerGame.takeIf{it>0.0} ?: leagueDrives
        val pace=((teamDrives+oppDrives)/2.0/leagueDrives).coerceIn(.92,1.08)

        val epaAdjustment=
            8.0*(teamMetric.offEpaPerPlay-leagueOffEpa) +
            7.0*(opponentMetric.defEpaAllowedPerPlay-leagueDefEpa)

        val successAdjustment=5.0*(teamMetric.successRate-leagueSuccess)
        val explosiveAdjustment=7.0*(teamMetric.explosiveRate-leagueExplosive)

        val feature=featureAdjustment(
            offenseTeam=team,
            defenseTeam=opponent,
            all=features
        )

        val learned=learnedTeamBias(
            team=team,
            beforeWeek=week,
            history=history
        )

        // Symmetric allocation: changes who scores the points, not the total.
        val venueAdjustment=if(isHome).75 else -.75

        val points=(
            base*pace +
            epaAdjustment +
            successAdjustment +
            explosiveAdjustment +
            feature.first +
            learned.adjustment +
            venueAdjustment
        ).coerceIn(6.0,42.0)

        val scoreSampleReliability=(
            (teamMetric.games+opponentMetric.games).toDouble()/12.0
        ).coerceIn(.0,1.0)

        val reliability=(
            .55*scoreSampleReliability +
            .45*feature.second
        ).coerceIn(.15,.95)

        return TeamProjection(
            points=points,
            featureAdjustment=feature.first,
            learningAdjustment=learned.adjustment,
            learningN=learned.n,
            coverage=feature.third,
            coverageTotal=5,
            reliability=reliability
        )
    }

    /**
     * Returns Triple(points adjustment, feature reliability, available pairs).
     * Cumulative rush yards remains stored but is excluded from V1 scoring.
     */
    private fun featureAdjustment(
        offenseTeam:String,
        defenseTeam:String,
        all:List<MatchupFeature>
    ):Triple<Double,Double,Int>{
        val off=all.filter{it.team==offenseTeam}.associateBy{it.feature}
        val def=all.filter{it.team==defenseTeam}.associateBy{it.feature}

        var adjustment=0.0
        var reliabilitySum=0.0
        var coverage=0

        fun addPair(
            offName:String,
            defName:String,
            weight:Double,
            offSign:Double=1.0,
            defSign:Double=1.0
        ){
            val a=off[offName] ?: return
            val b=def[defName] ?: return
            val za=zScore(a.value,offName,all)*offSign
            val zb=zScore(b.value,defName,all)*defSign
            val rel=((a.reliability+b.reliability)/2.0).coerceIn(.0,1.0)
            adjustment+=((za+zb)/2.0)*weight*rel
            reliabilitySum+=rel
            coverage++
        }

        addPair("rush_right_ypc","rush_right_ypc_allowed",.65)
        addPair(
            "pressure_allowed_rate",
            "pressure_rate",
            .85,
            offSign=-1.0,
            defSign=-1.0
        )
        addPair("rush_rate","ypc_allowed",.35)
        addPair("first_down_ypc","first_down_ypc_allowed",.55)
        addPair("rb_yac_per_carry","rb_yac_allowed",.45)

        val featureReliability=if(coverage==0)0.0
        else (reliabilitySum/coverage.toDouble())*(coverage.toDouble()/5.0)

        return Triple(
            adjustment.coerceIn(-2.5,2.5),
            featureReliability.coerceIn(.0,1.0),
            coverage
        )
    }

    private fun zScore(value:Double,feature:String,all:List<MatchupFeature>):Double{
        val values=all.filter{it.feature==feature}.map{it.value}
        if(values.size<6)return 0.0
        val mean=values.average()
        val variance=values.map{(it-mean).pow(2)}.average()
        val sd=sqrt(variance)
        if(sd<1e-9)return 0.0
        return ((value-mean)/sd).coerceIn(-2.5,2.5)
    }

    private fun pointsAllowedPerGame(
        opponent:String,
        beforeWeek:Int,
        schedule:List<GameRecord>
    ):Double?{
        var games=0
        var allowed=0
        schedule.filter{
            it.finished &&
            it.gameType=="REG" &&
            it.week<beforeWeek
        }.forEach{g->
            when(opponent){
                g.awayTeam -> {
                    games++
                    allowed+=g.homeScore ?: 0
                }
                g.homeTeam -> {
                    games++
                    allowed+=g.awayScore ?: 0
                }
            }
        }
        return if(games==0)null else allowed.toDouble()/games.toDouble()
    }

    private fun learnedTeamBias(
        team:String,
        beforeWeek:Int,
        history:List<MatchupScoreCensus>
    ):LearnedBias{
        val residuals=history
            .filter{
                it.week<beforeWeek &&
                it.finalAway!=null &&
                it.finalHome!=null
            }
            .mapNotNull{x->
                when(team){
                    x.awayTeam -> x.finalAway!!.toDouble()-x.awayProjection
                    x.homeTeam -> x.finalHome!!.toDouble()-x.homeProjection
                    else -> null
                }
            }

        if(residuals.isEmpty())return LearnedBias(0.0,0)

        val n=residuals.size
        val mean=residuals.average()
        val shrink=n.toDouble()/(n.toDouble()+6.0)

        return LearnedBias(
            adjustment=(mean*shrink).coerceIn(-2.5,2.5),
            n=n
        )
    }

    private fun featureFingerprint(
        away:String,
        home:String,
        features:List<MatchupFeature>
    ):String{
        val raw=features
            .filter{it.team==away || it.team==home}
            .sortedWith(compareBy<MatchupFeature>{it.team}.thenBy{it.feature})
            .joinToString(";"){
                "${it.team}:${it.feature}:${"%.5f".format(java.util.Locale.US,it.value)}:${it.sampleN}"
            }
        return stableId(raw).toString(16)
    }

    private fun stableId(raw:String):Long{
        var h=1125899906842597L
        raw.forEach{ch->h=31L*h+ch.code.toLong()}
        return h and Long.MAX_VALUE
    }
}
