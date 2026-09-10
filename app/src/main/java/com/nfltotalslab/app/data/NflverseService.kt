package com.nfltotalslab.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlin.math.max

class NflverseService {
    private val schedulesUrl = "https://github.com/nflverse/nflverse-data/releases/download/schedules/games.csv"

    suspend fun fetchSchedule(season: Int): List<GameRecord> = withContext(Dispatchers.IO) {
        openText(schedulesUrl).use { br ->
            val headers = Csv.parseLine(br.readLine())
            val ix = mapOf(
                "gameId" to Csv.index(headers,"game_id"),
                "season" to Csv.index(headers,"season"),
                "week" to Csv.index(headers,"week"),
                "type" to Csv.index(headers,"game_type"),
                "day" to Csv.index(headers,"gameday"),
                "time" to Csv.index(headers,"gametime"),
                "away" to Csv.index(headers,"away_team"),
                "home" to Csv.index(headers,"home_team"),
                "as" to Csv.index(headers,"away_score"),
                "hs" to Csv.index(headers,"home_score"),
                "total" to Csv.index(headers,"total_line"),
                "spread" to Csv.index(headers,"spread_line"),
                "roof" to Csv.index(headers,"roof"),
                "surface" to Csv.index(headers,"surface"),
                "temp" to Csv.index(headers,"temp"),
                "wind" to Csv.index(headers,"wind")
            )
            buildList {
                br.lineSequence().forEach { line ->
                    val r=Csv.parseLine(line)
                    val s=Csv.value(r,ix["season"]!!)?.toIntOrNull() ?: return@forEach
                    if(s != season) return@forEach
                    val away=Csv.value(r,ix["away"]!!) ?: return@forEach
                    val home=Csv.value(r,ix["home"]!!) ?: return@forEach
                    add(GameRecord(
                        gameId=Csv.value(r,ix["gameId"]!!) ?: "${season}_${Csv.value(r,ix["week"]!!) ?: 0}_${away}_${home}",
                        season=s,
                        week=Csv.value(r,ix["week"]!!)?.toIntOrNull() ?: 0,
                        gameType=Csv.value(r,ix["type"]!!) ?: "REG",
                        gameDay=Csv.value(r,ix["day"]!!) ?: "",
                        gameTime=Csv.value(r,ix["time"]!!) ?: "",
                        awayTeam=away, homeTeam=home,
                        awayScore=Csv.value(r,ix["as"]!!)?.toIntOrNull(),
                        homeScore=Csv.value(r,ix["hs"]!!)?.toIntOrNull(),
                        totalLine=Csv.value(r,ix["total"]!!)?.toDoubleOrNull(),
                        spreadLine=Csv.value(r,ix["spread"]!!)?.toDoubleOrNull(),
                        roof=Csv.value(r,ix["roof"]!!), surface=Csv.value(r,ix["surface"]!!),
                        temp=Csv.value(r,ix["temp"]!!)?.toDoubleOrNull(),
                        wind=Csv.value(r,ix["wind"]!!)?.toDoubleOrNull()
                    ))
                }
            }
        }
    }

    suspend fun fetchLiveScores(season:Int, week:Int): Map<String,LiveGameState> = withContext(Dispatchers.IO) {
        val url="https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?limit=100&dates=$season&seasontype=2&week=$week"
        val root=JSONObject(readAll(url))
        val events=root.optJSONArray("events") ?: return@withContext emptyMap()
        val out=linkedMapOf<String,LiveGameState>()

        for(i in 0 until events.length()){
            val event=events.optJSONObject(i) ?: continue
            val competitions=event.optJSONArray("competitions") ?: continue
            val competition=competitions.optJSONObject(0) ?: continue
            val competitors=competition.optJSONArray("competitors") ?: continue

            var awayTeam:String?=null
            var homeTeam:String?=null
            var awayScore:Int?=null
            var homeScore:Int?=null

            for(j in 0 until competitors.length()){
                val c=competitors.optJSONObject(j) ?: continue
                val team=c.optJSONObject("team")
                val abbr=normalizeTeam(team?.optString("abbreviation","") ?: "")
                val score=c.optString("score","").toIntOrNull()
                when(c.optString("homeAway","")){
                    "away" -> { awayTeam=abbr; awayScore=score }
                    "home" -> { homeTeam=abbr; homeScore=score }
                }
            }

            val a=awayTeam ?: continue
            val h=homeTeam ?: continue
            val status=competition.optJSONObject("status") ?: event.optJSONObject("status")
            val type=status?.optJSONObject("type")
            val state=type?.optString("state","pre") ?: "pre"
            val detail=type?.optString("shortDetail","")
                ?.ifBlank { type?.optString("detail","") ?: "" } ?: ""
            val clock=status?.optString("displayClock","") ?: ""
            val period=status?.optInt("period",0) ?: 0

            val live=LiveGameState(
                awayTeam=a,
                homeTeam=h,
                awayScore=awayScore ?: 0,
                homeScore=homeScore ?: 0,
                period=period,
                clock=clock,
                detail=detail,
                state=state,
                updatedAt=System.currentTimeMillis()
            )
            out[live.matchKey]=live
        }
        out
    }

    private fun normalizeTeam(raw:String):String = when(raw.uppercase()){
        "LAR" -> "LA"
        "WSH" -> "WAS"
        else -> raw.uppercase()
    }

    private data class Agg(
        var offPlays:Int=0, var offEpa:Double=0.0, var success:Int=0, var explosive:Int=0,
        var turnovers:Int=0, var tds:Int=0, var fgs:Int=0, var defPlays:Int=0, var defEpa:Double=0.0,
        val games:MutableSet<String> = linkedSetOf(), val drives:MutableSet<String> = linkedSetOf()
    )

    suspend fun fetchPbpMetrics(season: Int): Map<String,TeamMetrics> = withContext(Dispatchers.IO) {
        val url="https://github.com/nflverse/nflverse-data/releases/download/pbp/play_by_play_${season}.csv.gz"
        val conn=open(url)
        GZIPInputStream(conn.inputStream).bufferedReader().use { br ->
            val headers=Csv.parseLine(br.readLine())
            val iPost=Csv.index(headers,"posteam")
            val iDef=Csv.index(headers,"defteam")
            val iEpa=Csv.index(headers,"epa")
            val iSuccess=Csv.index(headers,"success")
            val iYards=Csv.index(headers,"yards_gained")
            val iInt=Csv.index(headers,"interception")
            val iFum=Csv.index(headers,"fumble_lost")
            val iTd=Csv.index(headers,"touchdown")
            val iFg=Csv.index(headers,"field_goal_result")
            val iGame=Csv.index(headers,"game_id")
            val iDrive=Csv.index(headers,"drive")
            val map=linkedMapOf<String,Agg>()

            br.lineSequence().forEach { line ->
                val r=Csv.parseLine(line)
                val post=Csv.value(r,iPost)
                val def=Csv.value(r,iDef)
                val epa=Csv.value(r,iEpa)?.toDoubleOrNull()
                val game=Csv.value(r,iGame)
                val drive=Csv.value(r,iDrive)

                if(post!=null && epa!=null){
                    val a=map.getOrPut(post){Agg()}
                    a.offPlays++; a.offEpa+=epa
                    if(Csv.value(r,iSuccess)?.toDoubleOrNull()?.let{it>=0.5}==true)a.success++
                    if((Csv.value(r,iYards)?.toDoubleOrNull() ?: 0.0)>=20.0)a.explosive++
                    if((Csv.value(r,iInt)?.toIntOrNull() ?: 0)==1 || (Csv.value(r,iFum)?.toIntOrNull() ?: 0)==1)a.turnovers++
                    if((Csv.value(r,iTd)?.toIntOrNull() ?: 0)==1)a.tds++
                    if(Csv.value(r,iFg)?.equals("made",true)==true)a.fgs++
                    if(game!=null)a.games+=game
                    if(game!=null && drive!=null)a.drives+="$game:$drive"
                }
                if(def!=null && epa!=null){
                    val a=map.getOrPut(def){Agg()}
                    a.defPlays++; a.defEpa+=epa
                }
            }

            map.mapValues { (team,a) ->
                val g=max(1,a.games.size); val d=max(1,a.drives.size); val p=max(1,a.offPlays)
                TeamMetrics(
                    season=season, team=team, games=a.games.size, plays=a.offPlays, drives=a.drives.size,
                    offEpaPerPlay=a.offEpa/max(1,a.offPlays),
                    defEpaAllowedPerPlay=a.defEpa/max(1,a.defPlays),
                    successRate=a.success.toDouble()/p,
                    explosiveRate=a.explosive.toDouble()/p,
                    turnoverRate=a.turnovers.toDouble()/p,
                    tdPerDrive=a.tds.toDouble()/d,
                    fgPerDrive=a.fgs.toDouble()/d,
                    drivesPerGame=a.drives.size.toDouble()/g
                )
            }
        }.also { conn.disconnect() }
    }

    suspend fun fetchRosterCounts(season:Int): Pair<Map<String,Int>,Int> = withContext(Dispatchers.IO) {
        val url="https://github.com/nflverse/nflverse-data/releases/download/rosters/roster_${season}.csv"
        countByTeam(url, listOf("team","team_abbr","recent_team"))
    }

    suspend fun fetchInjuryCounts(season:Int): Pair<Map<String,Int>,Int> = withContext(Dispatchers.IO) {
        val url="https://github.com/nflverse/nflverse-data/releases/download/injuries/injuries_${season}.csv"
        countByTeam(url, listOf("team","team_abbr","recent_team"))
    }

    private fun countByTeam(url:String, teamHeaders:List<String>):Pair<Map<String,Int>,Int>{
        openText(url).use { br ->
            val headers=Csv.parseLine(br.readLine())
            val iTeam=teamHeaders.asSequence().map{Csv.index(headers,it)}.firstOrNull{it>=0} ?: -1
            val m=linkedMapOf<String,Int>(); var rows=0
            br.lineSequence().forEach { line ->
                val r=Csv.parseLine(line); rows++
                val team=Csv.value(r,iTeam)
                if(team!=null)m[team]=(m[team]?:0)+1
            }
            return m to rows
        }
    }

    private fun readAll(url:String):String{
        val c=open(url)
        return try{
            c.inputStream.bufferedReader(Charsets.UTF_8).use{it.readText()}
        }finally{
            c.disconnect()
        }
    }

    private fun openText(url:String):BufferedReader{
        val c=open(url)
        return object:BufferedReader(InputStreamReader(c.inputStream,Charsets.UTF_8)){
            override fun close(){ super.close(); c.disconnect() }
        }
    }

    private fun open(url:String):HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod="GET"; connectTimeout=15000; readTimeout=120000
            instanceFollowRedirects=true
            setRequestProperty("User-Agent","NFL-Totals-Lab-Android/0.4.1")
            connect()
            if(responseCode !in 200..299) throw IllegalStateException("HTTP $responseCode en $url")
        }
    }
}
