import com.nfltotalslab.app.data.*
import com.nfltotalslab.app.simcast.*
import kotlin.math.abs

/** Standalone compiler smoke and invariant regression. Run with tests/test-v16.sh. */
fun main(){
    val game=GameRecord("2026_09_JAX_BAL",2026,9,"REG","2026-11-05","20:15",
        "JAX","BAL",null,null,39.5,-10.0,"outdoors","grass",null,null)
    val away=TeamUsage("JAX",8,listOf(
        UsagePlayer("qb-j","Trevor Lawrence","QB","JAX",77.0,3.0,0.0,45.0,545.0,10.0),
        UsagePlayer("rb-j","Bhayshul Tuten","RB","JAX",0.0,30.0,4.0,0.0,0.0,132.0,3.0,30.0),
        UsagePlayer("wr-j","Parker Washington","WR","JAX",0.0,0.0,13.0,0.0,0.0,0.0,8.0,95.0)))
    val home=TeamUsage("BAL",8,listOf(
        UsagePlayer("qb-b","Joe Fagnano","QB","BAL",21.0,2.0,0.0,12.0,130.0,7.0),
        UsagePlayer("rb-b","Derrick Henry","RB","BAL",0.0,41.0,4.0,0.0,0.0,208.0,3.0,22.0),
        UsagePlayer("wr-b","Zay Flowers","WR","BAL",0.0,0.0,22.0,0.0,0.0,0.0,13.0,139.0)))
    val lineup=GameLineup(TeamLineup("Trevor Lawrence"),TeamLineup("Joe Fagnano",setOf("Tyler Huntley","Lamar Jackson")))
    val mA=TeamMetrics(2026,"JAX",8,450,80,170,drivesPerGame=10.4)
    val mH=TeamMetrics(2026,"BAL",8,420,85,168,drivesPerGame=10.8)
    val cal=PregameSimcastCalibration.build(game,emptyList(),mA,mH)
    val engine=PlayerUsageDriveEngine()
    var overtime=0
    var maxErrors=0
    var minTotal=Int.MAX_VALUE
    var maxTotal=0
    for(seed in 0 until 1000){
        val s=engine.simulate(game,away,home,mA,mH,seed,lineup,cal)
        maxErrors=maxOf(maxErrors,s.integrityErrors.size)
        check(s.integrityErrors.isEmpty()){ "seed=$seed ${s.integrityErrors}" }
        val q3=s.plays.firstOrNull{it.quarter==3}
        check(q3?.kind=="KICKOFF"){ "Q3 must start with kickoff seed=$seed" }
        check(s.plays.none{it.actor in setOf("Tyler Huntley","Lamar Jackson")})
        check(s.awayFinal==s.awayQuarters.sum()+s.awayOvertime)
        check(s.homeFinal==s.homeQuarters.sum()+s.homeOvertime)
        check(s.plays.count{it.kind=="HALFTIME"}==1)
        if(s.overtimePlayed){
            overtime++
            check(s.awayQuarters.sum()==s.homeQuarters.sum())
            check(s.plays.any{it.quarter==5 && it.kind=="OVERTIME_START"})
        }
        val total=s.awayFinal+s.homeFinal
        minTotal=minOf(minTotal,total);maxTotal=maxOf(maxTotal,total)
    }
    for(n in listOf(100,500,1000)){
        val b=SimcastBatchEngine().run(game,away,home,mA,mH,lineup,cal,n,42,true)
        val rerun=SimcastBatchEngine().run(game,away,home,mA,mH,lineup,cal,n,42,true)
        check(b.meanTotal==rerun.meanTotal && b.tieRate==rerun.tieRate)
        check(b.totalHistogram.sumOf{it.second}==n)
        check(abs(b.awayWinRate+b.homeWinRate+b.tieRate-1.0)<1e-9)
        check(abs((b.overRate?:0.0)+(b.underRate?:0.0)+(b.pushRate?:0.0)-1.0)<1e-9)
        check(b.p10<=b.p25 && b.p25<=b.medianTotal && b.medianTotal<=b.p75 && b.p75<=b.p90)
        check(b.replay.integrityErrors.isEmpty())
        println("$n simulations: mean=${"%.2f".format(b.meanTotal)} P(over)=${"%.3f".format(b.overRate)} OT=${"%.3f".format(b.overtimeRate)} final ties=${"%.3f".format(b.tieRate)}")
    }
    println("V1.6 smoke PASS: 1000 individually checked, OT=$overtime, totalMin=$minTotal, totalMax=$maxTotal, maxErrors=$maxErrors")
}
