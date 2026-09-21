package com.nfltotalslab.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

/**
 * Roster/Depth resolver V2.
 *
 * ESPN Core depth charts frequently return athlete objects as {"$ref":"..."}
 * rather than embedding displayName/fullName. This resolver first maps those
 * refs to the already-fetched team roster by athlete id; only unresolved refs
 * are fetched directly from ESPN Core.
 */
class EspnRosterService {
    private val teamIdCache=linkedMapOf<String,String>()
    private val athleteCache=linkedMapOf<String,AthleteIdentity>()

    private data class InjuryInfo(
        val name:String,
        val status:String,
        val detail:String
    )

    private data class InjuryParseResult(
        val items:Map<String,InjuryInfo>,
        val rawCount:Int,
        val parsedCount:Int,
        val schemaRecognized:Boolean
    )


    private data class AthleteIdentity(
        val id:String,
        val name:String,
        val position:String
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
                "${it.athleteId}:${it.name}:${it.position}:${it.depthRank}:${it.injuryStatus}:${it.injuryDetail}"
            }.let{if(it.length<=2200)it else it.take(2200)}

        GameRosterIntelligence(
            away=away,
            home=home,
            totalAdjustment=adjustment,
            reliability=reliability,
            decisionReady=decisionReady,
            qualityReason=reason,
            source="ESPN Site/Core depth + athlete-ref resolver + roster + injuries",
            fingerprint=fingerprint
        )
    }

    private suspend fun fetchTeamIntelligence(team:String,season:Int):TeamRosterIntelligence =
        withContext(Dispatchers.IO){
            val id=resolveTeamId(team) ?: return@withContext emptyTeam(team)

            // Fetch roster early: it is our fastest athlete-id -> name dictionary.
            val rosterResult=runCatching{
                JSONObject(readAll(
                    "https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/$id/roster"
                ))
            }
            val rosterPlayers=rosterResult.getOrNull()?.let{parseRoster(it)} ?: emptyList()
            val rosterById=rosterPlayers
                .filter{it.athleteId.isNotBlank()}
                .associateBy{it.athleteId}

            val siteDepthResult=runCatching{
                JSONObject(readAll(
                    "https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/$id/depthcharts"
                ))
            }
            val siteDepthPlayers=siteDepthResult.getOrNull()
                ?.let{parseSiteDepthChart(it)}
                ?: emptyList()

            val coreDepthResult=if(siteDepthPlayers.isEmpty()){
                runCatching{
                    JSONObject(readAll(
                        "https://sports.core.api.espn.com/v2/sports/football/leagues/nfl/seasons/$season/teams/$id/depthcharts"
                    ))
                }
            }else null

            val coreDepthPlayers=if(coreDepthResult?.isSuccess==true){
                parseCoreDepthChart(
                    root=coreDepthResult.getOrThrow(),
                    rosterById=rosterById
                )
            }else emptyList()

            val depthPlayers=when{
                siteDepthPlayers.isNotEmpty()->siteDepthPlayers
                coreDepthPlayers.isNotEmpty()->coreDepthPlayers
                else->emptyList()
            }

            val injuryResult=runCatching{
                JSONObject(readAll(
                    "https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/$id/injuries"
                ))
            }

            val injuryParsed=if(injuryResult.isSuccess){
                parseInjuries(injuryResult.getOrThrow(),rosterById)
            }else{
                InjuryParseResult(emptyMap(),0,0,false)
            }

            val parseRatio=if(injuryParsed.rawCount==0)1.0
                else injuryParsed.parsedCount.toDouble()/injuryParsed.rawCount.toDouble()

            val injuriesValid=
                injuryResult.isSuccess &&
                injuryParsed.schemaRecognized &&
                (
                    injuryParsed.rawCount==0 ||
                    (injuryParsed.parsedCount>0 && parseRatio>=.60)
                )

            val injuryState=when{
                injuryResult.isFailure -> "ENDPOINT ERROR"
                !injuryParsed.schemaRecognized -> "SCHEMA NO RECONOCIDO"
                injuryParsed.rawCount==0 -> "VALID EMPTY 0/0"
                injuriesValid -> "VALID ${injuryParsed.parsedCount}/${injuryParsed.rawCount}"
                else -> "PARTIAL ${injuryParsed.parsedCount}/${injuryParsed.rawCount}"
            }

            val injuries=injuryParsed.items
            val rosterByName=rosterPlayers.associateBy{normalizeName(it.name)}

            val basePlayers=if(depthPlayers.isNotEmpty())depthPlayers else rosterPlayers
            val merged=basePlayers.map{p->
                val inj=injuries[normalizeName(p.name)]
                if(inj==null)p else p.copy(
                    injuryStatus=inj.status,
                    injuryDetail=inj.detail
                )
            }.toMutableList()

            injuries.forEach{(key,inj)->
                if(merged.none{normalizeName(it.name)==key}){
                    val rosterHit=rosterByName[key]
                    merged+=RosterPlayerState(
                        name=inj.name,
                        position=rosterHit?.position ?: "—",
                        unit=rosterHit?.unit ?: "OTHER",
                        depthRank=99,
                        athleteId=rosterHit?.athleteId ?: "",
                        coreStarter=false,
                        injuryStatus=inj.status,
                        injuryDetail=inj.detail
                    )
                }
            }

            val normalized=markCoreStarters(merged)
            val offenseCore=normalized.count{it.coreStarter && it.unit=="OFFENSE"}
            val defenseCore=normalized.count{it.coreStarter && it.unit=="DEFENSE"}
            val rawRankOne=depthPlayers.count{it.rankOne}

            val depthLoaded=
                depthPlayers.isNotEmpty() &&
                offenseCore>=10 &&
                defenseCore>=10

            val dataReady=depthLoaded && injuriesValid
            val offense=if(dataReady)normalized.filter{it.unit=="OFFENSE"} else emptyList()
            val defense=if(dataReady)normalized.filter{it.unit=="DEFENSE"} else emptyList()
            val offPenalty=offense.sumOf{impactPoints(it)}
            val defLeak=defense.sumOf{impactPoints(it)}
            val starters=if(depthLoaded)normalized.filter{it.coreStarter} else emptyList()

            TeamRosterIntelligence(
                team=team,
                players=normalized.sortedWith(
                    compareBy<RosterPlayerState>{
                        when(it.unit){"OFFENSE"->0;"DEFENSE"->1;else->2}
                    }.thenByDescending{it.coreStarter}
                        .thenBy{it.position}
                        .thenBy{it.depthRank}
                        .thenBy{it.name}
                ),
                offenseAvailability=if(dataReady)
                    (100.0-offPenalty*8.0).coerceIn(45.0,100.0)
                else 0.0,
                defenseAvailability=if(dataReady)
                    (100.0-defLeak*8.0).coerceIn(45.0,100.0)
                else 0.0,
                offensePenalty=offPenalty,
                defenseLeak=defLeak,
                startersTotal=starters.size,
                startersAvailable=if(injuriesValid)
                    starters.count{severity(it.injuryStatus)<.75}
                else 0,
                offenseCoreStarters=offenseCore,
                defenseCoreStarters=defenseCore,
                rawRankOneCount=rawRankOne,
                outCount=if(injuriesValid)
                    normalized.count{severity(it.injuryStatus)>=.75}
                else 0,
                questionableCount=if(injuriesValid)
                    normalized.count{
                        it.injuryStatus.contains("QUESTION",true) ||
                        it.injuryStatus.contains("DOUBT",true)
                    }
                else 0,
                depthLoaded=depthLoaded,
                injuriesLoaded=injuriesValid,
                injuryEndpointOk=injuryResult.isSuccess,
                injurySchemaOk=injuryParsed.schemaRecognized,
                injuryRawCount=injuryParsed.rawCount,
                injuryParsedCount=injuryParsed.parsedCount,
                injuryState=injuryState,
                rosterLoaded=rosterResult.isSuccess && rosterPlayers.isNotEmpty(),
                depthSource=when{
                    siteDepthPlayers.isNotEmpty()->"ESPN SITE"
                    coreDepthPlayers.isNotEmpty()->"ESPN CORE RESOLVED"
                    rosterPlayers.isNotEmpty()->"ROSTER FALLBACK"
                    else->"SIN DEPTH"
                }
            )

        }

    private fun markCoreStarters(players:List<RosterPlayerState>):List<RosterPlayerState>{
        val rankOne=players.filter{
            it.depthRank==1 && (it.unit=="OFFENSE" || it.unit=="DEFENSE")
        }
        val selected=linkedSetOf<String>()

        fun choose(unit:String,limit:Int,predicate:(String)->Boolean){
            rankOne.asSequence()
                .filter{it.unit==unit}
                .filter{predicate(it.position.uppercase())}
                .filter{starterKey(it) !in selected}
                .take(limit)
                .forEach{selected+=starterKey(it)}
        }

        choose("OFFENSE",1){it in setOf("QB","QUARTERBACK")}
        choose("OFFENSE",1){it in setOf("RB","HB","FB","RUNNING BACK","FULLBACK")}
        choose("OFFENSE",3){it in setOf("WR","LWR","RWR","SLOT","WIDE RECEIVER")}
        choose("OFFENSE",1){it in setOf("TE","TIGHT END")}
        choose("OFFENSE",5){
            it in setOf(
                "LT","RT","OT","T","LG","RG","G","C","OL",
                "LEFT TACKLE","RIGHT TACKLE","LEFT GUARD","RIGHT GUARD","CENTER"
            )
        }
        fillUnit(rankOne,selected,"OFFENSE",11)

        choose("DEFENSE",4){
            it in setOf(
                "DE","LDE","RDE","EDGE","DT","NT","DL",
                "LEFT DEFENSIVE END","RIGHT DEFENSIVE END","DEFENSIVE TACKLE"
            )
        }
        choose("DEFENSE",3){
            it in setOf("LB","ILB","MLB","OLB","LOLB","ROLB","LINEBACKER")
        }
        choose("DEFENSE",2){
            it in setOf("CB","LCB","RCB","NB","DB","CORNERBACK")
        }
        choose("DEFENSE",2){
            it in setOf("S","FS","SS","FREE SAFETY","STRONG SAFETY")
        }
        fillUnit(rankOne,selected,"DEFENSE",11)

        return players.map{p->p.copy(coreStarter=starterKey(p) in selected)}
    }

    private fun fillUnit(
        rankOne:List<RosterPlayerState>,
        selected:MutableSet<String>,
        unit:String,
        target:Int
    ){
        var current=rankOne.count{it.unit==unit && starterKey(it) in selected}
        rankOne.asSequence()
            .filter{it.unit==unit}
            .filter{starterKey(it) !in selected}
            .forEach{p->
                if(current<target){
                    selected+=starterKey(p)
                    current++
                }
            }
    }

    private fun starterKey(p:RosterPlayerState):String =
        if(p.athleteId.isNotBlank())"ID:${p.athleteId}"
        else "NM:${normalizeName(p.name)}:${p.position.uppercase()}"

    private fun parseSiteDepthChart(root:JSONObject):List<RosterPlayerState> =
        parseEmbeddedDepthGroups(root.optJSONArray("depthCharts"))

    private fun parseEmbeddedDepthGroups(groups:JSONArray?):List<RosterPlayerState>{
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
                val pos=positionAbbrev(posObj,key)
                val unit=unitForPosition(pos,groupName)
                val athletes=posObj.optJSONArray("athletes") ?: continue

                for(j in 0 until athletes.length()){
                    val slot=athletes.optJSONObject(j) ?: continue
                    val athlete=slot.optJSONObject("athlete") ?: continue
                    val name=athlete.optString(
                        "displayName",
                        athlete.optString("fullName","")
                    ).trim()

                    if(name.isBlank())continue

                    out+=RosterPlayerState(
                        name=name,
                        position=pos,
                        unit=unit,
                        depthRank=max(1,slot.optInt("rank",j+1)),
                        athleteId=athlete.optString("id","")
                    )
                }
            }
        }

        return out.distinctBy{"${it.unit}|${it.position}|${it.name}"}
    }

    private suspend fun parseCoreDepthChart(
        root:JSONObject,
        rosterById:Map<String,RosterPlayerState>
    ):List<RosterPlayerState> = coroutineScope {
        val groups=root.optJSONArray("items") ?: return@coroutineScope emptyList()

        data class Slot(
            val position:String,
            val unit:String,
            val rank:Int,
            val athleteId:String,
            val embeddedName:String,
            val ref:String
        )

        val slots=mutableListOf<Slot>()

        for(i in 0 until groups.length()){
            val group=groups.optJSONObject(i) ?: continue
            val groupName=group.optString("name","")
            val positions=group.optJSONObject("positions") ?: continue
            val keys=positions.keys()

            while(keys.hasNext()){
                val key=keys.next()
                val posObj=positions.optJSONObject(key) ?: continue
                val pos=positionAbbrev(posObj,key)
                val unit=unitForPosition(pos,groupName)
                val athletes=posObj.optJSONArray("athletes") ?: continue

                for(j in 0 until athletes.length()){
                    val slot=athletes.optJSONObject(j) ?: continue
                    val athlete=slot.optJSONObject("athlete") ?: continue
                    val ref=athlete.optString("\$ref","")
                    val id=athlete.optString("id","").ifBlank{
                        extractAthleteId(ref)
                    }
                    val embeddedName=athlete.optString(
                        "displayName",
                        athlete.optString("fullName","")
                    ).trim()

                    slots+=Slot(
                        position=pos,
                        unit=unit,
                        rank=max(1,slot.optInt("rank",j+1)),
                        athleteId=id,
                        embeddedName=embeddedName,
                        ref=ref
                    )
                }
            }
        }

        val unresolved=slots
            .filter{
                it.embeddedName.isBlank() &&
                rosterById[it.athleteId]?.name.isNullOrBlank() &&
                it.ref.isNotBlank()
            }
            .map{it.ref}
            .distinct()

        // Resolve only refs we could not map through the team roster.
        // Parallel + cache prevents dozens of sequential network round trips.
        val resolvedByRef=unresolved.map{ref->
            async(Dispatchers.IO){
                ref to resolveAthleteRef(ref)
            }
        }.awaitAll().toMap()

        slots.mapNotNull{slot->
            val rosterHit=rosterById[slot.athleteId]
            val resolved=resolvedByRef[slot.ref]

            val name=slot.embeddedName
                .ifBlank{rosterHit?.name.orEmpty()}
                .ifBlank{resolved?.name.orEmpty()}
                .trim()

            if(name.isBlank())return@mapNotNull null

            val resolvedPos=slot.position
                .ifBlank{rosterHit?.position.orEmpty()}
                .ifBlank{resolved?.position.orEmpty()}
                .ifBlank{"—"}

            RosterPlayerState(
                name=name,
                position=resolvedPos,
                unit=unitForPosition(resolvedPos,slot.unit),
                depthRank=slot.rank,
                athleteId=slot.athleteId.ifBlank{resolved?.id.orEmpty()}
            )
        }.distinctBy{"${it.unit}|${it.position}|${it.name}"}
    }

    private fun positionAbbrev(posObj:JSONObject,key:String):String{
        val p=posObj.optJSONObject("position")
        return p?.optString("abbreviation","")
            ?.ifBlank{p.optString("name","")}
            ?.ifBlank{key.uppercase()}
            ?: key.uppercase()
    }

    private fun extractAthleteId(ref:String):String{
        if(ref.isBlank())return ""
        val clean=ref.substringBefore('?').trimEnd('/')
        return clean.substringAfterLast('/').takeIf{
            it.isNotBlank() && it.all{ch->ch.isDigit()}
        } ?: ""
    }

    private fun resolveAthleteRef(rawRef:String):AthleteIdentity?{
        if(rawRef.isBlank())return null

        val safeRef=when{
            rawRef.startsWith("http://")->"https://${rawRef.removePrefix("http://")}"
            else->rawRef
        }

        val id=extractAthleteId(safeRef)
        if(id.isNotBlank()){
            synchronized(athleteCache){
                athleteCache[id]?.let{return it}
            }
        }

        val obj=runCatching{
            JSONObject(readAll(safeRef))
        }.getOrNull() ?: return null

        val resolvedId=obj.optString("id",id)
        val name=obj.optString(
            "displayName",
            obj.optString("fullName","")
        ).trim()
        if(name.isBlank())return null

        val position=obj.optJSONObject("position")
            ?.optString("abbreviation","")
            .orEmpty()

        val result=AthleteIdentity(
            id=resolvedId,
            name=name,
            position=position
        )

        if(resolvedId.isNotBlank()){
            synchronized(athleteCache){
                athleteCache[resolvedId]=result
            }
        }

        return result
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
                val name=athlete.optString(
                    "displayName",
                    athlete.optString("fullName","")
                ).trim()

                if(name.isBlank())continue

                val pos=athlete.optJSONObject("position")
                    ?.optString("abbreviation","")
                    ?.ifBlank{groupPosition}
                    ?: groupPosition

                val id=athlete.optString("id","")

                if(id.isNotBlank()){
                    synchronized(athleteCache){
                        athleteCache[id]=AthleteIdentity(
                            id=id,
                            name=name,
                            position=pos
                        )
                    }
                }

                out+=RosterPlayerState(
                    name=name,
                    position=pos.ifBlank{"—"},
                    unit=unitForPosition(pos,groupPosition),
                    depthRank=99,
                    athleteId=id
                )
            }
        }

        return out.distinctBy{
            if(it.athleteId.isNotBlank())it.athleteId
            else "${it.position}|${it.name}"
        }
    }

    private suspend fun parseInjuries(
        root:JSONObject,
        rosterById:Map<String,RosterPlayerState>
    ):InjuryParseResult = coroutineScope {
        data class RawInjury(
            val athleteId:String,
            val embeddedName:String,
            val ref:String,
            val status:String,
            val detail:String
        )

        val rootArray=root.optJSONArray("injuries") ?: root.optJSONArray("items")
        if(rootArray==null){
            return@coroutineScope InjuryParseResult(emptyMap(),0,0,false)
        }

        val raw=mutableListOf<RawInjury>()

        fun consume(arr:JSONArray){
            for(i in 0 until arr.length()){
                val item=arr.optJSONObject(i) ?: continue
                val nested=item.optJSONArray("injuries") ?: item.optJSONArray("items")
                if(nested!=null){
                    consume(nested)
                    continue
                }

                val athlete=item.optJSONObject("athlete") ?: item.optJSONObject("player")
                val ref=athlete?.optString("\$ref","").orEmpty()
                val id=athlete?.optString("id","").orEmpty().ifBlank{
                    extractAthleteId(ref)
                }
                val embeddedName=athlete?.let{
                    it.optString("displayName",it.optString("fullName",""))
                }?.trim().orEmpty()

                val status=when(val value=item.opt("status")){
                    is String -> value
                    is JSONObject -> value.optString("description",value.optString("name",""))
                    else -> ""
                }.ifBlank{item.optString("statusDescription","")}

                val detail=item.optJSONObject("type")
                    ?.optString("description","")
                    ?.ifBlank{item.optString("details","")}
                    ?: item.optString("details","")

                raw+=RawInjury(id,embeddedName,ref,status,detail)
            }
        }

        consume(rootArray)

        val unresolved=raw
            .filter{
                it.embeddedName.isBlank() &&
                rosterById[it.athleteId]?.name.isNullOrBlank() &&
                it.ref.isNotBlank()
            }
            .map{it.ref}
            .distinct()

        val resolvedByRef=unresolved.map{ref->
            async(Dispatchers.IO){ref to resolveAthleteRef(ref)}
        }.awaitAll().toMap()

        var parsedCount=0
        val out=linkedMapOf<String,InjuryInfo>()

        raw.forEach{x->
            val rosterHit=rosterById[x.athleteId]
            val resolved=resolvedByRef[x.ref]
            val name=x.embeddedName
                .ifBlank{rosterHit?.name.orEmpty()}
                .ifBlank{resolved?.name.orEmpty()}
                .trim()

            if(name.isNotBlank()){
                parsedCount++
                out[normalizeName(name)]=InjuryInfo(name,x.status,x.detail)
            }
        }

        InjuryParseResult(
            items=out,
            rawCount=raw.size,
            parsedCount=parsedCount,
            schemaRecognized=true
        )
    }

    private fun unitForPosition(position:String,groupName:String):String{
        val p=position.uppercase()
        val g=groupName.lowercase()

        val offense=setOf(
            "QB","RB","HB","FB","WR","LWR","RWR","SLOT","TE",
            "LT","RT","OT","T","LG","RG","G","C","OL",
            "LEFT TACKLE","RIGHT TACKLE","LEFT GUARD","RIGHT GUARD",
            "CENTER","QUARTERBACK","RUNNING BACK","WIDE RECEIVER","TIGHT END"
        )
        val defense=setOf(
            "DE","EDGE","DT","NT","DL","OLB","ILB","MLB","LB",
            "CB","LCB","RCB","NB","S","FS","SS","DB",
            "LEFT DEFENSIVE END","RIGHT DEFENSIVE END","DEFENSIVE TACKLE",
            "CORNERBACK","FREE SAFETY","STRONG SAFETY","LINEBACKER"
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
            p.coreStarter->1.0
            p.depthRank==1->.25
            p.depthRank==2->.30
            p.depthRank==3->.15
            else->.05
        }

        return sev*depth*positionWeight(
            p.position,
            p.unit
        )
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
                p=="QB" || p=="QUARTERBACK"->4.0
                p in setOf(
                    "LT","RT","OT","T","LG","RG","G","C","OL",
                    "LEFT TACKLE","RIGHT TACKLE","LEFT GUARD","RIGHT GUARD","CENTER"
                )->1.35
                p in setOf(
                    "WR","LWR","RWR","SLOT","WIDE RECEIVER"
                )->1.05
                p=="TE" || p=="TIGHT END"->.75
                p in setOf(
                    "RB","HB","FB","RUNNING BACK"
                )->.65
                else->.35
            }
        }else if(unit=="DEFENSE"){
            when{
                p in setOf(
                    "CB","LCB","RCB","NB","CORNERBACK"
                )->1.10
                p in setOf(
                    "DE","EDGE","OLB","LEFT DEFENSIVE END","RIGHT DEFENSIVE END"
                )->1.00
                p in setOf(
                    "S","FS","SS","FREE SAFETY","STRONG SAFETY"
                )->.85
                p in setOf(
                    "LB","ILB","MLB","LINEBACKER"
                )->.80
                p in setOf(
                    "DT","NT","DL","DEFENSIVE TACKLE"
                )->.70
                else->.40
            }
        }else .20
    }

    private fun teamReliability(t:TeamRosterIntelligence):Double{
        if(!t.depthLoaded || !t.injuriesLoaded)return 0.0
        val starterCoverage=(t.startersTotal.toDouble()/22.0).coerceIn(0.0,1.0)
        val injuryCoverage=if(t.injuryRawCount==0)1.0
            else (t.injuryParsedCount.toDouble()/t.injuryRawCount.toDouble()).coerceIn(0.0,1.0)
        return (.90*starterCoverage*injuryCoverage).coerceIn(0.0,.90)
    }

    private fun emptyTeam(team:String)=TeamRosterIntelligence(
        team=team,
        players=emptyList(),
        offenseAvailability=0.0,
        defenseAvailability=0.0,
        offensePenalty=0.0,
        defenseLeak=0.0,
        startersTotal=0,
        startersAvailable=0,
        offenseCoreStarters=0,
        defenseCoreStarters=0,
        rawRankOneCount=0,
        outCount=0,
        questionableCount=0,
        depthLoaded=false,
        injuriesLoaded=false,
        injuryEndpointOk=false,
        injurySchemaOk=false,
        injuryRawCount=0,
        injuryParsedCount=0,
        injuryState="SIN DATOS",
        rosterLoaded=false,
        depthSource="SIN DATOS"
    )

    private fun normalizeName(raw:String)=raw.lowercase()
        .replace(Regex("[^a-z0-9 ]"),"")
        .replace(Regex("\\s+")," ")
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
            val abbr=normalizeTeam(
                teamObj.optString("abbreviation","")
            )
            val id=teamObj.optString("id","")

            if(abbr.isNotBlank() && id.isNotBlank()){
                teamIdCache[abbr]=id
            }
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
            setRequestProperty(
                "User-Agent",
                "NFL-Totals-Lab-Android/0.9.2.3"
            )
            connect()

            if(responseCode !in 200..299){
                throw IllegalStateException(
                    "HTTP $responseCode en ESPN roster/depth"
                )
            }
        }

        return try{
            c.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use{it.readText()}
        }finally{
            c.disconnect()
        }
    }
}
