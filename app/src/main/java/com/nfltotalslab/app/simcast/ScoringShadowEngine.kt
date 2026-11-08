package com.nfltotalslab.app.simcast

import com.nfltotalslab.app.data.GameRecord
import com.nfltotalslab.app.data.TeamMetrics
import kotlin.math.*
import kotlin.random.Random

/**
 * PRE-GAME SCORE MODEL (SHADOW): independently predicts the *score*, not a rendered play sequence.
 * All inputs are limited to prior-week observations by the caller (NflRepository/DataCenter).
 * It must not be presented as calibrated betting probabilities until walk-forward validation passes.
 *
 * Model: empirical scored/allowed PPG with 5-game shrinkage (PregameSimcastCalibration),
 * prior-week offensive TD/FG per drive when available, a shared possession pace random effect,
 * an uncertain lineup-dependent QB efficiency adjustment and a modest skill-player-out penalty.
 * NO adjustment is made for defense/OL injuries without verified positional impact data.
 */
data class ScoringTeamParameters(
    val team:String,
    val expectedPpg:Double,
    val drivesPerGame:Double,
    val pTouchdown:Double,
    val pFieldGoal:Double,
    val qb:String,
    val qbAttemptsEvidence:Double,
    val qbMultiplier:Double,
    val offenseOutShare:Double,
    val skillInjuryMultiplier:Double,
    val epaMultiplier:Double,
    val rateSource:String,
    val lineupVerified:Boolean,
    val warning:String
)

data class ScoringShadowResult(
    val n:Int,
    val meanAway:Double,
    val meanHome:Double,
    val meanTotal:Double,
    val medianTotal:Int,
    val sdTotal:Double,
    val p10Total:Int,
    val p90Total:Int,
    val pOver:Double?,
    val pUnder:Double?,
    val pPush:Double?,
    val awayWinRate:Double,
    val homeWinRate:Double,
    val tieRate:Double,
    val away:ScoringTeamParameters,
    val home:ScoringTeamParameters,
    val topScores:List<ScoreFrequency>,
    val method:String="PRIOR_WEEK_DRIVE_SCORE_BAYES_V163_SHADOW",
    val warning:String="MODELO EXPERIMENTAL: no validado fuera de muestra; NO sustituye al Core ni al Simcast de jugadas"
)

class ScoringShadowEngine {
    private fun normalize(raw:String):String = canonicalNflPlayerName(raw)

    fun parameters(
        team:String, game:GameRecord, teamUsage:TeamUsage, teamMetrics:TeamMetrics?,
        opposingMetrics:TeamMetrics?, teamLineup:TeamLineup, calibration:SimcastCalibration
    ):ScoringTeamParameters {
        val starterName=teamLineup.startingQb.ifBlank {
            teamUsage.players.filter{it.position=="QB"}.maxByOrNull{it.passAttempts}?.name ?: "QB sin confirmar"
        }
        val outs=teamLineup.outNames.map(::normalize).toSet()
        val inPlayers=teamUsage.players.filterNot{normalize(it.name) in outs}
        val qb=inPlayers.firstOrNull{it.position=="QB" && normalize(it.name)==normalize(starterName)}
        val allQb=teamUsage.players.filter{it.position=="QB"}
        val allAttempts=allQb.sumOf{it.passAttempts}.coerceAtLeast(0.0)
        val allPassing=allQb.sumOf{it.passingYards}.coerceAtLeast(0.0)
        val starterAttempts=qb?.passAttempts?.coerceAtLeast(0.0) ?: 0.0
        // Bayesian smoothing: never treat a new QB as zero EPA or zero completion probability.
        val priorYpa=7.0
        val teamYpa=(allPassing+priorYpa*90.0)/(allAttempts+90.0)
        val starterYpa=((qb?.passingYards ?: 0.0)+priorYpa*90.0)/(starterAttempts+90.0)
        val quarterbackFactor=(1.0 + (starterYpa/teamYpa-1.0)*.55).coerceIn(.85,1.13)

        // Player Weekly contains only a small recent window. OUT skill-player volume is
        // a proxy for injury severity; redistributed snaps are explicitly discounted.
        val skill=teamUsage.players.filter{it.position in setOf("RB","WR","TE")}
        val totalTargets=skill.sumOf{it.targets}.coerceAtLeast(0.01)
        val totalCarries=skill.sumOf{it.carries}.coerceAtLeast(0.01)
        val missing=skill.filter{normalize(it.name) in outs}
        val targetShare=(missing.sumOf{it.targets}/totalTargets).coerceIn(0.0,1.0)
        val carryShare=(missing.sumOf{it.carries}/totalCarries).coerceIn(0.0,1.0)
        val missedShare=(.65*targetShare+.35*carryShare).coerceIn(0.0,1.0)
        val injuryMultiplier=(1.0-(.11*targetShare+.07*carryShare)).coerceIn(.83,1.0)

        // EPA is a limited matchup adjustment, not counted as a second independent source.
        val epa=((teamMetrics?.offEpaPerPlay ?: 0.0) +
            (opposingMetrics?.defEpaAllowedPerPlay ?: 0.0)).coerceIn(-.45,.45)
        val epaMultiplier=(1.0+epa*.16).coerceIn(.93,1.07)
        val base=if(team==game.awayTeam)calibration.awayMatchupPpg else calibration.homeMatchupPpg
        val expected=(base*quarterbackFactor*injuryMultiplier*epaMultiplier).coerceIn(7.0,37.0)
        val driveTarget=calibration.driveTarget.coerceIn(8.0,13.5)

        // Empirical TD/FG drive rates only when the source's game count predates the match.
        val before=if(team==game.awayTeam)calibration.awayGames else calibration.homeGames
        val validPbp=teamMetrics!=null && teamMetrics.games in 2..before && teamMetrics.drives>=20 &&
            teamMetrics.tdPerDrive in .04.. .52 && teamMetrics.fgPerDrive in .015.. .36
        val tdPrior=.220
        val fgPrior=.155
        val tdBase=if(validPbp) (teamMetrics!!.tdPerDrive*teamMetrics.drives + tdPrior*50.0)/(teamMetrics.drives+50.0) else tdPrior
        val fgBase=if(validPbp) (teamMetrics!!.fgPerDrive*teamMetrics.drives + fgPrior*50.0)/(teamMetrics.drives+50.0) else fgPrior
        val unscaledPoints=(tdBase*6.94+fgBase*3.0)*driveTarget
        // The scale is based solely on prior-week scored/allowed PPG, not on the bookmaker line.
        val scale=(expected/unscaledPoints).coerceIn(.65,1.4)
        val pTd=(tdBase*scale).coerceIn(.07,.44)
        val pFg=(fgBase*scale).coerceIn(.04,.32)
        val warning=buildList {
            if(!teamLineup.verified)add("QB previsto/no confirmado")
            if(starterAttempts<20.0)add("muestra QB pequeña: alta incertidumbre")
            if(qb==null)add("QB no identificado en Player Weekly; prior neutral")
            if(!validPbp)add("sin tasas PBP de drives anteriores; usa prior de liga")
            if(teamLineup.outNames.isEmpty())add("sin bajas confirmadas precargadas")
            if(missing.isNotEmpty())add("bajas ofensivas estimadas; suplentes sin eficiencia certificada")
            if(calibration.leagueGames<16)add("pocos partidos previos para calibracion")
        }.joinToString(" · ")
        return ScoringTeamParameters(team,expected,driveTarget,pTd,pFg,starterName,
            starterAttempts,quarterbackFactor,missedShare,injuryMultiplier,epaMultiplier,
            if(validPbp)"PBP previo del equipo + PPG anteriores" else "PPG anteriores + priors de liga",
            teamLineup.verified,warning)
    }

    fun run(
        game:GameRecord, awayUsage:TeamUsage, homeUsage:TeamUsage,
        awayMetrics:TeamMetrics?,homeMetrics:TeamMetrics?,
        lineup:GameLineup, calibration:SimcastCalibration,
        n:Int, seed:Int
    ):ScoringShadowResult {
        require(n in setOf(100,500,1000)) { "N debe ser 100, 500 o 1000" }
        val away=parameters(game.awayTeam,game,awayUsage,awayMetrics,homeMetrics,lineup.away,calibration)
        val home=parameters(game.homeTeam,game,homeUsage,homeMetrics,awayMetrics,lineup.home,calibration)
        val rng=Random(seed xor 0x13A54D19)
        val scores=mutableMapOf<Pair<Int,Int>,Int>()
        val totals=IntArray(n)
        var aSum=0L;var hSum=0L;var aWins=0;var hWins=0;var ties=0
        var over=0;var under=0;var push=0
        fun normal():Double {
            val u1=rng.nextDouble().coerceAtLeast(1e-12)
            return sqrt(-2*ln(u1))*cos(2*Math.PI*rng.nextDouble())
        }
        fun sample(team:ScoringTeamParameters, commonPace:Double):Int {
            // A common game-pace term couples both teams without pretending independence.
            val nDrives=(team.drivesPerGame*commonPace+normal()*1.3).roundToInt().coerceIn(6,16)
            val gameForm=exp(.12*normal()-.5*.12*.12)
            var points=0
            for(d in 0 until nDrives) {
                val td=(team.pTouchdown*gameForm).coerceIn(.02,.50)
                val fg=(team.pFieldGoal*gameForm).coerceIn(.01,.35)
                val draw=rng.nextDouble()
                if(draw<td) {
                    points+=6
                    val tryRoll=rng.nextDouble()
                    if(tryRoll<.94)points++ else if(tryRoll>.985)points+=2
                } else if(draw<td+fg)points+=3
            }
            return points
        }
        for(i in 0 until n){
            val pace=exp(.075*normal()-.5*.075*.075)
            val a=sample(away,pace)
            val h=sample(home,pace)
            aSum+=a;hSum+=h
            totals[i]=a+h
            scores[a to h]=(scores[a to h] ?: 0)+1
            if(a>h)aWins++ else if(h>a)hWins++ else ties++
            game.totalLine?.let { line -> if(a+h>line)over++ else if(a+h<line)under++ else push++ }
        }
        val mean=(aSum+hSum).toDouble()/n
        val sorted=totals.sorted()
        val sd=sqrt(totals.sumOf{(it-mean).pow(2.0)}/n)
        return ScoringShadowResult(n,aSum.toDouble()/n,hSum.toDouble()/n,mean,sorted[n/2],sd,
            sorted[((n-1)*.10).toInt()],sorted[((n-1)*.90).toInt()],
            if(game.totalLine!=null)over.toDouble()/n else null,
            if(game.totalLine!=null)under.toDouble()/n else null,
            if(game.totalLine!=null)push.toDouble()/n else null,
            aWins.toDouble()/n,hWins.toDouble()/n,ties.toDouble()/n,away,home,
            scores.entries.sortedByDescending{it.value}.take(10).map{ScoreFrequency(it.key.first,it.key.second,it.value)})
    }
}
