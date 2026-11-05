package com.nfltotalslab.app.simcast

import com.nfltotalslab.app.data.GameRecord
import com.nfltotalslab.app.data.TeamMetrics
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Inputs are prior-week NFLverse player stats, never a player's future box score. */
data class UsagePlayer(
    val id:String, val name:String, val position:String, val team:String,
    val passAttempts:Double=0.0, val carries:Double=0.0, val targets:Double=0.0
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
    val calibration:SimcastCalibration=SimcastCalibration()
){
    val awayFinal:Int get()=awayQuarters.sum()
    val homeFinal:Int get()=homeQuarters.sum()
}

/**
 * Experimental drive / down-distance state simulator. Separate from the official Core
 * probability. Usage chooses real QB / target / carrier; it is NOT play-by-play recreation.
 */
class PlayerUsageDriveEngine {
    fun simulate(
        game:GameRecord, awayUsage:TeamUsage, homeUsage:TeamUsage,
        awayMetrics:TeamMetrics?, homeMetrics:TeamMetrics?, seed:Int,
        lineup:GameLineup=GameLineup(),
        calibration:SimcastCalibration=SimcastCalibration()
    ):GameSim {
        val rng=Random(seed)
        val plays=mutableListOf<SimPlay>()
        val stats=linkedMapOf<String,SimPlayerStats>()
        val awayQ=IntArray(4)
        val homeQ=IntArray(4)
        // Exclude confirmed OUT / IR for this specific game, not questionable athletes.
        val withLineup=mapOf(game.awayTeam to lineup.away, game.homeTeam to lineup.home)
        val usage=mapOf(game.awayTeam to awayUsage, game.homeTeam to homeUsage).mapValues { (team,base) ->
            val outs=withLineup.getValue(team).outNames.map { it.trim().lowercase() }.toSet()
            base.copy(players=base.players.filterNot { it.name.trim().lowercase() in outs })
        }
        val starters=usage.mapValues { (team,base) ->
            val name=withLineup.getValue(team).startingQb.trim()
            val pool=base.players.filter { it.position=="QB" }
            val exact=pool.firstOrNull { it.name.equals(name,ignoreCase=true) }
            if(name.isNotEmpty()) exact ?: UsagePlayer("manual:$team:$name",name,"QB",team,passAttempts=1.0)
            else pool.maxByOrNull { it.passAttempts }
                ?: UsagePlayer("fallback:$team:QB","QB sin datos","QB",team,passAttempts=1.0)
        }
        val teamMetrics=mapOf(game.awayTeam to awayMetrics, game.homeTeam to homeMetrics)
        var remaining=3600
        var offense=if(rng.nextBoolean())game.awayTeam else game.homeTeam
        var drive=0
        var lastSpot=25
        var lastWasTurnover=false
        var drivePlays=0

        fun opponent(team:String)=if(team==game.awayTeam) game.homeTeam else game.awayTeam
        fun currentScore(team:String)=if(team==game.awayTeam)awayQ.sum() else homeQ.sum()
        fun player(team:String,role:String):UsagePlayer {
            val pool=usage.getValue(team).players
            val starter=starters.getValue(team)
            val eligible=when(role){
                "QB"->listOf(starter)
                "RUSH"->pool.filter{it.carries>0 && (it.position!="QB" || it.id==starter.id)}
                else->pool.filter{it.targets>0}
            }
            if(eligible.isEmpty())return UsagePlayer("fallback:$team:$role",when(role){
                "QB"->"QB sin datos";"RUSH"->"Corredor sin datos";else->"Receptor sin datos"
            },when(role){"QB"->"QB";"RUSH"->"RB";else->"WR"},team)
            fun weight(p:UsagePlayer)=when(role){"QB"->p.passAttempts;"RUSH"->p.carries;else->p.targets}.coerceAtLeast(0.1)
            val total=eligible.sumOf{weight(it)}
            var draw=rng.nextDouble()*total
            return eligible.firstOrNull{draw-=weight(it);draw<=0.0} ?: eligible.last()
        }
        fun record(p:UsagePlayer):SimPlayerStats = stats.getOrPut("${p.team}|${p.id}"){
            SimPlayerStats(p.team,p.name,p.position)
        }
        fun add(kind:String,text:String,actor:String,secondary:String,yards:Int,points:Int,
                down:Int,dist:Int,from:Int,to:Int,clock:Int){
            val q=((3600-clock)/900).coerceIn(0,3)
            if(points>0){if(offense==game.awayTeam)awayQ[q]+=points else homeQ[q]+=points}
            plays+=SimPlay(plays.size+1,drive,offense,q+1,(clock-1).coerceAtLeast(0)%900,
                down,dist,from,to,kind,text,actor,secondary,yards,points,awayQ.sum(),homeQ.sum())
        }
        // Approximate dropback tendency from prior-week passing + rushing usage.
        fun passShare(team:String):Double {
            val x=usage.getValue(team).players
            // Play-call rate is team-level; use the most recent QB usage in team data,
            // while only the selected starter actually throws on the simulated field.
            val qb=x.filter{it.position=="QB"}.sumOf{it.passAttempts}
            val rush=x.sumOf{it.carries}
            return if(qb+rush>20.0) (qb/(qb+rush)).coerceIn(.43,.76) else .58
        }
        while(remaining>0 && plays.size<240){
            drive++
            drivePlays=0
            var yard=if(lastWasTurnover)lastSpot.coerceIn(20,85) else (22+rng.nextInt(18))
            lastWasTurnover=false
            var down=1
            var dist=10
            val m=teamMetrics[offense]
            val opp=teamMetrics[opponent(offense)]
            val scoringFactor=calibration.factor(offense,game)
            val adjustment=(((m?.offEpaPerPlay ?: 0.0)-(opp?.defEpaAllowedPerPlay ?: 0.0))
                + (scoringFactor-1.0)*.40).coerceIn(-0.3,0.3)
            val passRate=passShare(offense)
            var ended=false
            while(!ended && remaining>0 && plays.size<240 && drivePlays<16){
                drivePlays++
                val before=remaining
                val from=yard
                val d=down
                val needed=dist
                var pts=0
                var gained=0
                var type=""
                var description=""
                var actor=""
                var secondary=""
                var turnover=false
                val qb=player(offense,"QB")
                if(down==4 || drivePlays==16){
                    if(yard>=62){
                        val chance=(.52+(yard-62)*.014).coerceIn(.5,.96)
                        if(rng.nextDouble()<chance){
                            pts=3
                            type="FG"
                            description="Gol de campo convertido"
                        }else{
                            type="FG_FALLADO"
                            description="Gol de campo fallado"
                        }
                    }else{
                        type="PUNT"
                        description="Despeje en cuarto down"
                    }
                    remaining-= (8*calibration.clockFactor).toInt().coerceAtLeast(4)
                    ended=true
                }else if(rng.nextDouble()<passRate){
                    val target=player(offense,"TARGET")
                    actor=qb.name
                    secondary=target.name
                    val qbstats=record(qb)
                    val rcstats=record(target)
                    qbstats.passAttempts++
                    rcstats.targets++
                    val roll=rng.nextDouble()
                    val completion=(.60+((m?.successRate ?: .43)-.43)*.25+adjustment*.20).coerceIn(.49,.68)
                    when {
                        roll<.04 -> {
                            type="SACK"; gained=-(2+rng.nextInt(7))
                            description="Captura a ${qb.name}"
                            // Sacks are NOT official passing attempts / targeted passes.
                            qbstats.passAttempts--;rcstats.targets--
                            secondary=""
                        }
                        roll<.055 -> {
                            type="INT"; turnover=true
                            qbstats.interceptions++
                            description="Intercepción en pase de ${qb.name}"
                        }
                        roll<completion -> {
                            type="PASS"; qbstats.completions++;rcstats.receptions++
                            gained=when(rng.nextInt(100)){
                                in 0..19->1+rng.nextInt(4)
                                in 20..74->5+rng.nextInt(8)
                                in 75..95->13+rng.nextInt(12)
                                else->25+rng.nextInt(20)
                            }
                            gained=(gained*(1.0+adjustment*.65)).toInt().coerceIn(1,100-yard)
                            qbstats.passYards+=gained
                            rcstats.receivingYards+=gained
                            description="${qb.name} → ${target.name} · +$gained yd"
                        }
                        else -> {
                            type="INCOMPLETE"
                            description="Pase incompleto de ${qb.name} a ${target.name}"
                        }
                    }
                    remaining-=if(type=="INCOMPLETE") 10+rng.nextInt(12) else 23+rng.nextInt(19)
                    if(type=="SACK")remaining-=0
                }else{
                    val runner=player(offense,"RUSH")
                    actor=runner.name
                    val line=record(runner)
                    line.rushAttempts++
                    val z=rng.nextInt(100)
                    gained=when(z){
                        in 0..12 -> -(1+rng.nextInt(3))
                        in 13..74 -> 2+rng.nextInt(6)
                        in 75..96 -> 8+rng.nextInt(8)
                        else -> 16+rng.nextInt(14)
                    }
                    gained=(gained*(1.0+adjustment*.6)).toInt().coerceAtMost(100-yard)
                    line.rushYards+=gained
                    type="RUN"
                    description="Acarreo de ${runner.name} · ${if(gained>=0)"+" else ""}$gained yd"
                    if(rng.nextDouble()<.008){type="FUMBLE";turnover=true;description="Balón perdido por ${runner.name}"}
                    remaining-=24+rng.nextInt(18)
                }
                // Drive pace is calibrated from eligible prior-week drive observations.
                // Earlier play-time decrement already applied, adjust the actual elapsed time.
                val spent=before-remaining
                remaining=max(0,before-(spent*calibration.clockFactor).toInt().coerceAtLeast(1))
                yard=(yard+gained).coerceIn(1,105)
                if(!ended && !turnover && yard>=100){
                    pts=if(rng.nextDouble()<.94)7 else 6
                    type=if(type=="RUN")"RUSH_TD" else "PASS_TD"
                    description+=" · TOUCHDOWN ($pts pts)"
                    val key=if(type=="RUSH_TD")actor else secondary
                    if(type=="RUSH_TD"){
                        stats.values.firstOrNull{it.team==offense&&it.name==key}?.rushingTds =
                            (stats.values.firstOrNull{it.team==offense&&it.name==key}?.rushingTds ?: 0)+1
                    }else{
                        record(qb).passingTds++
                        stats.values.firstOrNull{it.team==offense&&it.name==key}?.receivingTds =
                            (stats.values.firstOrNull{it.team==offense&&it.name==key}?.receivingTds ?: 0)+1
                    }
                    ended=true
                }
                add(type,description,actor,secondary,gained,pts,d,needed,from,yard,before)
                if(turnover){
                    ended=true
                    lastWasTurnover=true
                    lastSpot=(100-yard).coerceIn(15,85)
                }
                if(!ended){
                    if(gained>=dist){down=1;dist=min(10,100-yard)}
                    else{down++;dist=(dist-gained).coerceIn(1,35)}
                }
            }
            offense=opponent(offense)
        }
        return GameSim(game.gameId,game.season,game.week,game.awayTeam,game.homeTeam,seed,
            awayUsage.throughWeek,homeUsage.throughWeek,awayUsage.players.size,homeUsage.players.size,
            plays,stats.values.toList(),
            listOf(game.awayTeam,game.homeTeam).flatMap { team ->
                val inputs=usage.getValue(team).players
                val starter=starters.getValue(team)
                if(inputs.any{it.id==starter.id})inputs else inputs+starter
            },awayQ.toList(),homeQ.toList(),lineup,calibration)
    }
}
