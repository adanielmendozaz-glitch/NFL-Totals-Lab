package com.nfltotalslab.app.simcast

import com.nfltotalslab.app.data.GameRecord
import com.nfltotalslab.app.data.TeamMetrics
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Inputs are prior-week NFLverse player stats, never a player's future box score. */
data class UsagePlayer(
    val id:String, val name:String, val position:String, val team:String,
    val passAttempts:Double=0.0, val carries:Double=0.0, val targets:Double=0.0,
    val completions:Double=0.0, val passingYards:Double=0.0,
    val rushingYards:Double=0.0, val receptions:Double=0.0, val receivingYards:Double=0.0
)
data class TeamUsage(val team:String, val throughWeek:Int, val players:List<UsagePlayer>){
    val verified:Boolean get()=throughWeek>0 && players.any{it.position=="QB" && it.passAttempts>0}
}

/** Pregame input only: officially announced starter when known, otherwise manually selected QB.
 * Out means confirmed unavailable; Questionable is not excluded until final inactives.
 * This deliberately does not alter the official Core pick. */
data class TeamLineup(
    val startingQb:String="",
    val outNames:Set<String> = emptySet(),
    val source:String="Sin titular confirmado",
    val verified:Boolean=false
)

data class GameLineup(
    val away:TeamLineup=TeamLineup(),
    val home:TeamLineup=TeamLineup()
)

/** nflverse weekly efficiency computed from weighted *prior* player box scores.
 * Volume ratios are regularized toward NFL priors because a four-game window is noisy. */
internal data class ObservedTeamEfficiency(
    val passRate:Double,val rushYpc:Double,val passYpa:Double,
    val completionRate:Double,val passAttempts:Double,val rushAttempts:Double
){
    companion object {
        fun from(usage:TeamUsage):ObservedTeamEfficiency {
            val qbs=usage.players.filter{it.position=="QB"}
            val pass=qbs.sumOf{it.passAttempts}.coerceAtLeast(0.0)
            val completions=qbs.sumOf{it.completions}.coerceIn(0.0,pass)
            val passYds=qbs.sumOf{it.passingYards}.coerceAtLeast(0.0)
            val rush=usage.players.sumOf{it.carries}.coerceAtLeast(0.0)
            val rushYds=usage.players.sumOf{it.rushingYards}
            val share=(pass+.58*100)/(pass+rush+100)
            return ObservedTeamEfficiency(share.coerceIn(.40,.76),
                ((rushYds+4.30*70)/(rush+70)).coerceIn(2.8,6.8),
                ((passYds+7.0*85)/(pass+85)).coerceIn(4.0,10.0),
                ((completions+.62*85)/(pass+85)).coerceIn(.48,.74),pass,rush)
        }
    }
}

/** Fixed to this specific fixture; never generalized across later weeks. Updated 2026-11-05.
 * Source: https://www.baltimoreravens.com/news/lamar-jackson-tyler-huntley-injured-reserve-austin-reed-adisa-isaac-jake-moody
 * Inactives are not inferred for players listed Questionable. */
object PregameLineup {
    fun forGame(game:GameRecord):GameLineup {
        if(game.season==2026 && game.week==9 && game.homeTeam=="BAL" && game.awayTeam=="JAX"){
            return GameLineup(
                away=TeamLineup(source="JAX: titular pendiente de confirmar"),
                home=TeamLineup(
                    startingQb="Joe Fagnano",
                    outNames=setOf("Lamar Jackson","Tyler Huntley"),
                    source="BAL: QB anunciado y bajas en IR, 2026-11-05",
                    verified=true
                )
            )
        }
        return GameLineup()
    }
}

data class SimPlay(
    val index:Int, val drive:Int, val offense:String, val quarter:Int, val clock:Int,
    val down:Int, val distance:Int, val fromYard:Int, val toYard:Int,
    val kind:String, val description:String, val actor:String, val secondary:String,
    val yards:Int, val points:Int, val awayScore:Int, val homeScore:Int
)

data class SimPlayerStats(
    val team:String, val name:String, val position:String,
    var passAttempts:Int=0, var completions:Int=0, var passYards:Int=0,
    var interceptions:Int=0, var rushAttempts:Int=0, var rushYards:Int=0,
    var targets:Int=0, var receptions:Int=0, var receivingYards:Int=0,
    var passingTds:Int=0, var rushingTds:Int=0, var receivingTds:Int=0
)

data class GameSim(
    val gameId:String, val season:Int, val week:Int,
    val away:String, val home:String, val seed:Int,
    val awayUsageWeek:Int, val homeUsageWeek:Int,
    val awayPlayerCount:Int, val homePlayerCount:Int,
    val plays:List<SimPlay>, val players:List<SimPlayerStats>,
    val usageInputs:List<UsagePlayer>,
    val awayQuarters:List<Int>, val homeQuarters:List<Int>,
    val lineup:GameLineup=GameLineup(),
    val calibration:SimcastCalibration=SimcastCalibration(),
    val awayOvertime:Int=0, val homeOvertime:Int=0,
    val overtimePlayed:Boolean=false,
    val integrityErrors:List<String> = emptyList()
){
    val awayFinal:Int get()=awayQuarters.sum()+awayOvertime
    val homeFinal:Int get()=homeQuarters.sum()+homeOvertime
}

/**
 * V1.6 experimental drive simulator. Models possession and period boundaries explicitly.
 * All random parameters are research priors and must be calibrated out of sample.
 * This remains separate from the official Core probability.
 */
class PlayerUsageDriveEngine {
    fun simulate(
        game:GameRecord, awayUsage:TeamUsage, homeUsage:TeamUsage,
        awayMetrics:TeamMetrics?, homeMetrics:TeamMetrics?, seed:Int,
        lineup:GameLineup=GameLineup(), calibration:SimcastCalibration=SimcastCalibration()
    ):GameSim {
        val rng=Random(seed)
        val plays=mutableListOf<SimPlay>()
        val stats=linkedMapOf<String,SimPlayerStats>()
        val awayQ=IntArray(4)
        val homeQ=IntArray(4)
        var awayOt=0
        var homeOt=0
        val withLineup=mapOf(game.awayTeam to lineup.away,game.homeTeam to lineup.home)
        val usage=mapOf(game.awayTeam to awayUsage,game.homeTeam to homeUsage).mapValues { (team,base) ->
            val outs=withLineup.getValue(team).outNames.map{it.trim().lowercase()}.toSet()
            base.copy(players=base.players.filterNot{it.name.trim().lowercase() in outs})
        }
        val starters=usage.mapValues { (team,base) ->
            val name=withLineup.getValue(team).startingQb.trim()
            val pool=base.players.filter{it.position=="QB"}
            pool.firstOrNull{it.name.equals(name,true)}
                ?: if(name.isNotBlank()) UsagePlayer("manual:$team:$name",name,"QB",team,passAttempts=1.0)
                   else pool.maxByOrNull{it.passAttempts}
                    ?: UsagePlayer("fallback:$team:QB","QB sin datos","QB",team,passAttempts=1.0)
        }
        val metrics=mapOf(game.awayTeam to awayMetrics,game.homeTeam to homeMetrics)
        val observed=usage.mapValues{ObservedTeamEfficiency.from(it.value)}
        fun opponent(team:String)=if(team==game.awayTeam)game.homeTeam else game.awayTeam
        fun score(team:String)= if(team==game.awayTeam)awayQ.sum()+awayOt else homeQ.sum()+homeOt
        fun choose(team:String,role:String):UsagePlayer {
            val starter=starters.getValue(team)
            val pool=usage.getValue(team).players
            val eligible=when(role){
                "QB"->listOf(starter)
                "RUSH"->pool.filter{it.carries>0 && (it.position!="QB" || it.id==starter.id)}
                else->pool.filter{it.targets>0}
            }
            if(eligible.isEmpty())return UsagePlayer("fallback:$team:$role",
                if(role=="TARGET")"Receptor sin datos" else "Corredor sin datos",
                if(role=="TARGET")"WR" else "RB",team)
            fun weight(p:UsagePlayer)=when(role){"QB"->p.passAttempts;"RUSH"->p.carries;else->p.targets}.coerceAtLeast(.1)
            var draw=rng.nextDouble()*eligible.sumOf{weight(it)}
            return eligible.firstOrNull{draw-=weight(it);draw<=0} ?: eligible.last()
        }
        fun playerStats(p:UsagePlayer)=stats.getOrPut("${p.team}|${p.id}"){
            SimPlayerStats(p.team,p.name,p.position)
        }
        // Prior sample sizes prevent a rookie QB or one explosive run from dominating its matchup.
        fun completionRate(qb:UsagePlayer,offense:String):Double {
            val sample=qb.passAttempts
            val prior=.62
            val priorWeight=85.0
            val teamRate=observed.getValue(offense).completionRate
            val blendedPrior=.75*prior+.25*teamRate
            val estimated=if(qb.completions<=0.0 && qb.passingYards<=0.0)blendedPrior
                else (qb.completions+blendedPrior*priorWeight)/(sample+priorWeight)
            val opp=metrics[opponent(offense)]
            val m=metrics[offense]
            val passEpa=(m?.offPassEpa ?: m?.offEpaPerPlay ?: 0.0)+
                (opp?.defPassEpaAllowed ?: opp?.defEpaAllowedPerPlay ?: 0.0)
            val defensiveAdjustment=(passEpa*.12).coerceIn(-.05,.05)
            return (estimated+defensiveAdjustment+
                (calibration.factor(offense,game)-1.0)*.12).coerceIn(.48,.72)
        }
        fun rushEfficiency(runner:UsagePlayer,offense:String):Double {
            val priorYpc=4.35
            val posterior=if(runner.rushingYards<=0.0)priorYpc
                else (runner.rushingYards+priorYpc*60)/(runner.carries+60)
            val m=metrics[offense];val opp=metrics[opponent(offense)]
            val epa=((m?.offRushEpa ?: m?.offEpaPerPlay ?: 0.0)+
                (opp?.defRushEpaAllowed ?: opp?.defEpaAllowedPerPlay ?: 0.0)).coerceIn(-.3,.3)
            val observedYpc=observed.getValue(offense).rushYpc
            val blendedYpc=.75*posterior+.25*observedYpc
            return ((blendedYpc/priorYpc)*(1+epa*.20)*
                (1.0+(calibration.factor(offense,game)-1.0)*.30)).coerceIn(.74,1.28)
        }
        fun catchEfficiency(target:UsagePlayer,qb:UsagePlayer):Double {
            val ypr=(target.receivingYards+12.0*18)/(target.receptions+18)
            val quarterbackYpa=if(qb.passingYards<=0.0)6.8
                else (qb.passingYards+6.8*95)/(qb.passAttempts+95)
            val teamYpa=observed.getValue(target.team).passYpa
            return ((ypr/12.0)*.60+(quarterbackYpa/6.8)*.28+
                (teamYpa/7.0)*.12).coerceIn(.70,1.40)
        }
        fun passShare(team:String,remaining:Int):Double {
            // Empirical ratio from nflverse Player Weekly, with 100-play prior.
            val baseline=observed.getValue(team).passRate.coerceIn(.42,.73)
            val deficit=score(opponent(team))-score(team)
            val gameScript=when{
                remaining<=480 && deficit>=8 -> .13
                remaining<=900 && deficit>=3 -> .07
                remaining<=700 && deficit<=-10 -> -.09
                else -> 0.0
            }
            return (baseline+gameScript).coerceIn(.40,.81)
        }
        fun drawRushYards():Int {
            val z=rng.nextInt(1000)
            return when(z){
                in 0..189 -> -(rng.nextInt(4))    // negative or stopped
                in 190..549 -> 1+rng.nextInt(4)
                in 550..869 -> 5+rng.nextInt(4)
                in 870..979 -> 9+rng.nextInt(7)
                else -> 16+rng.nextInt(19)
            }
        }
        fun drawPassYards():Int {
            val z=rng.nextInt(1000)
            return when(z){
                in 0..279 -> 1+rng.nextInt(5)
                in 280..749 -> 6+rng.nextInt(8)
                in 750..949 -> 14+rng.nextInt(11)
                else -> 25+rng.nextInt(25)
            }
        }
        fun snapTime(kind:String):Int{
            val elapsed=if(kind=="INCOMPLETE"||kind=="INT") 8+rng.nextInt(11)
                else if(kind=="KICKOFF")5+rng.nextInt(4)
                else 23+rng.nextInt(19)
            return (elapsed*calibration.clockFactor).toInt().coerceAtLeast(1)
        }
        var remaining=3600
        var overtimeRemaining=600
        var isOvertime=false
        var drive=0
        var possession=if(rng.nextBoolean())game.awayTeam else game.homeTeam
        var spot=35
        var kickoffNext=true
        val openingReceiver=possession
        val secondHalfReceiver=opponent(openingReceiver)
        var halftimeDone=false
        var overtimePlayed=false
        var otPossessions=0

        fun append(team:String,kind:String,description:String,actor:String="",receiver:String="",
                   from:Int=spot,to:Int=spot,yards:Int=0,points:Int=0,
                   down:Int=1,distance:Int=10,clockAtSnap:Int=remaining,driveId:Int=drive){
            val quarter=if(isOvertime)5 else if(kind=="HALFTIME")2 else when {
                clockAtSnap>2700->1;clockAtSnap>1800->2;clockAtSnap>900->3;else->4
            }
            val clock=if(isOvertime)clockAtSnap.coerceIn(0,600)
                else (clockAtSnap-((4-quarter)*900)).coerceIn(0,900)
            if(points>0) {
                if(isOvertime){if(team==game.awayTeam)awayOt+=points else homeOt+=points}
                else{if(team==game.awayTeam)awayQ[quarter-1]+=points else homeQ[quarter-1]+=points}
            }
            plays.add(SimPlay(plays.size+1,driveId,team,quarter,clock,down,distance,
                from,to,kind,description,actor,receiver,yards,points,score(game.awayTeam),score(game.homeTeam)))
        }
        fun kickoff(team:String,atClock:Int){
            val returnYard=if(rng.nextDouble()<.55)35 else 23+rng.nextInt(17)
            spot=returnYard
            append(team,"KICKOFF","Recepción de kickoff; balón en la yarda $returnYard",
                from=0,to=returnYard,clockAtSnap=atClock,driveId=drive+1)
            if(isOvertime)overtimeRemaining=(overtimeRemaining-snapTime("KICKOFF")).coerceAtLeast(0)
            else remaining=(remaining-snapTime("KICKOFF")).coerceAtLeast(if(!halftimeDone)1800 else 0)
        }
        fun extraPoint(team:String,clockAtSnap:Int){
            // Walk-off TD after each team has had an opening chance requires no try.
            if(isOvertime && otPossessions>=1 && score(team)>score(opponent(team)))return
            val twoPoint= !isOvertime && remaining<300 && (score(opponent(team))-score(team)) in setOf(1,2,5,8)
            val success=rng.nextDouble() < if(twoPoint).46 else .945
            val kind=if(twoPoint)if(success)"TWO_GOOD" else "TWO_FAILED"
                        else if(success)"XP_GOOD" else "XP_MISSED"
            append(team,kind,if(twoPoint)"Intento de dos puntos: ${if(success)"convertido" else "fallado"}"
                    else "Punto extra ${if(success)"convertido" else "fallado"}",points=if(success)if(twoPoint)2 else 1 else 0,
                    clockAtSnap=clockAtSnap)
        }
        fun markHalftime(){
            append(possession,"HALFTIME","Descanso: termina la primera mitad",clockAtSnap=1800)
            // If a late Q2 kickoff was returned as time expired, its provisional
            // next-drive id cannot be reused by the mandatory Q3 kickoff.
            if(plays.lastOrNull()?.kind=="HALFTIME" && plays.getOrNull(plays.lastIndex-1)?.kind=="KICKOFF")drive++
            halftimeDone=true
            remaining=1800
            possession=secondHalfReceiver
            kickoffNext=true
            spot=35
        }
        fun runPossession():String {
            drive++
            var down=1
            var dist=min(10,100-spot)
            var playsInDrive=0
            var nextSpot=35
            var ending="CLOCK"
            var ended=false
            val team=possession
            val opponentTeam=opponent(team)
            val m=metrics[team]
            val opp=metrics[opponentTeam]
            val passEpa=((m?.offPassEpa ?: m?.offEpaPerPlay ?: 0.0)+
                (opp?.defPassEpaAllowed ?: opp?.defEpaAllowedPerPlay ?: 0.0)).coerceIn(-.3,.3)
            while(!ended && (if(isOvertime)overtimeRemaining else remaining)>0
                && (isOvertime || halftimeDone || remaining>1800)
                && plays.size<250 && playsInDrive<18){
                playsInDrive++
                val before=if(isOvertime)overtimeRemaining else remaining
                val from=spot
                val d=down
                val needed=dist
                var kind=""
                var desc=""
                var actor=""
                var receiver=""
                var yards=0
                var points=0
                var turnover=false
                val qb=choose(team,"QB")
                // 4th-down decisions depend on field position, distance and clock.
                val lateTrailing=(if(isOvertime)overtimeRemaining else remaining) < 360 &&
                    score(team)<score(opponentTeam)
                val goForFourth=down==4 && playsInDrive<18 && spot in 42..93 &&
                    (lateTrailing || (dist<=2 && rng.nextDouble()<.42) ||
                        (dist<=5 && spot>=69 && rng.nextDouble()<.18))
                if((down==4 && !goForFourth) || playsInDrive==18){
                    if(spot>=62){
                        val kickDistance=100-spot+17
                        val fgChance=when{
                            kickDistance<=35->.96
                            kickDistance<=45->.87
                            kickDistance<=55->.70
                            kickDistance<=60->.47
                            else->.20
                        }
                        if(rng.nextDouble()<fgChance){
                            kind="FG";points=3;desc="Gol de campo convertido desde $kickDistance yardas";kickoffNext=true
                        }else{
                            kind="FG_FALLADO";desc="Gol de campo fallado desde $kickDistance yardas"
                            nextSpot=(100-spot+7).coerceIn(20,95)
                        }
                    }else{
                        kind="PUNT";desc="Despeje en cuarto down"
                        val puntSpot=(100-spot-(37+rng.nextInt(17))).coerceAtLeast(20)
                        nextSpot=puntSpot.coerceIn(20,95)
                    }
                    ended=true;ending=kind
                }else if(rng.nextDouble()<passShare(team,remaining)){
                    val target=choose(team,"TARGET")
                    actor=qb.name;receiver=target.name
                    val qbstats=playerStats(qb);val rcstats=playerStats(target)
                    qbstats.passAttempts++;rcstats.targets++
                    val completion=completionRate(qb,team)
                    val isSack=rng.nextDouble()<.038
                    val roll=rng.nextDouble()
                    when {
                        isSack->{
                            kind="SACK";yards=-(2+rng.nextInt(7));desc="Captura a ${qb.name}"
                            qbstats.passAttempts--;rcstats.targets--;receiver=""
                        }
                        roll<.019->{
                            kind="INT";turnover=true;qbstats.interceptions++
                            desc="Intercepción en pase de ${qb.name}"
                        }
                        roll<.019+completion->{
                            kind="PASS";qbstats.completions++;rcstats.receptions++
                            yards=(drawPassYards()*catchEfficiency(target,qb)*(1.0+passEpa*.25)).toInt().coerceIn(1,100-spot)
                            qbstats.passYards+=yards;rcstats.receivingYards+=yards
                            desc="${qb.name} → ${target.name} · +$yards yd"
                        }
                        else->{kind="INCOMPLETE";desc="Pase incompleto de ${qb.name} a ${target.name}"}
                    }
                }else{
                    val runner=choose(team,"RUSH")
                    actor=runner.name;val line=playerStats(runner)
                    line.rushAttempts++
                    val raw=drawRushYards()
                    yards=if(raw<=0)raw else (raw*rushEfficiency(runner,team)).toInt().coerceAtLeast(1)
                    yards=yards.coerceIn(-10,100-spot)
                    line.rushYards+=yards
                    kind="RUN"
                    desc="Acarreo de ${runner.name} · ${if(yards>=0)"+" else ""}$yards yd"
                    if(rng.nextDouble()<.007 && spot+yards<100){
                        kind="FUMBLE";turnover=true;desc="Balón perdido por ${runner.name}"
                    }
                }
                val elapsed=snapTime(kind)
                if(isOvertime)overtimeRemaining=(overtimeRemaining-elapsed).coerceAtLeast(0)
                else remaining=(remaining-elapsed).coerceAtLeast(if(!halftimeDone)1800 else 0)
                spot=(spot+yards).coerceIn(1,100)
                if(!ended && !turnover && spot>=100){
                    kind=if(kind=="RUN")"RUSH_TD" else "PASS_TD"
                    points=6;desc+=" · TOUCHDOWN"
                    if(kind=="RUSH_TD")playerStats(chooseTeamPlayer(team,actor,usage,starters)).rushingTds++
                    else{
                        playerStats(qb).passingTds++
                        playerStats(chooseTeamPlayer(team,receiver,usage,starters)).receivingTds++
                    }
                    ended=true;ending="TD";kickoffNext=true
                }
                append(team,kind,desc,actor,receiver,from,spot,yards,points,d,needed,before)
                if(ending=="TD")extraPoint(team,before)
                if(turnover){ended=true;ending="TURNOVER";nextSpot=(100-spot).coerceIn(1,99)}
                if(!ended){
                    if(d==4 && yards<dist){
                        ended=true;ending="DOWNS";nextSpot=(100-spot).coerceIn(1,99)
                    }else if(yards>=dist){down=1;dist=min(10,100-spot)}
                    else{down++;dist=(dist-yards).coerceIn(1,35)}
                }
            }
            // A kickoff following a score has no hidden change of field position.
            if(!kickoffNext)spot=nextSpot
            return ending
        }
        // Choose by name only for updating the already-selected actor, never a new random player.
        while(remaining>0 && plays.size<235){
            if(!halftimeDone && remaining<=1800){markHalftime();continue}
            if(kickoffNext){kickoff(possession,remaining);kickoffNext=false}
            if(!halftimeDone && remaining<=1800){markHalftime();continue}
            if(remaining<=0)break
            runPossession()
            if(!halftimeDone && remaining<=1800){markHalftime();continue}
            possession=opponent(possession)
        }
        if(awayQ.sum()==homeQ.sum() && plays.size<240){
            overtimePlayed=true;isOvertime=true;overtimeRemaining=600
            possession=if(rng.nextBoolean())game.awayTeam else game.homeTeam
            otPossessions=0
            kickoffNext=true
            append(possession,"OVERTIME_START","Inicio de tiempo extra: periodo máximo de 10 minutos",
                clockAtSnap=overtimeRemaining)
            while(overtimeRemaining>0 && plays.size<250){
                if(kickoffNext){kickoff(possession,overtimeRemaining);kickoffNext=false}
                if(overtimeRemaining<=0)break
                runPossession()
                otPossessions++
                if(otPossessions>=2 && score(game.awayTeam)!=score(game.homeTeam))break
                possession=opponent(possession)
            }
        }
        val result=GameSim(game.gameId,game.season,game.week,game.awayTeam,game.homeTeam,seed,
            awayUsage.throughWeek,homeUsage.throughWeek,awayUsage.players.size,homeUsage.players.size,
            plays,stats.values.toList(),
            listOf(game.awayTeam,game.homeTeam).flatMap{team->
                val p=usage.getValue(team).players;val qb=starters.getValue(team)
                if(p.any{it.id==qb.id})p else p+qb
            },awayQ.toList(),homeQ.toList(),lineup,calibration,awayOt,homeOt,overtimePlayed)
        val errors=SimcastIntegrity.check(result)
        return result.copy(integrityErrors=errors)
    }
    private fun chooseTeamPlayer(team:String,name:String,usage:Map<String,TeamUsage>,starters:Map<String,UsagePlayer>):UsagePlayer {
        return usage.getValue(team).players.firstOrNull{it.name==name}
            ?:starters.getValue(team).takeIf{it.name==name}
            ?:UsagePlayer("fallback:$team:$name",name,"WR",team)
    }
}
