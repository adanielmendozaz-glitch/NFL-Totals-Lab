package com.nfltotalslab.app.simcast

import com.nfltotalslab.app.data.GameRecord
import com.nfltotalslab.app.data.TeamMetrics
import kotlin.math.pow

/** Pregame-only empirical shrinkage. Scores use strictly previous regular-season weeks.
 * NFLverse season-to-date drive metrics may only adjust tempo if their game counts
 * do not exceed observed prior completed games; no future rows are read here. */
data class SimcastCalibration(
    val cutoffWeek:Int=0,
    val leagueGames:Int=0,
    val awayGames:Int=0,
    val homeGames:Int=0,
    val leaguePpg:Double=21.5,
    val awayPpg:Double=21.5,
    val homePpg:Double=21.5,
    val awayScoringFactor:Double=1.0,
    val homeScoringFactor:Double=1.0,
    val clockFactor:Double=1.0,
    val driveTarget:Double=10.6,
    val driveSampleTeams:Int=0,
    val awayMatchupPpg:Double=21.5,
    val homeMatchupPpg:Double=21.5,
    val awayMetricsSource:String="SIN EPA",
    val homeMetricsSource:String="SIN EPA",
    val playerSource:String="nflverse Player Weekly",
    val method:String="PRIOR_WEEKS_2026_EMPIRICAL_V161"
){
    fun factor(team:String,game:GameRecord):Double = if(team==game.awayTeam)awayScoringFactor else homeScoringFactor
}

object PregameSimcastCalibration {
    fun build(game:GameRecord,games:List<GameRecord>,awayMetrics:TeamMetrics?,homeMetrics:TeamMetrics?):SimcastCalibration {
        val prior=games.filter { it.season==game.season && it.gameType=="REG" && it.finished && it.week < game.week }
        val global=(prior.sumOf{(it.awayScore ?: 0)+(it.homeScore ?: 0)}+21.5*2*16)/(2*prior.size+32.0)
        fun gamesFor(team:String)=prior.filter{it.awayTeam==team || it.homeTeam==team}
        fun scored(g:GameRecord,team:String):Int=if(g.awayTeam==team)g.awayScore ?: 0 else g.homeScore ?: 0
        fun allowed(g:GameRecord,team:String):Int=if(g.awayTeam==team)g.homeScore ?: 0 else g.awayScore ?: 0
        val ag=gamesFor(game.awayTeam);val hg=gamesFor(game.homeTeam)
        fun shrunk(data:List<GameRecord>,value:(GameRecord)->Int):Double =
            (data.sumOf{value(it)}+global*5)/(data.size+5.0)
        val aOff=shrunk(ag){scored(it,game.awayTeam)}
        val hOff=shrunk(hg){scored(it,game.homeTeam)}
        val aDef=shrunk(ag){allowed(it,game.awayTeam)}
        val hDef=shrunk(hg){allowed(it,game.homeTeam)}
        fun factor(off:Double,oppAllow:Double):Double =
            ((off/global).coerceIn(.4,2.5)*(oppAllow/global).coerceIn(.4,2.5)).pow(.27).coerceIn(.83,1.17)
        // Only use season-to-date drive counts when they cannot include more games
        // than are known to have finished before this matchup's week.
        val validPaces=listOf(awayMetrics to ag.size,homeMetrics to hg.size).mapNotNull { (m,n) ->
            if(m!=null && n>=2 && m.games in 2..n && m.drivesPerGame in 7.0..15.0) m.drivesPerGame else null
        }
        val target=if(validPaces.isEmpty())10.6 else (validPaces.average()*validPaces.size+10.6*4)/(validPaces.size+4)
        return SimcastCalibration(
            cutoffWeek=(game.week-1).coerceAtLeast(0),leagueGames=prior.size,
            awayGames=ag.size,homeGames=hg.size,
            leaguePpg=global,awayPpg=aOff,homePpg=hOff,
            awayScoringFactor=factor(aOff,hDef),homeScoringFactor=factor(hOff,aDef),
            clockFactor=(10.6/target).coerceIn(.88,1.12),driveTarget=target,driveSampleTeams=validPaces.size,
            awayMatchupPpg=((aOff+hDef)/2.0).coerceIn(10.0,35.0),
            homeMatchupPpg=((hOff+aDef)/2.0).coerceIn(10.0,35.0)
        )
    }
}
