import com.nfltotalslab.app.data.*
import com.nfltotalslab.app.simcast.*
import kotlin.math.abs

/** Reproducibility and conservative injury/QB impact; NOT external predictive validation. */
fun main(){
    val game=GameRecord("2026_09_CIN_ATL",2026,9,"REG","2026-11-08","08:30",
        "CIN","ATL",null,null,52.5,-3.0,"outdoors","grass",null,null)
    val a=TeamUsage("CIN",8,listOf(
        UsagePlayer("b","Joe Burrow","QB","CIN",60.0,3.0,0.0,43.0,510.0,9.0),
        UsagePlayer("ch","Ja'Marr Chase","WR","CIN",targets=16.0,receptions=12.0,receivingYards=161.0),
        UsagePlayer("rb","Chase Brown","RB","CIN",carries=27.0,targets=5.0,rushingYards=126.0)))
    val h=TeamUsage("ATL",8,listOf(
        UsagePlayer("p","Michael Penix Jr.","QB","ATL",72.0,2.0,0.0,45.0,432.0,8.0),
        UsagePlayer("alt","Backup QB","QB","ATL",20.0,0.0,0.0,14.0,172.0),
        UsagePlayer("wr","Drake London","WR","ATL",targets=15.0,receptions=9.0,receivingYards=124.0),
        UsagePlayer("br","Bijan Robinson","RB","ATL",carries=30.0,targets=5.0,rushingYards=154.0),
        UsagePlayer("out","Zachariah Branch","WR","ATL",targets=5.0,receptions=3.0,receivingYards=31.0)))
    val lineup=GameLineup(TeamLineup("Joe Burrow"),TeamLineup("Michael Penix Jr.",setOf("Zachariah Branch")))
    val cm=TeamMetrics(2026,"CIN",7,425,73,160,tdPerDrive=.25,fgPerDrive=.155,drivesPerGame=10.4)
    val am=TeamMetrics(2026,"ATL",8,450,84,174,tdPerDrive=.21,fgPerDrive=.16,drivesPerGame=10.5)
    val cal=PregameSimcastCalibration.build(game,emptyList(),cm,am)
        .copy(awayGames=7,homeGames=8,leagueGames=121,leaguePpg=22.8,
            awayMatchupPpg=25.02,homeMatchupPpg=22.32,driveTarget=10.7)
    val engine=ScoringShadowEngine()
    for(n in listOf(100,500,1000)){
        val r=engine.run(game,a,h,cm,am,lineup,cal,n,1801)
        val repeat=engine.run(game,a,h,cm,am,lineup,cal,n,1801)
        check(r==repeat){"same seed not reproducible"}
        check(abs(r.pOver!!+r.pUnder!!+r.pPush!!-1.0)<1e-9)
        check(abs(r.awayWinRate+r.homeWinRate+r.tieRate-1.0)<1e-9)
        check(abs(r.meanAway+r.meanHome-r.meanTotal)<1e-9)
        check(r.p10Total<=r.medianTotal && r.medianTotal<=r.p90Total)
        check(r.away.pTouchdown+r.away.pFieldGoal<1.0)
        check(r.home.pTouchdown+r.home.pFieldGoal<1.0)
        println("SCORELAB $n: CIN ${"%.1f".format(r.meanAway)} ATL ${"%.1f".format(r.meanHome)} total ${"%.1f".format(r.meanTotal)} UNDER52.5 ${"%.1f".format(100*r.pUnder)}%")
    }
    // A bookmaker line must affect ONLY the OVER/UNDER comparison, never the score estimate.
    val lowLine=engine.run(game.copy(totalLine=39.5),a,h,cm,am,lineup,cal,1000,1801)
    val highLine=engine.run(game.copy(totalLine=57.5),a,h,cm,am,lineup,cal,1000,1801)
    check(lowLine.meanTotal==highLine.meanTotal && lowLine.meanAway==highLine.meanAway)
    check(lowLine.pOver!!>highLine.pOver!!)
    val absent=engine.parameters("ATL",game,h,am,cm,lineup.home,cal)
    val healthy=engine.parameters("ATL",game,h,am,cm,lineup.home.copy(outNames=emptySet()),cal)
    check(absent.expectedPpg<=healthy.expectedPpg)
    check(absent.offenseOutShare>0.0)
    val backup=engine.parameters("ATL",game,h,am,cm,lineup.home.copy(startingQb="Backup QB"),cal)
    check(abs(backup.expectedPpg-absent.expectedPpg)>0.001){"QB change did not affect expected points"}
    val missing=engine.parameters("ATL",game,h,am,cm,lineup.home.copy(startingQb="QB desconocido"),cal)
    check("prior neutral" in missing.warning)
    println("SCORELAB smoke PASS: injury delta ${"%.2f".format(healthy.expectedPpg-absent.expectedPpg)} QB delta ${"%.2f".format(backup.expectedPpg-absent.expectedPpg)}")
}
