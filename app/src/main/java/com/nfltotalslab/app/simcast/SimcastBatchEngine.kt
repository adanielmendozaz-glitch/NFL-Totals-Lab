package com.nfltotalslab.app.simcast

import com.nfltotalslab.app.data.GameRecord
import com.nfltotalslab.app.data.TeamMetrics
import kotlin.math.pow
import kotlin.math.sqrt

/** No storage of 1,000 full play-by-play streams; only one replay and auditable aggregates. */
data class BatchPlayerLine(
    val team:String,val name:String,val position:String,
    val meanPassYards:Double,val meanRushYards:Double,val meanRecYards:Double,
    val meanAttempts:Double,val meanCarries:Double,val meanTargets:Double,
    val p90PassYards:Int,val p90RushYards:Int,val p90RecYards:Int
)
data class BatchTeamLine(
    val team:String, val meanDrives:Double, val meanPasses:Double,val meanRuns:Double,
    val meanTd:Double,val meanFg:Double,val meanPunts:Double,val meanTurnovers:Double
)
data class ScoreFrequency(val away:Int,val home:Int,val count:Int)
data class BatchReport(
    val gameId:String,val season:Int,val week:Int,val away:String,val home:String,
    val n:Int,val baseSeed:Int,val builtAt:Long,val totalLine:Double?,val pregame:Boolean,
    val calibration:SimcastCalibration,val lineup:GameLineup,
    val awayUsageWeek:Int,val homeUsageWeek:Int,val awayPlayers:Int,val homePlayers:Int,
    val meanTotal:Double,val medianTotal:Double,val sdTotal:Double,
    val p10:Int,val p25:Int,val p75:Int,val p90:Int,
    val meanAway:Double,val meanHome:Double,
    val awayWinRate:Double,val homeWinRate:Double,val tieRate:Double,
    val overRate:Double?,val underRate:Double?,val pushRate:Double?,
    val totalHistogram:List<Pair<Int,Int>>,val mostCommonScores:List<ScoreFrequency>,
    val teamLines:List<BatchTeamLine>,val playerLines:List<BatchPlayerLine>,
    val replay:GameSim, val inputSignature:String="",
    val overtimeRate:Double=0.0, val regulationTieRate:Double=0.0,
    val scoringShadow:ScoringShadowResult?=null
)

data class SimcastBatchAudit(
    val settled:Int=0,val meanAbsoluteError:Double=0.0,
    val brier:Double?=null,val linesGraded:Int=0,val directionAccuracy:Double?=null,
    val shadowSettled:Int=0,val shadowMeanAbsoluteError:Double?=null,
    val shadowBrier:Double?=null,val shadowLinesGraded:Int=0,
    val shadowDirectionAccuracy:Double?=null
)

class SimcastBatchEngine(private val engine:PlayerUsageDriveEngine=PlayerUsageDriveEngine()){
    fun run(
        game:GameRecord,away:TeamUsage,home:TeamUsage,awayMetrics:TeamMetrics?,homeMetrics:TeamMetrics?,
        lineup:GameLineup, calibration:SimcastCalibration, count:Int,seed:Int,pregame:Boolean,
        onProgress:(Int)->Unit={},shouldContinue:()->Boolean={true}, inputSignature:String=""
    ):BatchReport {
        require(count in setOf(100,500,1000)){"Lote admitido: 100, 500 o 1,000"}
        val totals=IntArray(count)
        val awayPoints=IntArray(count)
        val homePoints=IntArray(count)
        val scores=mutableMapOf<Pair<Int,Int>,Int>()
        val histogram=mutableMapOf<Int,Int>()
        val teamAgg=mutableMapOf<String,DoubleArray>()
        // Values are per-run and include zero production for non-participating players.
        val playerYards=linkedMapOf<String,Triple<MutableList<Int>,MutableList<Int>,MutableList<Int>>>()
        val playerOther=linkedMapOf<String,DoubleArray>()
        val playerLabels=linkedMapOf<String,Triple<String,String,String>>()
        var awin=0;var hwin=0;var tie=0;var over=0;var under=0;var push=0;var otCount=0
        var replay:GameSim?=null
        for(i in 0 until count){
            if(!shouldContinue())throw java.util.concurrent.CancellationException("Lote cancelado")
            val s=engine.simulate(game,away,home,awayMetrics,homeMetrics,seed xor ((i+1)*104729),lineup,calibration)
            if(replay==null)replay=s
            // Never count rule-invalid runs as Monte Carlo outcomes.
            check(s.integrityErrors.isEmpty()) { "Simcast invalid: ${s.integrityErrors.joinToString()}" }
            if(s.overtimePlayed)otCount++
            val ap=s.awayFinal;val hp=s.homeFinal;val total=ap+hp
            totals[i]=total;awayPoints[i]=ap;homePoints[i]=hp
            scores[ap to hp]=(scores[ap to hp] ?: 0)+1
            val bucket=(total/5)*5
            histogram[bucket]=(histogram[bucket] ?: 0)+1
            if(ap>hp)awin++ else if(ap<hp)hwin++ else tie++
            game.totalLine?.let{if(total>it)over++ else if(total<it)under++ else push++}
            for(team in listOf(game.awayTeam,game.homeTeam)){
                val p=s.plays.filter{it.offense==team}
                val a=teamAgg.getOrPut(team){DoubleArray(7)}
                a[0]+=p.filter{it.kind in setOf("RUN","RUSH_TD","PASS","PASS_TD", "INCOMPLETE",
                    "SACK","INT","FUMBLE","FG","FG_FALLADO","PUNT")}.map{it.drive}.distinct().size.toDouble()
                a[1]+=p.count{it.kind in setOf("PASS","PASS_TD","INCOMPLETE","INT")}.toDouble()
                a[2]+=p.count{it.kind in setOf("RUN","RUSH_TD","FUMBLE")}.toDouble()
                a[3]+=p.count{it.kind=="PASS_TD"||it.kind=="RUSH_TD"}.toDouble()
                a[4]+=p.count{it.kind=="FG"}.toDouble()
                a[5]+=p.count{it.kind=="PUNT"}.toDouble()
                a[6]+=p.count{it.kind=="INT"||it.kind=="FUMBLE"}.toDouble()
            }
            s.players.forEach { p ->
                val key="${p.team}|${p.name}|${p.position}"
                val y=playerYards.getOrPut(key){Triple(mutableListOf(),mutableListOf(),mutableListOf())}
                val v=playerOther.getOrPut(key){DoubleArray(3)}
                playerLabels[key]=Triple(p.team,p.name,p.position)
                // Avoid filling missing prior zeros on every simulation: backfill on first appearance.
                while(y.first.size<i){y.first.add(0);y.second.add(0);y.third.add(0)}
                y.first.add(p.passYards);y.second.add(p.rushYards);y.third.add(p.receivingYards)
                v[0]+=p.passAttempts.toDouble();v[1]+=p.rushAttempts.toDouble();v[2]+=p.targets.toDouble()
            }
            // Previously seen players absent this run must get a zero, not be omitted.
            playerYards.values.forEach{ y->if(y.first.size==i){y.first.add(0);y.second.add(0);y.third.add(0)}}
            if((i+1)%25==0 || i==count-1)onProgress(i+1)
        }
        fun mean(a:IntArray)=a.average()
        val sorted=totals.sorted()
        fun pct(p:Double):Int=sorted[((count-1)*p).toInt().coerceIn(0,count-1)]
        val mu=mean(totals)
        val sd=sqrt(totals.sumOf{(it-mu).pow(2.0)}/count)
        val teamLines=teamAgg.map{(team,v)->BatchTeamLine(team,v[0]/count,v[1]/count,v[2]/count,
            v[3]/count,v[4]/count,v[5]/count,v[6]/count)}
        val playerLines=playerYards.map{(key,y)->
            val meta=playerLabels.getValue(key)
            val v=playerOther.getValue(key)
            fun p90(data:List<Int>)=data.sorted()[((count-1)*.90).toInt()]
            BatchPlayerLine(meta.first,meta.second,meta.third,y.first.average(),y.second.average(),y.third.average(),
                v[0]/count,v[1]/count,v[2]/count,p90(y.first),p90(y.second),p90(y.third))
        }.sortedWith(compareBy<BatchPlayerLine>{it.team}.thenByDescending{it.meanPassYards+it.meanRushYards+it.meanRecYards})
        return BatchReport(game.gameId,game.season,game.week,game.awayTeam,game.homeTeam,count,seed,
            System.currentTimeMillis(),game.totalLine,pregame,calibration,lineup,
            away.throughWeek,home.throughWeek,away.players.size,home.players.size,
            mu,pct(.50).toDouble(),sd,pct(.10),pct(.25),pct(.75),pct(.90),mean(awayPoints),mean(homePoints),
            awin.toDouble()/count,hwin.toDouble()/count,tie.toDouble()/count,
            if(game.totalLine!=null)over.toDouble()/count else null,
            if(game.totalLine!=null)under.toDouble()/count else null,
            if(game.totalLine!=null)push.toDouble()/count else null,
            histogram.toSortedMap().map{it.key to it.value},
            scores.entries.sortedByDescending{it.value}.take(10).map{ScoreFrequency(it.key.first,it.key.second,it.value)},
            teamLines,playerLines,replay!!,inputSignature,otCount.toDouble()/count,otCount.toDouble()/count)
    }
}
