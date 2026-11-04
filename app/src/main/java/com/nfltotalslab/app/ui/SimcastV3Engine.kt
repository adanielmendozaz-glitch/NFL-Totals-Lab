package com.nfltotalslab.app.ui

import com.nfltotalslab.app.data.TeamMetrics
import com.nfltotalslab.app.data.MatchupFeature
import kotlin.math.*
import kotlin.random.Random

/** Experimental possession model. Every player statistic is derived from the same play stream. */
internal data class V3Play(val away:Boolean,val start:Int,val finish:Int,val down:Int,val distance:Int,
    val elapsed:Int,val kind:String,val yards:Int,val points:Int,val awayScore:Int,val homeScore:Int,
    val passer:String="",val receiver:String="",val receiverPosition:String="",val rusher:String="")
internal data class V3Athlete(val name:String,val position:String,val starter:Boolean,val depth:Int,val unavailable:Boolean)
internal data class V3Prop(val team:String,val player:String,val market:String,val mean:Double,
    val p10:Double,val median:Double,val p90:Double,val sample:Int,val draws:IntArray) {
    fun pOver(line:Double):Double=draws.count{it>line}.toDouble()/draws.size.coerceAtLeast(1)
}
internal data class V43DriveProfile(val drivesPerGame:Double,val playsPerDrive:Double,
    val secondsPerDrive:Double,val secondsPerPlay:Double,val passRate:Double,val sampleGames:Int)
private fun Random.nextGaussianCompat():Double {
    val u1=nextDouble().coerceAtLeast(1e-12)
    return sqrt(-2.0*ln(u1))*cos(2.0*Math.PI*nextDouble())
}

/** Gamma-Poisson mixture (negative binomial) for overdispersed drive opportunity budgets.
 * This is a prior, NOT calibrated 2026 player usage. */
private fun Random.gammaShape(shape:Double):Double {
    require(shape>0.0)
    if(shape<1.0) return gammaShape(shape+1.0)*nextDouble().coerceAtLeast(1e-12).pow(1.0/shape)
    val d=shape-1.0/3.0
    val c=1.0/sqrt(9.0*d)
    while(true) {
        val z=nextGaussianCompat()
        val v0=1.0+c*z
        if(v0<=0.0) continue
        val v=v0*v0*v0
        val u=nextDouble()
        if(u<1.0-0.0331*z*z*z*z || ln(u)<0.5*z*z+d*(1.0-v+ln(v))) return d*v
    }
}
private fun Random.poissonDraw(lambda:Double):Int {
    val lam=lambda.coerceIn(0.01,35.0)
    var product=1.0;var k=0
    val threshold=exp(-lam)
    do { k++;product*=nextDouble().coerceAtLeast(1e-12) } while(product>threshold && k<200)
    return k-1
}
private fun Random.negativeBinomialDraw(mean:Double,dispersion:Double):Int {
    val k=dispersion.coerceAtLeast(0.1)
    return poissonDraw(gammaShape(k)*mean.coerceAtLeast(0.01)/k)
}

internal object SimcastV3Engine {
    private fun pick(r:Random, athletes:List<V3Athlete>, positions:Set<String>,fallback:String):V3Athlete {
        // V4: use a plausible rotation, not the entire historical roster.
        // A roster snapshot is NOT proof of official game-day availability.
        val ranked=athletes.filter { !it.unavailable && it.name.isNotBlank() && it.position.uppercase() in positions }
            .distinctBy { it.name.trim().lowercase() }
            .sortedWith(compareBy<V3Athlete> { !it.starter }.thenBy { it.depth.coerceAtLeast(1) })
        val candidates=when {
            positions==setOf("QB") -> {
                val starters=ranked.filter { it.starter }
                if(starters.size==1) starters else ranked.sortedWith(compareBy<V3Athlete> { it.depth.coerceAtLeast(1) }.thenBy { !it.starter }).take(1)
            }
            positions==setOf("RB","QB") -> ranked.filter { it.position.uppercase()=="RB" }.take(3)
            else -> ranked.filter { it.position.uppercase()=="WR" }.take(5) +
                ranked.filter { it.position.uppercase()=="TE" }.take(2) +
                ranked.filter { it.position.uppercase()=="RB" }.take(3)
        }
        if(candidates.isEmpty())return V3Athlete(fallback,positions.first(),true,1,false)
        val weights=candidates.map { a -> when(a.position.uppercase()) {
            "WR" -> if(a.starter) 5.0 else 1.4
            "TE" -> if(a.starter) 3.0 else 0.9
            "RB" -> if(positions==setOf("RB","QB")) (if(a.starter) 8.0 else 1.5) else (if(a.starter) 2.0 else 0.6)
            else -> 1.0
        } / (1.0 + (a.depth.coerceAtLeast(1)-1)*0.55) }
        val target=r.nextDouble()*weights.sum();var sum=0.0
        for(i in candidates.indices){sum+=weights[i];if(target<=sum)return candidates[i]}
        return candidates.last()
    }
    fun simulate(awayCode:String,homeCode:String,seed:Int,metrics:Map<String,TeamMetrics>,
                 features:List<MatchupFeature>,rosters:Map<String,List<V3Athlete>>,profiles:Map<String,V43DriveProfile> = emptyMap()):List<V3Play>{
        val rng=Random(seed);val plays=ArrayList<V3Play>();var elapsed=0;var away=true
        var awayScore=0;var homeScore=0;var field=25;var down=1;var distance=10
        var drivePlayCount=0
        var driveClock=0
        var driveOpportunityBudget=0
        // V4.5: nflverse drives calibrate clock/tempo, never force illegal punts.
        val featureMap=features.groupBy { it.team }
        fun rate(team:String,key:String):Double? = featureMap[team]?.firstOrNull{it.feature==key && it.sampleN>=20}?.value
        // V4.2 CLOCK ENGINE: situational clock, quarter boundaries
        fun tick(seconds:Int){ elapsed+=min(seconds.coerceAtLeast(1),min(3600-elapsed,900-elapsed%900)) }
        require(profiles.containsKey(awayCode) && profiles.containsKey(homeCode)) {
            "NFLVERSE PROFILE MISSING: refusing generic fallback"
        }
        while(elapsed<3600 && plays.size<260){
            val team=if(away)awayCode else homeCode;val opponent=if(away)homeCode else awayCode
            val off=metrics[team]?.takeIf{it.games>=2};val def=metrics[opponent]?.takeIf{it.games>=2}
            // V5 Phase 1: 2026 team metrics already stored by NFL Totals Lab.
            // Shrink small samples toward neutral league priors; avoid overreacting
            // to a single game. This is a heuristic pending historical calibration.
            val evidence=if(off!=null && def!=null)
                (min(off.games,def.games).toDouble()/(min(off.games,def.games)+8.0)) else 0.0
            val epaEdge=if(off!=null && def!=null)
                (off.offEpaPerPlay-def.defEpaAllowedPerPlay).coerceIn(-.65,.65) else 0.0
            val successEdge=if(off!=null && def!=null)
                (off.successRate-.43).coerceIn(-.20,.20) else 0.0
            val explosiveEdge=if(off!=null && def!=null)
                (off.explosiveRate-.10).coerceIn(-.08,.12) else 0.0
            val edge=(evidence*(epaEdge+.30*successEdge+.35*explosiveEdge)).coerceIn(-.65,.65)
            val turnoverRisk=if(off!=null)
                (.022+(off.turnoverRate-.025)*(off.games.toDouble()/(off.games+8.0))).coerceIn(.008,.05)
                else .022
            // Negative-binomial drive budget: diagnostic prior for opportunities, not a hard stop.
            // A drive can exceed this budget when first downs are earned.
            if(drivePlayCount==0) {
                val expected=(profiles[team]?.playsPerDrive ?: 6.0).coerceIn(3.0,11.0)
                driveOpportunityBudget=rng.negativeBinomialDraw(expected,7.0).coerceIn(2,18)
            }
            val roster=rosters[team].orEmpty();val qb=pick(rng,roster,setOf("QB"),"$team QB")
            val profile=profiles[team]
            val historicalPass=profile?.passRate?.coerceIn(.25,.82)
            val rushShare=(if(historicalPass!=null)1.0-historicalPass else (rate(team,"rush_rate")?:.43)).coerceIn(.18,.75)
            val pass=rng.nextDouble()<(if(down>=3) (1.0-rushShare+.15) else 1.0-rushShare).coerceIn(.25,.9)
            val beforeField=field;val beforeDown=down;val beforeDistance=distance
            // V4.1: decide special teams before generating an offensive play.
            if(down==4){
                val remaining=3600-elapsed
                val gap=if(away)awayScore-homeScore else homeScore-awayScore
                val fgLength=117-field
                val inRange=fgLength<=60
                val short=distance<=2
                val desperation=remaining<=180 && gap<0
                val go=when {
                    desperation && (!inRange || gap<=-4) -> true
                    field>=95 && short -> true
                    field>=50 && short && rng.nextDouble()<.38 -> true
                    else -> false
                }
                if(!go){
                    val kick=if(inRange) "Intento de gol de campo" else "Despeje"
                    val success=inRange && rng.nextDouble()<(.98-.012*(fgLength-30).coerceAtLeast(0)).coerceIn(.25,.98)
                    val points=if(success) 3 else 0
                    tick(rng.nextInt(5,12))
                    if(away)awayScore+=points else homeScore+=points
                    plays+=V3Play(away,field,field,down,distance,elapsed,kick,0,points,awayScore,homeScore)
                    away=!away
                    field=if(inRange && !success) (107-field).coerceIn(20,99) else 25
                    down=1;distance=min(10,100-field);drivePlayCount=0;driveClock=0
                    continue
                }
            }
            val rusher=if(!pass)pick(rng,roster,setOf("RB","QB"),"$team RB") else null
            val target=if(pass)pick(rng,roster,setOf("WR","TE","RB"),"$team WR") else null
            val roll=rng.nextDouble();var yards=0;var kind="";var points=0;var switch=false
            if(pass){
                when {
                    roll<(turnoverRisk-edge*.008).coerceIn(.008,.045)->{kind="Intercepcion";switch=true}
                    roll<(.095-edge*.025).coerceIn(.05,.16)->{kind="Sack";yards=-rng.nextInt(2,11)}
                    roll<(.40-edge*.13).coerceIn(.25,.55)->kind="Pase incompleto"
                    else->{kind="Pase completo";yards=((rng.nextDouble().pow(1.85)*30+1)*(1+edge*.45)).roundToInt().coerceAtLeast(1)}
                }
            } else {
                when {
                    roll<(turnoverRisk*.55).coerceIn(.005,.027)->{kind="Fumble";switch=true}
                    roll<(.19-edge*.07).coerceIn(.09,.28)->{kind="Carrera detenida";yards=-rng.nextInt(0,4)}
                    else->{kind="Carrera";yards=((rng.nextDouble().pow(1.85)*13+1)*(1+edge*.35)).roundToInt().coerceAtLeast(1)}
                }
            }
            // Keep the original play type on scores so individual passing/rushing yards are conserved.
            yards=yards.coerceAtMost(100-field);field=(field+yards).coerceIn(1,100)
            if(field==100){points=if(rng.nextDouble()<.94)7 else 6;switch=true}
            else if(!switch){
                if(yards>=distance){down=1;distance=min(10,100-field)}
                else{down++;distance=(distance-yards).coerceAtLeast(1)}
                if(down>4){
                    // Fourth-down offensive attempt: turnover on downs, not a kick.
                    switch=true
                }
            }
            val scoreGap=if(away)awayScore-homeScore else homeScore-awayScore
            val hurry=(elapsed>=2700 && scoreGap<0) || (elapsed%1800>=1680 && scoreGap<=0)
            // nflverse: drive duration, plays/drive, seconds/play and pass share affect actual simulation.
            val baseSeconds=(profile?.secondsPerPlay ?: 31.0).coerceIn(18.0,45.0)
            val driveBudget=(profile?.secondsPerDrive ?: 175.0).coerceIn(75.0,350.0)
            val targetPlays=(0.7*(profile?.playsPerDrive ?: 6.0)+0.3*driveOpportunityBudget.coerceAtLeast(2)).coerceIn(3.0,11.0)
            val expectedRemaining=(driveBudget-driveClock).coerceAtLeast(5.0)
            val budgetPerPlay=(expectedRemaining/(targetPlays-drivePlayCount).coerceAtLeast(1.0)).coerceIn(8.0,45.0)
            val nominal=(baseSeconds*.65+budgetPerPlay*.35)
            val seconds=when {
                kind=="Pase incompleto"||kind=="Intercepcion" -> rng.nextInt(5,12)
                hurry -> (nominal*.52+rng.nextInt(-3,4)).toInt().coerceIn(8,24)
                else -> (nominal+rng.nextInt(-7,8)).toInt().coerceIn(14,45)
            }
            val clockBefore=elapsed
            tick(seconds)
            driveClock+=elapsed-clockBefore
            drivePlayCount++
            if(away)awayScore+=points else homeScore+=points
            plays+=V3Play(away,beforeField,field,beforeDown,beforeDistance,elapsed,kind,yards,points,awayScore,homeScore,
                if(pass)qb.name else "",if(pass)target!!.name else "",if(pass)target!!.position else "",rusher?.name?:"")
            if(switch){
                val downs=points==0 && beforeDown==4 && kind!="Intercepcion" && kind!="Fumble"
                away=!away
                field=if(kind=="Intercepcion"||kind=="Fumble"||downs)(100-field).coerceIn(1,99) else 25
                down=1;distance=10;drivePlayCount=0;driveClock=0
            }
        }
        return plays
    }
    fun props(away:String,home:String,seed:Int,metrics:Map<String,TeamMetrics>,features:List<MatchupFeature>,
              rosters:Map<String,List<V3Athlete>>,iterations:Int=400,
              profiles:Map<String,V43DriveProfile> = emptyMap()):List<V3Prop>{
        val values=linkedMapOf<Triple<String,String,String>,IntArray>()
        for(i in 0 until iterations){
            val game=simulate(away,home,seed+i*7919,metrics,features,rosters,profiles)
            val totals=linkedMapOf<Triple<String,String,String>,Int>()
            for(p in game){
                val team=if(p.away)away else home
                if(p.passer.isNotBlank() && p.kind=="Pase completo"){
                    val k=Triple(team,p.passer,"PASS_YDS");totals[k]=(totals[k]?:0)+p.yards
                }
                if(p.receiver.isNotBlank() && p.kind=="Pase completo"){
                    val k=Triple(team,p.receiver,"REC_YDS");totals[k]=(totals[k]?:0)+p.yards
                }
                if(p.rusher.isNotBlank() && p.kind.contains("Carrera")){
                    val k=Triple(team,p.rusher,"RUSH_YDS");totals[k]=(totals[k]?:0)+p.yards
                }
            }
            for((k,v) in totals)values.getOrPut(k){IntArray(iterations)}[i]=v
        }
        return values.map{(k,arr)->
            val sorted=arr.sorted();fun q(p:Double)=sorted[((iterations-1)*p).toInt()].toDouble()
            V3Prop(k.first,k.second,k.third,arr.average(),q(.1),q(.5),q(.9),iterations,arr)
        }.sortedWith(compareBy<V3Prop>{it.team}.thenByDescending{it.mean})
    }
}
