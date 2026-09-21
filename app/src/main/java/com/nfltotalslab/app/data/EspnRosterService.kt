package com.nfltotalslab.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

class EspnRosterService {
    private val teamIdCache=linkedMapOf<String,String>()

    private data class InjuryInfo(
        val name:String,
        val status:String,
        val detail:String
    )

    suspend fun fetchGameIntelligence(game:GameRecord):GameRosterIntelligence = coroutineScope {
        val awayJob=async(Dispatchers.IO){fetchTeamIntelligence(game.awayTeam,game.season)}
        val homeJob=async(Dispatchers.IO){fetchTeamIntelligence(game.homeTeam,game.season)}
        val away=awayJob.await()
        val home=homeJob.await()

        val decisionReady=
            away.depthLoaded && home.depthLoaded &&
            away.injuriesLoaded && home.injuriesLoaded

        val reliability=if(decisionReady){
            listOf(teamReliability(away),teamReliability(home)).average()
        }else 0.0

        val raw=-away.offensePenalty-home.offensePenalty+away.defenseLeak+home.defenseLeak
        val adjustment=if(decisionReady){
            (raw*.55*reliability).coerceIn(-6.0,6.0)
        }else 0.0

        val reason=when{
            !away.depthLoaded && !home.depthLoaded -> "Depth chart incompleto para ambos equipos."
            !away.depthLoaded -> "Depth chart incompleto para ${away.team}."
            !home.depthLoaded -> "Depth chart incompleto para ${home.team}."
            !away.injuriesLoaded && !home.injuriesLoaded -> "Injury report no disponible para ambos equipos."
            !away.injuriesLoaded -> "Injury report no disponible para ${away.team}."
            !home.injuriesLoaded -> "Injury report no disponible para ${home.team}."
            else -> "Depth charts + injury reports completos."
        }

        val fingerprint=(away.players+home.players)
            .filter{it.starter || it.injuryStatus.isNotBlank()}
            .sortedWith(compareBy<RosterPlayerState>{it.unit}.thenBy{it.position}.thenBy{it.name})
            .joinToString(";"){
                "${it.name}:${it.position}:${it.depthRank}:${it.injuryStatus}:${it.injuryDetail}"
            }.let{if(it.length<=1800)it else it.take(1800)}

        GameRosterIntelligence(
            away=away,
            home=home,
            totalAdjustment=adjustment,
            reliability=reliability,
            decisionReady=decisionReady,
            qualityReason=reason,
            source="ESPN site/core depth chart + roster fallback + injury report",
            fingerprint=fingerprint
        )
    }

    private suspend fun fetchTeamIntelligence(team:String,season:Int):TeamRosterIntelligence =
        withContext(Dispatchers.IO){
            val id=resolveTeamId(team) ?: return@withContext emptyTeam(team)

            val siteDepth=runCatching{
                JSONObject(readAll("https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/$id/depthcharts"))
            }
            val siteDepthPlayers=siteDepth.getOrNull()?.let{parseSiteDepthChart(it)} ?: emptyList()

            val coreDepth=if(siteDepthPlayers.isEmpty()){
                runCatching{
                    JSONObject(readAll("https://sports.core.api.espn.com/v2/sports/football/leagues/nfl/seasons/$season/teams/$id/depthcharts"))
                }
            }else null
            val coreDepthPlayers=coreDepth?.getOrNull()?.let{parseCoreDepthChart(it)} ?: emptyList()

            val depthPlayers=when{
                siteDepthPlayers.isNotEmpty()->siteDepthPlayers
                coreDepthPlayers.isNotEmpty()->coreDepthPlayers
                else->emptyList()
            }

            val rosterResult=runCatching{
                JSONObject(readAll("https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/$id/roster"))
            }
            val rosterPlayers=rosterResult.getOrNull()?.let{parseRoster(it)} ?: emptyList()

            val injuryResult=runCatching{
                JSONObject(readAll("https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/$id/injuries"))
            }
            val injuries=injuryResult.getOrNull()?.let{parseInjuries(it)} ?: emptyMap()

            val basePlayers=if(depthPlayers.isNotEmpty())depthPlayers else rosterPlayers
            val merged=basePlayers.map{p->
                val inj=injuries[normalizeName(p.name)]
                if(inj==null)p else p.copy(injuryStatus=inj.status,injuryDetail=inj.detail)
            }.toMutableList()

            injuries.forEach{(key,inj)->
                if(merged.none{normalizeName(it.name)==key}){
                    merged+=RosterPlayerState(
                        name=inj.name,
                        position="—",
                        unit="OTHER",
                        depthRank=99,
                        injuryStatus=inj.status,
                        injuryDetail=inj.detail
                    )
                }
            }

            val offenseStarters=depthPlayers.count{it.unit=="OFFENSE" && it.starter}
            val defenseStarters=depthPlayers.count{it.unit=="DEFENSE" && it.starter}
            val depthLoaded=
                depthPlayers.isNotEmpty() &&
                offenseStarters>=8 &&
                defenseStarters>=8

            val offense=if(depthLoaded)merged.filter{it.unit=="OFFENSE"} else emptyList()
            val defense=if(depthLoaded)merged.filter{it.unit=="DEFENSE"} else emptyList()
            val offPenalty=offense.sumOf{impactPoints(it)}
            val defLeak=defense.sumOf{impactPoints(it)}
            val starters=if(depthLoaded)merged.filter{it.starter} else emptyList()

            TeamRosterIntelligence(
                team=team,
                players=merged.sortedWith(
                    compareBy<RosterPlayerState>{
                        when(it.unit){"OFFENSE"->0;"DEFENSE"->1;else->2}
                    }.thenBy{it.position}.thenBy{it.depthRank}.thenBy{it.name}
                ),
                offenseAvailability=if(depthLoaded)
                    (100.0-offPenalty*8.0).coerceIn(45.0,100.0)
                else 0.0,
                defenseAvailability=if(depthLoaded)
                    (100.0-defLeak*8.0).coerceIn(45.0,100.0)
                else 0.0,
                offensePenalty=offPenalty,
                defenseLeak=defLeak,
                startersTotal=starters.size,
                startersAvailable=starters.count{severity(it.injuryStatus)<.75},
                outCount=merged.count{severity(it.injuryStatus)>=.75},
                questionableCount=merged.count{
                    it.injuryStatus.contains("QUESTION",true) ||
                    it.injuryStatus.contains("DOUBT",true)
                },
                depthLoaded=depthLoaded,
                injuriesLoaded=injuryResult.isSuccess,
                rosterLoaded=rosterResult.isSuccess && rosterPlayers.isNotEmpty(),
                depthSource=when{
                    siteDepthPlayers.isNotEmpty()->"ESPN SITE"
                    coreDepthPlayers.isNotEmpty()->"ESPN CORE"
                    rosterPlayers.isNotEmpty()->"ROSTER FALLBACK"
                    else->"SIN DEPTH"
                }
            )
        }

    private fun parseSiteDepthChart(root:JSONObject):List<RosterPlayerState> =
        parseDepthGroups(root.optJSONArray("depthCharts"))

    private fun parseCoreDepthChart(root:JSONObject):List<RosterPlayerState> =
        parseDepthGroups(root.optJSONArray("items"))

    private fun parseDepthGroups(groups:JSONArray?):List<RosterPlayerState>{
        val out=mutableListOf<RosterPlayerState>()
        if(groups==null)return out
        for(i in 0 until groups.length()){
            val group=groups.optJSONObject(i) ?: continue
            val groupName=group.optString("name","")
            val positions=group.optJSONObject("positions") ?: continue
            val keys=positions.keys()
            while(keys.hasNext()){
                val key=keys.next()
                val posObj=positions.optJSONObject(key) ?: continue
                val pos=posObj.optJSONObject("position")?.optString("abbreviation","")
                    ?.ifBlank{key.uppercase()} ?: key.uppercase()
                val unit=unitForPosition(pos,groupName)
                val athletes=posObj.optJSONArray("athletes") ?: continue
                for(j in 0 until athletes.length()){
                    val slot=athletes.optJSONObject(j) ?: continue
                    val athlete=slot.optJSONObject("athlete") ?: continue
                    val name=athlete.optString("displayName",athlete.optString("fullName","")).trim()
                    if(name.isBlank())continue
                    out+=RosterPlayerState(
                        name=name,
                        position=pos,
                        unit=unit,
                        depthRank=max(1,slot.optInt("rank",j+1))
                    )
                }
            }
        }
        return out.distinctBy{"${it.unit}|${it.position}|${it.name}"}
    }

    private fun parseRoster(root:JSONObject):List<RosterPlayerState>{
        val out=mutableListOf<RosterPlayerState>()
        val groups=root.optJSONArray("athletes") ?: return out
        for(i in 0 until groups.length()){
            val group=groups.optJSONObject(i) ?: continue
            val groupPosition=group.optString("position","")
            val items=group.optJSONArray("items") ?: continue
            for(j in 0 until items.length()){
                val athlete=items.optJSONObject(j) ?: continue
                val name=athlete.optString("displayName",athlete.optString("fullName","")).trim()
                if(name.isBlank())continue
                val pos=athlete.optJSONObject("position")
                    ?.optString("abbreviation","")
                    ?.ifBlank{groupPosition}
                    ?: groupPosition
                out+=RosterPlayerState(
                    name=name,
                    position=pos.ifBlank{"—"},
                    unit=unitForPosition(pos,groupPosition),
                    depthRank=99
                )
            }
        }
        return out.distinctBy{"${it.position}|${it.name}"}
    }

    private fun parseInjuries(root:JSONObject):Map<String,InjuryInfo>{
        val out=linkedMapOf<String,InjuryInfo>()
        fun consume(arr:JSONArray){
            for(i in 0 until arr.length()){
                val item=arr.optJSONObject(i) ?: continue
                val nested=item.optJSONArray("injuries")
                if(nested!=null){
                    consume(nested)
                    continue
                }
                val athlete=item.optJSONObject("athlete")
                val name=athlete?.let{
                    it.optString("displayName",it.optString("fullName",""))
                }?.trim().orEmpty()
                if(name.isBlank())continue

                val status=when(val raw=item.opt("status")){
                    is String -> raw
                    is JSONObject -> raw.optString("description",raw.optString("name",""))
                    else -> ""
                }.ifBlank{item.optString("statusDescription","")}

                val detail=item.optJSONObject("type")
                    ?.optString("description","")
                    ?.ifBlank{item.optString("details","")}
                    ?: item.optString("details","")

                out[normalizeName(name)]=InjuryInfo(name,status,detail)
            }
        }
        root.optJSONArray("injuries")?.let{consume(it)}
        return out
    }

    private fun unitForPosition(position:String,groupName:String):String{
        val p=position.uppercase()
        val g=groupName.lowercase()
        val offense=setOf(
            "QB","RB","HB","FB","WR","LWR","RWR","SLOT","TE",
            "LT","RT","OT","T","LG","RG","G","C","OL"
        )
        val defense=setOf(
            "DE","EDGE","DT","NT","DL","OLB","ILB","MLB","LB",
            "CB","LCB","RCB","NB","S","FS","SS","DB"
        )
        return when{
            p in offense->"OFFENSE"
            p in defense->"DEFENSE"
            g.contains("off")->"OFFENSE"
            g.contains("def") || g.contains("base") ||
                g.contains("nickel") || g.contains("dime")->"DEFENSE"
            else->"SPECIAL"
        }
    }

    private fun impactPoints(p:RosterPlayerState):Double{
        val sev=severity(p.injuryStatus)
        if(sev<=0.0)return 0.0
        val depth=when{
            p.depthRank==1->1.0
            p.depthRank==2->.40
            p.depthRank==3->.20
            else->.10
        }
        return sev*depth*positionWeight(p.position,p.unit)
    }

    private fun severity(status:String):Double{
        val s=status.uppercase()
        return when{
            s.isBlank()->0.0
            s.contains("OUT")->1.0
            s.contains("INJURED RESERVE") || s=="IR" || s.contains("PUP")->1.0
            s.contains("SUSP")->1.0
            s.contains("DOUBT")->.85
            s.contains("QUESTION")->.35
            s.contains("PROB")->.12
            else->.08
        }
    }

    private fun positionWeight(position:String,unit:String):Double{
        val p=position.uppercase()
        return if(unit=="OFFENSE"){
            when{
                p=="QB"->4.0
                p in setOf("LT","RT","OT","T","LG","RG","G","C","OL")->1.35
                p in setOf("WR","LWR","RWR","SLOT")->1.05
                p=="TE"->.75
                p in setOf("RB","HB","FB")->.65
                else->.35
            }
        }else if(unit=="DEFENSE"){
            when{
                p in setOf("CB","LCB","RCB","NB")->1.10
                p in setOf("DE","EDGE","OLB")->1.00
                p in setOf("S","FS","SS")->.85
                p in setOf("LB","ILB","MLB")->.80
                p in setOf("DT","NT","DL")->.70
                else->.40
            }
        }else .20
    }

    private fun teamReliability(t:TeamRosterIntelligence):Double =
        if(t.depthLoaded && t.injuriesLoaded).90 else 0.0

    private fun emptyTeam(team:String)=TeamRosterIntelligence(
        team=team,
        players=emptyList(),
        offenseAvailability=0.0,
        defenseAvailability=0.0,
        offensePenalty=0.0,
        defenseLeak=0.0,
        startersTotal=0,
        startersAvailable=0,
        outCount=0,
        questionableCount=0,
        depthLoaded=false,
        injuriesLoaded=false,
        rosterLoaded=false,
        depthSource="SIN DATOS"
    )

    private fun normalizeName(raw:String)=raw.lowercase()
        .replace(Regex("[^a-z0-9 ]"),"")
        .replace(Regex("\s+")," ")
        .trim()

    private fun resolveTeamId(team:String):String?{
        val normalized=normalizeTeam(team)
        teamIdCache[normalized]?.let{return it}
        val root=JSONObject(readAll(
            "https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams?limit=50"
        ))
        val sports=root.optJSONArray("sports") ?: return null
        val leagues=sports.optJSONObject(0)?.optJSONArray("leagues") ?: return null
        val teams=leagues.optJSONObject(0)?.optJSONArray("teams") ?: return null
        for(i in 0 until teams.length()){
            val teamObj=teams.optJSONObject(i)?.optJSONObject("team") ?: continue
            val abbr=normalizeTeam(teamObj.optString("abbreviation",""))
            val id=teamObj.optString("id","")
            if(abbr.isNotBlank() && id.isNotBlank())teamIdCache[abbr]=id
        }
        return teamIdCache[normalized]
    }

    private fun normalizeTeam(raw:String)=when(raw.uppercase()){
        "LAR"->"LA"
        "WSH"->"WAS"
        else->raw.uppercase()
    }

    private fun readAll(url:String):String{
        val c=(URL(url).openConnection() as HttpURLConnection).apply{
            requestMethod="GET"
            connectTimeout=12000
            readTimeout=25000
            instanceFollowRedirects=true
            setRequestProperty("User-Agent","NFL-Totals-Lab-Android/0.9.2.1")
            connect()
            if(responseCode !in 200..299){
                throw IllegalStateException("HTTP $responseCode en ESPN roster")
            }
        }
        return try{
            c.inputStream.bufferedReader(Charsets.UTF_8).use{it.readText()}
        }finally{c.disconnect()}
    }
}
